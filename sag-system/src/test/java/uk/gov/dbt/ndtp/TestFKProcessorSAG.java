// SPDX-License-Identifier: Apache-2.0
// Originally developed by Telicent Ltd.; subsequently adapted, enhanced, and maintained by the National Digital Twin Programme.
/*
 *  Copyright (c) Telicent Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
/*
 *  Modifications made by the National Digital Twin Programme (NDTP)
 *  © Crown Copyright 2026. This work has been developed by the National Digital Twin Programme
 *  and is legally attributed to the UK's Department for Business, Innovation, Science and Trade (BIST) as the governing entity.
 */

package uk.gov.dbt.ndtp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.dbt.ndtp.LibTestsSAG.queryNoToken;
import static uk.gov.dbt.ndtp.LibTestsSAG.queryWithToken;

import java.util.Map;

import uk.gov.dbt.ndtp.core.FKProcessorSAG;
import uk.gov.dbt.ndtp.core.SecureAgentGraph;
import uk.gov.dbt.ndtp.jena.abac.ABAC;
import uk.gov.dbt.ndtp.jena.abac.AttributeValueSet;
import uk.gov.dbt.ndtp.jena.abac.SysABAC;
import uk.gov.dbt.ndtp.jena.abac.attributes.Attribute;
import uk.gov.dbt.ndtp.jena.abac.attributes.AttributeValue;
import uk.gov.dbt.ndtp.jena.abac.lib.AttributesStore;
import uk.gov.dbt.ndtp.jena.abac.lib.AttributesStoreLocal;
import uk.gov.dbt.ndtp.jena.abac.lib.AttributesStoreModifiable;
import uk.gov.dbt.ndtp.jena.abac.lib.DatasetGraphABAC;
import uk.gov.dbt.ndtp.jena.abac.labels.Labels;
import uk.gov.dbt.ndtp.jena.abac.labels.LabelsStore;
import uk.gov.dbt.ndtp.secure.agent.configuration.Configurator;
import org.apache.jena.atlas.lib.Bytes;
import org.apache.jena.fuseki.kafka.FKProcessor;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.server.Operation;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.kafka.FusekiKafka;
import org.apache.jena.kafka.RequestFK;
import org.apache.jena.kafka.ResponseFK;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFWriter;
import org.apache.jena.riot.WebContent;
import org.apache.jena.riot.web.HttpNames;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.exec.QueryExec;
import org.apache.jena.sparql.exec.QueryExecDataset;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetOps;
import org.junit.jupiter.api.*;

/** Test the FusekiKafka processor inside SAG. */
@TestMethodOrder(MethodOrderer.MethodName.class)
class TestFKProcessorSAG {

    static { FusekiLogging.setLogging(); }

    private static final AttributeValue attrPermit = AttributeValue.of(Attribute.create("PERMIT"), AttributeValue.dftTrue);
    private static final AttributeValue attrOther  = AttributeValue.of(Attribute.create("OTHER"),  AttributeValue.dftTrue);

    private static final String USER_PUBLIC = "public";      // Registered user, no attributes
    private static final String USER_PERMIT = "userPermit";  // Registered user, attribute PERMIT=true
    private static final String USER_OTHER = "userOther";    // Registered, attribute OTHER=true

    // SPARQL query to get every thing, anywhere in the dataset (subject to ABAC)
    private static final String QUERY_ALL = "SELECT * { { ?s ?p ?o } UNION { GRAPH ?g { ?s ?p ?o } } }";

    // Fuseki service name. ABAC Dataset.
    private static final String DS_NAME = "/ds";
    // Fuseki service name. Access to the non-ABAC storage database for test inspection.
    private static final String DS_BASE = "/base";

    private static DatasetGraphABAC getDatasetABAC(FusekiServer server) {
        return (DatasetGraphABAC)server.getDataAccessPointRegistry().get(DS_NAME).getDataService().getDataset();
    }

    // The test - directly send requests to the FKProcessor and check by making HTTP requests to the Fuseki server.
    interface TestAction { public void execTest(FKProcessor proc, FusekiServer server, DatasetGraph dsgBase); }

    @BeforeAll
    static void beforeClass() throws Exception {
        LibTestsSAG.setupAuthentication();
        LibTestsSAG.disableInitialCompaction();
    }

    @AfterAll
    static void afterClass() throws Exception {
        LibTestsSAG.teardownAuthentication();
        Configurator.reset();
    }

    @Test
    void processorSAG_load_good_1() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(DS_NAME);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    PREFIX : <http://example/>
                    :s :p "turtle" .
                    """, WebContent.contentTypeTurtle, attrPermit);
            // Check base has changed.
            checkDatasetSize(dsgBase, 1);
            // Check visibility
            long c1 = count(URL, QUERY_ALL, USER_PERMIT);
            assertEquals(1L, c1, "Count (user:permit)");
            long c2 = count(URL, QUERY_ALL, USER_OTHER);
            assertEquals(0L, c2, "Count (user:other)");
        };
        runTestProcessorSAGWithAuth(action);
    }

    @Test
    void processorSAG_load_good_2() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(DS_NAME);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    PREFIX : <http://example/>
                    :s :p "turtle" .
                    """, WebContent.contentTypeTurtle, attrPermit);

            checkDatasetSize(dsgBase, 1);
            long c1 = count(URL, QUERY_ALL, USER_PERMIT);
            assertEquals(1L, c1, "Count (user:permit)");
            long c2 = count(URL, QUERY_ALL, USER_OTHER);
            assertEquals(0L, c2, "Count (user:other)");
        };
        runTestProcessorSAGWithAuth(action);
    }

    @Test
    void processorSAG_load_bad_1() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(DS_NAME);
            LibTestsSAG.withLevel(FusekiKafka.LOG, "FATAL", ()->{
                checkDatasetSize(dsgBase, 0);
                processorRequest(proc, """
                        JUNK
                        """, WebContent.contentTypeTurtle, attrPermit);
            });
            // Should be no data loaded.
            checkDatasetSize(dsgBase, 0);

            long c1 = count(URL, QUERY_ALL, USER_PERMIT);
            assertEquals(0L, c1, "Count (user:permit)");

            // No data, no labels.
            DatasetGraphABAC dsgz = getDatasetABAC(server);
            assertTrue(dsgz.getBase().getDefaultGraph().isEmpty());
            assertTrue(dsgz.labelsStore().isEmpty());
        };
        runTestProcessorSAGWithAuth(action);
    }

    @Test
    void processorSAG_patch_1_add2() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(DS_NAME);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    TX .
                    PA "ex" <http://ex/> .
                    A <http://ex/s1> <http://ex/p> "triple1" .
                    A <http://ex/s2> <http://ex/p> "triple2" .
                    TC .
                    """, WebContent.contentTypePatch, attrPermit);
            checkDatasetSize(dsgBase, 2);
            long c1 = count(URL, QUERY_ALL, USER_PERMIT);
            assertEquals(2L, c1);
            long c2 = count(URL, QUERY_ALL, USER_OTHER);
            assertEquals(0L, c2);
            checkDatasetSize(dsgBase, 2);
        };
        runTestProcessorSAGWithAuth(action);
    }

    @Test
    void processorSAG_patch_2_add1_add1() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(DS_NAME);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    TX .
                    PA "ex" <http://ex/> .
                    A <http://ex/s1> <http://ex/p> "triple1" .
                    TC .
                    """, WebContent.contentTypePatch, attrPermit);
            checkDatasetSize(dsgBase, 1);

            processorRequest(proc, """
                             A <http://ex/s2> <http://ex/p> "triple2" .
                             """, WebContent.contentTypePatch, attrPermit);
            checkDatasetSize(dsgBase, 2);

            long c1 = count(URL, QUERY_ALL, USER_PERMIT);
            assertEquals(2L, c1);
            long c2 = count(URL, QUERY_ALL, USER_OTHER);
            assertEquals(0L, c2);
            checkDatasetSize(dsgBase, 2);
        };
        runTestProcessorSAGWithAuth(action);
    }

    @Test
    void processorSAG_patch_3_add2_delete1() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(DS_NAME);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                             TX .
                             PA "ex" <http://ex/> .
                             A <http://ex/s1> <http://ex/p> "triple1" .
                             A <http://ex/s2> <http://ex/p> "triple2" .
                             TC .
                             """, WebContent.contentTypePatch, attrPermit);
            checkDatasetSize(dsgBase, 2);
            processorRequest(proc, """
                             TX .
                             D <http://ex/s2> <http://ex/p> "triple2" .
                             TX .
                             """, WebContent.contentTypePatch, attrPermit);
            checkDatasetSize(dsgBase, 1);
            long c = count(URL, QUERY_ALL, USER_PERMIT);
            assertEquals(1L, c);
            checkDatasetSize(dsgBase, 1);
        };
        runTestProcessorSAGWithAuth(action);
    }

    /** No label - dataset default applies which in this setup is "deny" */
    @Test
    void processorSAG_patch_4_add_no_label() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URLauthz = server.datasetURL(DS_NAME);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                             A <http://ex/s2> <http://ex/p> "triple2" .
                             """, WebContent.contentTypePatch, null);
            checkDatasetSize(dsgBase, 1);

            long c1 = count(URLauthz, QUERY_ALL, USER_PERMIT);
            // Default is deny.
            assertEquals(0L, c1);
            // The storage should have the triple.
            checkDatasetSize(dsgBase, 1);
        };
        runTestProcessorSAGWithAuth(action);
    }

    /** Add quad. Ignored. */
    @Test
    void processorSAG_patch_5_quad() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URLauthz = server.datasetURL(DS_NAME);
            checkDatasetSize(dsgBase, 0);

            // Warning - not an error.
            LibTestsSAG.withLevel(FusekiKafka.LOG, "ERROR", ()->{
                processorRequest(proc, """
                        A <http://ex/s2> <http://ex/p> "triple2" <http://ex/namedGraph> .
                        """, WebContent.contentTypePatch, attrPermit);
            });
            checkDatasetSize(dsgBase, 1);
            long c1 = count(URLauthz, QUERY_ALL, USER_PERMIT);
            assertEquals(0L, c1);

            // Check quads count.
            checkDatasetSize(dsgBase, 1);
        };
        runTestProcessorSAGWithAuth(action);
    }

    /** Bad patch syntax. */
    @Test
    void processorSAG_patch_6_bad_patch() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URLauthz = server.datasetURL(DS_NAME);
            checkDatasetSize(dsgBase, 0);
            LibTestsSAG.withLevel(FusekiKafka.LOG, "FATAL", ()->{
                processorRequest(proc, "A JUNK .", WebContent.contentTypePatch, null);
            });
            checkDatasetSize(dsgBase, 0);

            long c1 = count(URLauthz, QUERY_ALL, USER_PERMIT);
            assertEquals(0L, c1);
            checkDatasetSize(dsgBase, 0);
        };
        runTestProcessorSAGWithAuth(action);
    }

    @Test
    void processorSAG_load_good_notInBatch() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(DS_NAME);
            checkDatasetSize(dsgBase, 0);
            String messageBody = """
                    PREFIX : <http://example/>
                    :s :p "turtle" .
                    """;
            Map<String, String> headers = Map.of(HttpNames.hContentType, WebContent.contentTypeTurtle, SysABAC.H_SECURITY_LABEL, attrOther.asString());
            byte[] bytes = Bytes.asUTF8bytes(messageBody);
            RequestFK request = new RequestFK("knowledge", headers, bytes);
            LibTestsSAG.withLevel(FusekiKafka.LOG, "FATAL", ()->{
                ResponseFK response = proc.process(request);
                assertNotNull(response);
            });
            // It happened even though not batched.
            checkDatasetSize(dsgBase, 1);
            long c1 = count(URL, QUERY_ALL, USER_PERMIT);
            assertEquals(0L, c1, "Count (user:permit)");
            long c2 = count(URL, QUERY_ALL, USER_OTHER);
            assertEquals(1L, c2, "Count (user:other)");
            checkDatasetSize(dsgBase, 1);
        };
        runTestProcessorSAGWithAuth(action);
    }

    @Test
    void processorSAG_load_good_differentDSG_noSecurityApplied() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(DS_NAME);
            checkDatasetSize(dsgBase, 0);
            String messageBody = """
                    PREFIX : <http://example/>
                    :s :p "turtle" .
                    """;
            Map<String, String> headers = Map.of(HttpNames.hContentType, WebContent.contentTypeTurtle);
            byte[] bytes = Bytes.asUTF8bytes(messageBody);
            RequestFK request = new RequestFK("knowledge", headers, bytes);

            LibTestsSAG.withLevel(FusekiKafka.LOG, "FATAL", ()->{
                ResponseFK response = proc.process(request);
                assertNotNull(response);
            });
            // Test was on a different, non-auth dataset
            checkDatasetSize(dsgBase, 0);

            long c1 = count(URL, QUERY_ALL, USER_PERMIT);
            assertEquals(1L, c1, "Count (user:permit)");
            long c2 = count(URL, QUERY_ALL, USER_OTHER);
            assertEquals(1L, c2, "Count (user:other)");
            // Look at the alternative dataset used in the test.
            checkDatasetSize(server.getDataAccessPointRegistry().get(DS_NAME).getDataService().getDataset(), 1);
        };
        runTestProcessorSAGWithGivenDSG(action, DatasetGraphFactory.create());
    }

    @Test
    void processorSAG_load_good_patch_differentDSG_noLabel() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(DS_NAME);
            checkDatasetSize(dsgBase, 0);
            processorRequest(proc, """
                    TX .
                    PA "ex" <http://ex/> .
                    A <http://ex/s1> <http://ex/p> "triple1" .
                    A <http://ex/s2> <http://ex/p> "triple2" .
                    TC .
                    """, WebContent.contentTypePatch, null);
            // Test was on a different dataset
            checkDatasetSize(dsgBase, 0);

            long c1 = count(URL, QUERY_ALL, USER_PERMIT);
            assertEquals(2L, c1);
            long c2 = count(URL, QUERY_ALL, USER_OTHER);
            assertEquals(2L, c2);
            checkDatasetSize(server.getDataAccessPointRegistry().get(DS_NAME).getDataService().getDataset(), 2);
        };
        runTestProcessorSAGWithGivenDSG(action, DatasetGraphFactory.createTxnMem());
    }

    @Test
    void processorSAG_load_bad_securityNotSupported() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(DS_NAME);
            checkDatasetSize(dsgBase, 0);
            String messageBody = """
                    PREFIX : <http://example/>
                    :s :p "turtle" .
                    """;
            Map<String, String> headers = Map.of(HttpNames.hContentType, WebContent.contentTypeTurtle, SysABAC.H_SECURITY_LABEL, attrOther.asString());
            byte[] bytes = Bytes.asUTF8bytes(messageBody);
            RequestFK request = new RequestFK("knowledge", headers, bytes);

            LibTestsSAG.withLevel(FusekiKafka.LOG, "FATAL", ()->{
                ResponseFK response = proc.process(request);
                assertNotNull(response);
            });
            // Load didn't happen.
            checkDatasetSize(dsgBase, 0);

            long c1 = count(URL, QUERY_ALL, USER_PERMIT);
            assertEquals(0L, c1, "Count (user:permit)");
            long c2 = count(URL, QUERY_ALL, USER_OTHER);
            assertEquals(0L, c2, "Count (user:other)");

            // Check no change
            checkDatasetSize(dsgBase, 0);
        };

        // Instead of auth setup, use this.
        runTestProcessorSAGWithGivenDSG(action, DatasetGraphFactory.createTxnMem());
    }

    @Test
    void processorSAG_load_patch_differentDSG_securityLabelNotHandled() {
        TestAction action = (FKProcessor proc, FusekiServer server, DatasetGraph dsgBase) -> {
            String URL = server.datasetURL(DS_NAME);
            String URLbase = server.datasetURL(DS_BASE);
            checkDatasetSize(dsgBase, 0);

            LibTestsSAG.withLevel(FusekiKafka.LOG, "FATAL", ()->{
                processorRequest(proc, """
                        TX .
                        PA "ex" <http://ex/> .
                        A <http://ex/s1> <http://ex/p> "triple1" .
                        A <http://ex/s2> <http://ex/p> "triple2" .
                        TC .
                        """, WebContent.contentTypePatch, attrPermit);
            });

            long c1 = count(URL, QUERY_ALL, USER_PERMIT);
            assertEquals(0L, c1);
            long c2 = count(URL, QUERY_ALL, USER_OTHER);
            assertEquals(0L, c2);

            checkDatasetSize(server.getDataAccessPointRegistry().get(DS_NAME).getDataService().getDataset(), 0);
        };

        runTestProcessorSAGWithGivenDSG(action, DatasetGraphFactory.createTxnMem());
    }

    private long count(String URL, String queryString, String user) {
        RowSet rowSet =
                (user == null )
                ? queryNoToken(URL, queryString)
                : queryWithToken(URL, queryString, user);
        long c = RowSetOps.count(rowSet);
        return c;
    }

    private void checkDatasetSize(DatasetGraph dsg, int expectedCount) {
        try ( QueryExec qExec = QueryExecDataset.newBuilder().dataset(dsg).query(QUERY_ALL).build() ) {
            RowSet rowSet = qExec.select();
            long c = RowSetOps.count(rowSet);
            assertEquals(expectedCount, c, "Dataset size");
        }
    }

    private void checkSizeNoAuth(FusekiServer server, String serviceName, long expectedCount) {
        String URL = server.datasetURL(serviceName);
        // No auth call.
        long c = count(URL, QUERY_ALL, null);
        // The storage should have the triple.
        assertEquals(expectedCount, c);
    }

    private void processorRequest(FKProcessor proc, String body, String contentType, AttributeValue securityLabel) {
        Map<String, String> headers = (securityLabel == null)
                                      ? Map.of(HttpNames.hContentType, contentType)
                                      : Map.of(HttpNames.hContentType, contentType, SysABAC.H_SECURITY_LABEL, securityLabel.asString());
        processorRequest(proc, body, headers);
    }

    private void processorRequest(FKProcessor proc, String body, Map<String, String> headers) {
        byte[] bytes = Bytes.asUTF8bytes(body);
        RequestFK request = new RequestFK("knowledge", headers, bytes);
        long startOffset = 900;
        int batchSize = 1;
        long finishOffset = 900 + batchSize;
        proc.startBatch(1, startOffset);
        ResponseFK response = proc.process(request);
        proc.finishBatch(batchSize, startOffset,  finishOffset);
        assertNotNull(response);
    }

    private void runTestProcessorSAGWithAuth(TestAction execTestAction) {
        // Setup a DatasetGraphABAC in a Fuseki/SAG with a FKProcessor.
        DatasetGraph dsgBase = DatasetGraphFactory.createTxnMem();
        LabelsStore labelsStore = Labels.createLabelsStoreMem();
        AttributesStoreModifiable attributesStore = new AttributesStoreLocal();
        // Register - no attributes.
        attributesStore.put(USER_PUBLIC, AttributeValueSet.of());
        attributesStore.put(USER_PERMIT, AttributeValueSet.of("PERMIT"));
        attributesStore.put(USER_OTHER,  AttributeValueSet.of("OTHER"));

        DatasetGraphABAC dsgz = ABAC.authzDataset(dsgBase,
                                                  SysABAC.ALLOW_LABEL,   // API access label
                                                  labelsStore,
                                                  SysABAC.DENY_LABEL,    // Dataset data label default.
                                                  attributesStore);
        DataService dataSrv = DataService
                .newBuilder(dsgz)
                .addEndpoint(Operation.Query)
                .build();
        runTestProcessorWithGivenDSGAndService(execTestAction, dsgBase, dsgz, dataSrv);
    }

    private void runTestProcessorSAGWithGivenDSG(TestAction execTestAction, DatasetGraph dsg) {
        DatasetGraph dsgBase = DatasetGraphFactory.createTxnMem();
        DataService dataSrv = DataService.newBuilder(dsg)
                                         .addEndpoint(Operation.Query)
                                         .build();
        runTestProcessorWithGivenDSGAndService(execTestAction, dsgBase, dsg, dataSrv);
    }

    private void runTestProcessorWithGivenDSGAndService(TestAction execTestAction, DatasetGraph dsgBase, DatasetGraph dsg, DataService dataSrv) {
        FusekiServer server = SecureAgentGraph.serverBuilder().port(0)
                                             .add(DS_NAME, dataSrv)
                                             .add(DS_BASE, dsgBase)
                                             .build();
        server.start();
        try {
            FKProcessor proc = new FKProcessorSAG(dsg, "http://base/request/", server);
            execTestAction.execTest(proc, server, dsgBase);
        } finally { server.stop(); }
    }

    private static void dumpState(LabelsStore labelsStore, AttributesStore attributesStore) {
        dumpLabelStore(labelsStore);
        dumpAttributesStore(attributesStore);
        System.out.println();
    }

    private static void dumpLabelStore(LabelsStore labelsStore) {
        if ( labelsStore != null ) {
            System.out.println("-- Labels");
            RDFWriter.source(labelsStore.asGraph()).lang(Lang.TTL).output(System.out);
        } else
            System.out.println("-- Labels -- null");
        System.out.println();
    }

    private static void dumpAttributesStore(AttributesStore attributesStore) {
        if ( attributesStore != null ) {
            System.out.println("-- User Attributes");
            attributesStore.users().forEach(u -> {
                AttributeValueSet avs = attributesStore.attributes(u);
                System.out.printf("%s %s\n", u, avs);
            });
        } else {
            System.out.println("-- User Attributes -- null");
        }
    }
}
