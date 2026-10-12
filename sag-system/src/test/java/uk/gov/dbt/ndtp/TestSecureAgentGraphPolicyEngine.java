package uk.gov.dbt.ndtp;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.apache.jena.sparql.exec.RowSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.dbt.ndtp.core.SecureAgentGraph;
import uk.gov.dbt.ndtp.jena.abac.lib.ABACRequest;
import uk.gov.dbt.ndtp.jena.abac.lib.OpaDatasetFilterProvider;
import uk.gov.dbt.ndtp.jena.abac.opa.DecisionServiceProvider;
import uk.gov.dbt.ndtp.jena.abac.opa.OpaDecisionServiceProvider;
import uk.gov.dbt.ndtp.jena.abac.opa.resilience.CachingDecisionServiceProvider;
import uk.gov.dbt.ndtp.jena.abac.opa.resilience.CircuitBreaker;
import uk.gov.dbt.ndtp.jena.abac.opa.resilience.CircuitBreakingDecisionServiceProvider;
import uk.gov.dbt.ndtp.jena.abac.opa.transport.HttpOpaTransport;

public class TestSecureAgentGraphPolicyEngine {

    private static final String DIR = TestSecureAgentGraphIntegration.DIR;
    private static final String QUERY = "SELECT * { ?s ?p ?o }";
    private static final String POLICY_PATH = "sag/test";

    private static final String USER_SECRET = "user1";                   // clearance=secret
    private static final String USER_TOP_SECRET = "someone@host.email";  // clearance=top-secret
    private static final String USER_NO_ATTRIBUTES = "public";           // registered, no attributes
    private static final String USER_UNKNOWN = "u3";                     // not in the attribute store

    private static final Set<String> DATASET_LABELS =
            Set.of("clearance=ordinary", "clearance=secret", "clearance=top-secret");

    private static FusekiServer server;
    private static String datasetURL;
    private static HttpServer opa;
    private static URI opaURI;

    private record OpaReply(int status, String body) {}
    private static volatile Function<JsonObject, OpaReply> policy;
    private static volatile JsonObject lastOpaInput;
    private static final AtomicInteger opaCalls = new AtomicInteger();

    @BeforeAll
    static void beforeAll() throws Exception {
        assumeFalse("true".equalsIgnoreCase(System.getenv("POLICY_ENGINE_ENABLED")),
                    "A real policy engine is configured in the environment");
        FusekiLogging.markInitialized(false);
        FusekiLogging.setLogging();
        LibTestsSAG.setupAuthentication();
        LibTestsSAG.disableInitialCompaction();

        opa = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        opa.createContext("/v1/data/" + POLICY_PATH, TestSecureAgentGraphPolicyEngine::handleOpa);
        opa.start();
        opaURI = URI.create("http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + opa.getAddress().getPort());

        server = SecureAgentGraph.construct("--port=0", "--conf", DIR + "/config-policy-engine.ttl").start();
        datasetURL = "http://localhost:" + server.getHttpPort() + "/ds";
        LibTestsSAG.uploadFile(datasetURL + "/upload", DIR + "/data-hierarchies.trig");
    }

    @AfterAll
    static void afterAll() throws Exception {
        ABACRequest.reset();
        if ( server != null )
            server.stop();
        if ( opa != null )
            opa.stop(0);
        LibTestsSAG.teardownAuthentication();
    }

    @BeforeEach
    void beforeEach() {
        policy = TestSecureAgentGraphPolicyEngine::labelsFromAttributes;
        lastOpaInput = null;
        opaCalls.set(0);
        // Fresh provider for each test so circuit breaker state does not carry over.
        ABACRequest.setFilterProvider(new OpaDatasetFilterProvider(decisionService(opaURI)));
    }

    @Test
    void noToken_isUnauthorised_andOpaIsNotAsked() {
        assertEquals(401, statusOf(null));
        assertEquals(0, opaCalls.get());
    }

    @Test
    void user_seesOnlyTheLabelsOpaPermits() {
        assertEquals(Set.of("Secret"), objects(USER_SECRET));
    }

    @Test
    void differentUsers_sameQuery_differentResults() {
        assertEquals(Set.of("Secret"), objects(USER_SECRET));
        assertEquals(Set.of("Top Secret"), objects(USER_TOP_SECRET));
    }

    @Test
    void permittedLabels_areExactMatches_notHierarchyExpanded() {
        assertFalse(objects(USER_SECRET).contains("Ordinary"));
    }

    @Test
    void unlabelledTriple_isNotReturned() {
        policy = input -> allow(DATASET_LABELS);
        assertEquals(Set.of("Ordinary", "Secret", "Top Secret"), objects(USER_SECRET));
    }

    @Test
    void labelNotUsedInDataset_isIgnored() {
        policy = input -> allow(Set.of("clearance=secret", "made-up-label"));
        assertEquals(Set.of("Secret"), objects(USER_SECRET));
    }

    @Test
    void policyChange_appliesToTheNextRequest() {
        assertEquals(Set.of("Secret"), objects(USER_SECRET));
        policy = input -> allow(Set.of("clearance=ordinary"));
        assertEquals(Set.of("Ordinary"), objects(USER_SECRET));
    }

    @Test
    void opaRequest_carriesIdentityFromTheJwt_andTheDatasetVocabulary() {
        objects(USER_SECRET);

        JsonObject input = lastOpaInput;
        assertNotNull(input, "OPA was not called");
        assertEquals(USER_SECRET, input.getString("subject_id"));
        assertEquals("/ds", input.getString("dataset_name"));
        assertEquals("query", input.getString("action"));
        assertEquals(DATASET_LABELS, strings(input.get("vocabulary").getAsArray()));
        JsonObject attributes = input.get("subject_attributes").getAsObject();
        assertEquals("secret", attributes.getString("clearance"));
        assertEquals(1, attributes.keys().size());
    }

    @Test
    void oneQuery_isOneOpaCall() {
        objects(USER_SECRET);
        assertEquals(1, opaCalls.get());
    }

    @Test
    void userUnknownToAttributeStore_isForbidden_beforeOpaIsAsked() {
        assertEquals(403, statusOf(USER_UNKNOWN));
        assertEquals(0, opaCalls.get());
    }

    @Test
    void userWithNoAttributes_isForbiddenByPolicy() {
        assertEquals(403, statusOf(USER_NO_ATTRIBUTES));
        assertEquals(1, opaCalls.get());
    }

    @Test
    void opaDenies_isForbidden() {
        policy = input -> reply(200, "{\"result\":{\"allow\":false}}");
        assertEquals(403, statusOf(USER_SECRET));
    }

    @Test
    void opaAllowsWithNoLabels_isForbidden() {
        policy = input -> allow(Set.of());
        assertEquals(403, statusOf(USER_SECRET));
    }

    @Test
    void opaServerError_isServiceUnavailable() {
        policy = input -> reply(500, "{\"code\":\"internal_error\"}");
        assertEquals(503, statusOf(USER_SECRET));
    }

    @Test
    void opaUnreachable_isServiceUnavailable() throws IOException {
        ABACRequest.setFilterProvider(new OpaDatasetFilterProvider(decisionService(unusedLocalURI())));
        assertEquals(503, statusOf(USER_SECRET));
    }

    @Test
    void opaResultMissingAllow_isServiceUnavailable() {
        policy = input -> reply(200, "{\"result\":{}}");
        assertEquals(503, statusOf(USER_SECRET));
    }

    @Test
    void permittedLabelsAsObject_isServiceUnavailable() {
        // A policy that echoes input.subject_attributes returns an object, not a list of labels.
        policy = input -> reply(200, "{\"result\":{\"allow\":true,\"permitted_labels\":"
                                     + JSON.toStringFlat(input.get("subject_attributes")) + "}}");
        assertEquals(503, statusOf(USER_SECRET));
    }

    @Test
    void repeatedOpaFailures_openTheCircuit_andOpaIsNoLongerCalled() {
        policy = input -> reply(500, "{}");
        for ( int i = 0 ; i < 5 ; i++ )
            assertEquals(503, statusOf(USER_SECRET));
        assertEquals(5, opaCalls.get());

        assertEquals(503, statusOf(USER_SECRET));
        assertEquals(5, opaCalls.get(), "Circuit is open: OPA should not be called");
    }

    @Test
    void withoutPolicyEngine_labelsAreEvaluatedLocally() {
        ABACRequest.reset();
        // Local evaluation applies the clearance hierarchy: secret also permits ordinary.
        assertEquals(Set.of("Ordinary", "Secret"), objects(USER_SECRET));
        assertEquals(0, opaCalls.get());
    }

    private static DecisionServiceProvider decisionService(URI opaBase) {
        HttpOpaTransport transport = new HttpOpaTransport(opaBase, POLICY_PATH, Duration.ofSeconds(2));
        DecisionServiceProvider opaProvider = new OpaDecisionServiceProvider(transport, Duration.ofSeconds(2), Duration.ofSeconds(2));
        DecisionServiceProvider circuitBreaking =
                new CircuitBreakingDecisionServiceProvider(opaProvider, new CircuitBreaker(5, Duration.ofSeconds(30)));
        return new CachingDecisionServiceProvider(circuitBreaking);
    }

    private static OpaReply labelsFromAttributes(JsonObject input) {
        JsonObject attributes = input.get("subject_attributes").getAsObject();
        if ( attributes.isEmpty() )
            return reply(200, "{\"result\":{\"allow\":false}}");
        Set<String> labels = new HashSet<>();
        for ( String key : attributes.keys() )
            labels.add(key + "=" + attributes.getString(key));
        return allow(labels);
    }

    private static OpaReply allow(Set<String> labels) {
        JsonArray array = new JsonArray();
        labels.forEach(array::add);
        return reply(200, "{\"result\":{\"allow\":true,\"permitted_labels\":" + JSON.toStringFlat(array) + "}}");
    }

    private static OpaReply reply(int status, String body) {
        return new OpaReply(status, body);
    }

    private static void handleOpa(HttpExchange exchange) throws IOException {
        opaCalls.incrementAndGet();
        try ( InputStream in = exchange.getRequestBody() ) {
            JsonObject request = JSON.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            lastOpaInput = request.get("input").getAsObject();
            OpaReply opaReply = policy.apply(lastOpaInput);
            byte[] bytes = opaReply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(opaReply.status(), bytes.length);
            try ( OutputStream out = exchange.getResponseBody() ) {
                out.write(bytes);
            }
        }
    }

    private static URI unusedLocalURI() throws IOException {
        HttpServer probe = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        int port = probe.getAddress().getPort();
        probe.stop(0);
        return URI.create("http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":" + port);
    }

    private static Set<String> objects(String user) {
        RowSet rowSet = LibTestsSAG.queryWithToken(datasetURL, QUERY, user);
        Set<String> values = new HashSet<>();
        rowSet.forEachRemaining(binding -> values.add(binding.get("o").getLiteralLexicalForm()));
        return values;
    }

    private static int statusOf(String user) {
        try {
            LibTestsSAG.withLevel(org.apache.jena.fuseki.Fuseki.actionLog, "ERROR",
                                  () -> LibTestsSAG.queryWithToken(datasetURL, QUERY, user).materialize());
            return 200;
        } catch (QueryExceptionHTTP ex) {
            return ex.getStatusCode();
        }
    }

    private static Set<String> strings(JsonArray array) {
        Set<String> values = new HashSet<>();
        for ( JsonValue v : array )
            values.add(v.getAsString().value());
        return values;
    }
}
