package uk.gov.dbt.ndtp.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.http.HttpOp;
import org.apache.jena.riot.WebContent;
import org.apache.jena.riot.web.HttpNames;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.exec.http.UpdateExecHTTP;
import org.apache.jena.system.Txn;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.jena.abac.SysABAC;

class TestCQRS {

    private static final String TOPIC = "knowledge";
    private static final String DATASET = "/ds";
    private static final String ENDPOINT = "update";

    private static final Node S = NodeFactory.createURI("http://example/s");
    private static final Node P = NodeFactory.createURI("http://example/p");
    private static final Node O = NodeFactory.createLiteralString("o");

    private MockProducer<String, byte[]> producer;
    private DatasetGraph dsg;
    private FusekiServer server;
    private String updateURL;

    @BeforeAll
    static void beforeAll() {
        FusekiLogging.setLogging();
    }

    @BeforeEach
    void startServer() {
        producer = new MockProducer<>(true, new StringSerializer(), new ByteArraySerializer());
        dsg = DatasetGraphFactory.createTxnMem();
        server = startServer(CQRS.updateAction(TOPIC, producer));
    }

    @AfterEach
    void stopServer() {
        if ( server != null )
            server.stop();
    }

    private FusekiServer startServer(org.apache.jena.fuseki.servlets.ActionService cqrsUpdate) {
        FusekiServer fusekiServer = FusekiServer.create()
                .port(0)
                .registerOperation(CQRS.Vocab.operationUpdateCQRS, WebContent.contentTypeSPARQLUpdate, cqrsUpdate)
                .add(DATASET, dsg)
                .addEndpoint(DATASET, ENDPOINT, CQRS.Vocab.operationUpdateCQRS)
                .build()
                .start();
        updateURL = fusekiServer.datasetURL(DATASET) + "/" + ENDPOINT;
        return fusekiServer;
    }

    private void update(String updateString) {
        UpdateExecHTTP.service(updateURL).update(updateString).execute();
    }

    private void update(String updateString, String securityLabel) {
        UpdateExecHTTP.service(updateURL).update(updateString)
                .httpHeader(SysABAC.H_SECURITY_LABEL, securityLabel)
                .execute();
    }

    private static String body(ProducerRecord<String, byte[]> rec) {
        return new String(rec.value(), StandardCharsets.UTF_8);
    }

    private static String header(ProducerRecord<String, byte[]> rec, String name) {
        Header h = rec.headers().lastHeader(name);
        return (h == null) ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private long localSize() {
        return Txn.calculateRead(dsg, () -> dsg.stream().count());
    }

    @Test
    void insert_isSentToKafkaAsPatch_andNotAppliedLocally() {
        update("INSERT DATA { <http://example/s> <http://example/p> 'o' }");

        List<ProducerRecord<String, byte[]>> sent = producer.history();
        assertEquals(1, sent.size());
        ProducerRecord<String, byte[]> rec = sent.get(0);
        assertEquals(TOPIC, rec.topic());
        String patch = body(rec);
        assertTrue(patch.contains("TX"), "Patch should start a transaction: " + patch);
        assertTrue(patch.contains("A <http://example/s> <http://example/p> \"o\""), "Patch should add the triple: " + patch);
        assertTrue(patch.contains("TC"), "Patch should commit the transaction: " + patch);
        assertEquals(0, localSize(), "CQRS must not apply the update to the local dataset");
    }

    @Test
    void delete_isSentToKafkaAsPatch_andNotAppliedLocally() {
        Txn.executeWrite(dsg, () -> dsg.getDefaultGraph().add(S, P, O));

        update("DELETE DATA { <http://example/s> <http://example/p> 'o' }");

        assertEquals(1, producer.history().size());
        String patch = body(producer.history().get(0));
        assertTrue(patch.contains("D <http://example/s> <http://example/p> \"o\""), "Patch should delete the triple: " + patch);
        assertEquals(1, localSize(), "CQRS must not apply the delete to the local dataset");
    }

    @Test
    void patch_hasContentTypeHeader() {
        update("INSERT DATA { <http://example/s> <http://example/p> 'o' }");

        ProducerRecord<String, byte[]> rec = producer.history().get(0);
        assertEquals(WebContent.contentTypePatch, header(rec, HttpNames.hContentType));
        assertNull(header(rec, SysABAC.H_SECURITY_LABEL), "No Security-Label header was sent");
    }

    @Test
    void securityLabelHeader_isForwardedToKafka() {
        update("INSERT DATA { <http://example/s> <http://example/p> 'o' }", "clearance=secret");

        ProducerRecord<String, byte[]> rec = producer.history().get(0);
        assertEquals("clearance=secret", header(rec, SysABAC.H_SECURITY_LABEL));
        assertEquals(WebContent.contentTypePatch, header(rec, HttpNames.hContentType));
    }

    @Test
    void eachUpdateRequest_isOneKafkaMessage() {
        update("INSERT DATA { <http://example/s> <http://example/p> 'o1' }");
        update("INSERT DATA { <http://example/s> <http://example/p> 'o2' }");

        assertEquals(2, producer.history().size());
        assertTrue(body(producer.history().get(0)).contains("\"o1\""));
        assertTrue(body(producer.history().get(1)).contains("\"o2\""));
    }

    @Test
    void parseError_isBadRequest_andNothingIsSent() {
        // Sent as a raw body: the update client would reject the syntax before sending it.
        HttpException ex = assertThrows(HttpException.class, () ->
                HttpOp.httpPost(updateURL, WebContent.contentTypeSPARQLUpdate, "INSERT DATA { junk"));
        assertEquals(400, ex.getStatusCode());
        assertTrue(producer.history().isEmpty());
        assertEquals(0, localSize());
    }

    @Test
    void usingGraphParameter_isBadRequest_andNothingIsSent() {
        String url = updateURL + "?" + HttpNames.paramUsingGraphURI + "=http://example/g";
        HttpException ex = assertThrows(HttpException.class, () ->
                HttpOp.httpPost(url, WebContent.contentTypeSPARQLUpdate,
                                "INSERT { <http://example/s> <http://example/p> 'o' } WHERE {}"));
        assertEquals(400, ex.getStatusCode());
        assertTrue(producer.history().isEmpty());
    }

    @Test
    void kafkaSendFailure_isServerError_andNotAppliedLocally() {
        stopServer();
        producer = new MockProducer<>(false, new StringSerializer(), new ByteArraySerializer());
        producer.sendException = new KafkaException("Broker unavailable");
        server = startServer(CQRS.updateAction(TOPIC, producer));

        HttpException ex = assertThrows(HttpException.class,
                () -> update("INSERT DATA { <http://example/s> <http://example/p> 'o' }"));
        assertEquals(500, ex.getStatusCode());
        assertEquals(0, localSize());
    }

    @Test
    void noProducer_logsPatch_andSucceeds() {
        stopServer();
        server = startServer(CQRS.updateAction(TOPIC, (Properties) null));

        update("INSERT DATA { <http://example/s> <http://example/p> 'o' }");
        assertEquals(0, localSize(), "Without Kafka the update is still not applied locally");
    }
}
