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
import static uk.gov.dbt.ndtp.LibTestsSAG.queryWithToken;

import uk.gov.dbt.ndtp.core.SecureAgentGraph;
import uk.gov.dbt.ndtp.jena.abac.lib.Attributes;
import uk.gov.dbt.ndtp.jena.abac.lib.AttributesStore;
import uk.gov.dbt.ndtp.jena.abac.services.AttributeService;
import uk.gov.dbt.ndtp.jena.abac.services.LibAuthService;
import uk.gov.dbt.ndtp.jena.abac.services.SimpleAttributesStore;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.graph.Graph;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetOps;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Integration tests including mocks the SAG as deployed on AWS (or etc). */
public class TestSecureAgentGraphIntegration {

    public static final String DIR = "src/test/files";

    @BeforeAll
    static void beforeClass() throws Exception {
        FusekiLogging.markInitialized(false);
        FusekiLogging.setLogging();
        LibTestsSAG.setupAuthentication();
        LibTestsSAG.disableInitialCompaction();
    }

    @AfterAll
    static void afterClass() throws Exception {
        LibTestsSAG.teardownAuthentication();
    }

    @Test
    void integration_graphql_1() {
        runTest("config-graphql.ttl", DIR+"/data-hierarchies.trig", 2);
    }

    @Test
    void integration_graphql_2() {
        runTest("config-graphql-plain.ttl", DIR+"/data-plain.ttl", 4);
    }

    @Test
    void integration_no_hierarchies() {
        runTest("config-no-hierarchies.ttl", DIR+"/data-hierarchies.trig", 1);
    }

    private static void runTest(String configFile, String datafile, int expected) {
            runTest2(configFile, datafile, expected);
    }

    private static void runTest2(String configFile, String datafile, int expected) {
        FusekiServer server = launchServer(configFile);
        try {
            int port = server.getHttpPort();
            String URL = "http://localhost:" + port + "/ds";

            // Data and labelling.
            LibTestsSAG.uploadFile(URL+"/upload", DIR+"/data-hierarchies.trig");

            //CxtABAC.systemTrace(Track.DEBUG);
            RowSet rs = queryWithToken(URL, "SELECT * {?s ?p ?o}", "user1");
            //RowSetOps.out(rs);
            // With hierarchies: 2
            // Without hierarchies: 1
            long x = RowSetOps.count(rs);
            assertEquals(expected, x);
        } finally {
            if ( server != null )
                server.stop();
        }
    }

    /** Return the mock server URL */
    public static FusekiServer launchServer(String configFile) {
        // Remote Attribute Store
        Graph g = RDFParser.source(DIR+"/attribute-store.ttl").toGraph();
        AttributesStore attrStore = Attributes.buildStore(g);
        String mockAttributesStoreURL = SimpleAttributesStore.run(0, attrStore);

        String attributeStoreBaseURL = mockAttributesStoreURL;
        String lookupUserAttribesURL = LibAuthService.serviceURL(attributeStoreBaseURL, AttributeService.LOOKUP_USER_ATTRIBUTE_TEMPLATE);
        String lookupHierarchAttribesURL = LibAuthService.serviceURL(attributeStoreBaseURL, AttributeService.LOOKUP_HIERARCHY_TEMPLATE);

        System.setProperty("USER_ATTRIBUTES_URL", lookupUserAttribesURL);
        System.setProperty("ABAC_HIERARCHIES_URL", lookupHierarchAttribesURL);
        System.setProperty("AWS_REGION", "eu-west-1");

        FusekiServer server = SecureAgentGraph.construct("--port=0", "--conf", DIR+"/"+configFile).start();
        return server;
    }
}
