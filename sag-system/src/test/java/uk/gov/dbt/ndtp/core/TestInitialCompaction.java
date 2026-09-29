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

package uk.gov.dbt.ndtp.core;

import uk.gov.dbt.ndtp.LibTestsSAG;
import uk.gov.dbt.ndtp.jena.abac.ABAC;
import uk.gov.dbt.ndtp.jena.abac.SysABAC;
import uk.gov.dbt.ndtp.jena.abac.lib.Attributes;
import uk.gov.dbt.ndtp.jena.abac.lib.AttributesStoreLocal;
import uk.gov.dbt.ndtp.jena.abac.lib.DatasetGraphABAC;
import uk.gov.dbt.ndtp.jena.abac.labels.Labels;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.servlets.ActionErrorException;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.tdb1.TDB1Factory;
import org.apache.jena.tdb1.base.file.Location;
import org.apache.jena.tdb2.DatabaseMgr;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Set;

import static uk.gov.dbt.ndtp.TestJwtServletAuth.makePOSTCallWithPath;
import static uk.gov.dbt.ndtp.TestSecureAgentGraphIntegration.launchServer;
import static uk.gov.dbt.ndtp.core.FMod_InitialCompaction.compactLabels;
import static org.apache.jena.graph.Graph.emptyGraph;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestInitialCompaction {
    private static final MockedStatic<DatabaseMgr> mockDatabaseMgr  = mockStatic(DatabaseMgr.class, Mockito.CALLS_REAL_METHODS);
    private static FusekiServer server;

    @BeforeAll
    static void createAndSetupServerDetails() throws Exception {
        LibTestsSAG.setupAuthentication();
        FusekiLogging.setLogging();
        Attributes.buildStore(emptyGraph);
    }

    @AfterEach
    void clearDown() {
       mockDatabaseMgr.clearInvocations();
       mockDatabaseMgr.reset();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.stop();
        }
        LibTestsSAG.teardownAuthentication();
        mockDatabaseMgr.close();
    }

    @Test
    void test_emptyGraph() {
        // given, when
        server = SecureAgentGraph.construct("--port=0", "--empty").start();
        // then
        assertNotNull(server.serverURL());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    void test_authMemoryDataset() {
        // given
        String configFile = "config-no-hierarchies.ttl";
        // when
        server = launchServer(configFile);
        // then
        assertNotNull(server.serverURL());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    void test_persistentDataset() {
        // given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        mockDatabaseMgr.clearInvocations();
        String configFile = "config-persistent.ttl";
        // when
        server = launchServer(configFile);
        // then
        assertNotNull(server.serverURL());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(1));
    }

    @Test
    void test_persistentDataset_sizeSame_ignoredSecondCall() {
        // given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent.ttl";
        // when
        server = launchServer(configFile);
        // then
        assertNotNull(server.serverURL());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(1));
        mockDatabaseMgr.clearInvocations();
        // when
        HttpResponse<InputStream> compactResponse = makePOSTCallWithPath(server, "$/compactall");
        // then
        assertEquals(200, compactResponse.statusCode());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    void test_persistentDataset_sizeDifferent_makeSecondCall() {
        // given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent.ttl";
        // when
        server = launchServer(configFile);
        // then
        assertNotNull(server.serverURL());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(1));
        // when
        FMod_InitialCompaction.sizes.put("/knowledge", 500L);
        HttpResponse<InputStream> compactResponse = makePOSTCallWithPath(server, "$/compactall");
        // then
        assertEquals(200, compactResponse.statusCode());
    }


    @Test
    void test_name() {
        // given
        FMod_InitialCompaction fModInitialCompaction = new FMod_InitialCompaction();
        // when
        String name = fModInitialCompaction.name();
        // then
        assertNotNull(name);
    }

    @Test
    void test_TDB1_DSG() {
        // given
        DatasetGraph tdb1_DG = TDB1Factory.createDatasetGraph(Location.mem());
        // when
        DatasetGraph actualDSG = FMod_InitialCompaction.getTDB2(tdb1_DG);
        // then
        assertNull(actualDSG);
    }

    @Test
     void test_ABACDSG_wrapped_memGraph() {
        // given
        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                SysABAC.ALLOW_LABEL,
                Labels.createLabelsStoreMem(),
                SysABAC.DENY_LABEL,
                new AttributesStoreLocal());
        // when
        DatasetGraph actualDSG = FMod_InitialCompaction.getTDB2(dsgABAC);
        // then
        assertNull(actualDSG);
    }

    @Test
    void test_ABACDSG_wrapped_persistedGraph() {
        // given
        DatasetGraphSwitchable dsgPersists = new DatasetGraphSwitchable(Path.of("./"), null, DatasetGraphFactory.createTxnMem());
        DatasetGraphABAC dsgABAC = ABAC.authzDataset(dsgPersists,
                SysABAC.ALLOW_LABEL,
                Labels.createLabelsStoreMem(),
                SysABAC.DENY_LABEL,
                new AttributesStoreLocal());
        // when
        DatasetGraph actualDSG = FMod_InitialCompaction.getTDB2(dsgABAC);
        // then
        assertNotNull(actualDSG);
    }

    @Test
    void test_noServices() {
        // given
        server = SecureAgentGraph.construct("--port=0", "--empty").start();
        FMod_InitialCompaction fModInitialCompaction = new FMod_InitialCompaction();
        // when
        fModInitialCompaction.serverBeforeStarting(server);
        // then
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    void test_wrongServices() {
        // given
        server = SecureAgentGraph.construct("--port=0", "--empty").start();
        FMod_InitialCompaction fModInitialCompaction = new FMod_InitialCompaction();
        fModInitialCompaction.prepare(null, Set.of("Missing", "NotThere"), null);
        // when
        fModInitialCompaction.serverAfterStarting(server);
        // then
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    void test_databaseSize_handlesInvalidInput() {
        // given
        DatasetGraph emptyDsg = DatasetGraphFactory.create();
        long expectedSize = -1;
        // when
        long actualSize = FMod_InitialCompaction.findDatabaseSize(emptyDsg);
        // then
        assertEquals(expectedSize, actualSize);
    }

    @Test
    void test_databaseSize_invalidData() {
        // given
        DatasetGraphSwitchable mockDSG = mock(DatasetGraphSwitchable.class);
        when(mockDSG.getContainerPath()).thenReturn(Path.of("/doesnotexist/tmp.txt"));
        long expectedSize = -1;
        // when
        long actualSize = FMod_InitialCompaction.findDatabaseSize(mockDSG);
        // then
        assertEquals(expectedSize, actualSize);
    }

    @Test
    void test_humanReadableSize_handlesInvalidInput() {
        // given
        String expectedSize = "Unknown";
        // when
        String actualSize = FMod_InitialCompaction.humanReadableSize(-5000);
        // then
        assertEquals(expectedSize, actualSize);
    }

    @Test
    void test_compactWithExistingLock()  {
        // given
        DatasetGraphSwitchable dsgPersists = new DatasetGraphSwitchable(Path.of("./"), null, DatasetGraphFactory.createTxnMem());
        DatasetGraphSwitchable mockedDsg = Mockito.spy(dsgPersists);

        when(mockedDsg.tryExclusiveMode(false)).thenReturn(false);
        doNothing().when(mockedDsg).finishExclusiveMode();
        server = SecureAgentGraph.secureAgentGraphBuilder().add("test", mockedDsg).build().start();

        // when
        HttpResponse<InputStream> compactResponse = makePOSTCallWithPath(server, "$/compactall");
        // then
        assertEquals(200, compactResponse.statusCode());
        mockDatabaseMgr.verify(() -> DatabaseMgr.compact(any(), anyBoolean()), times(0));
    }

    @Test
    void test_compactAll_happyPath() {
        // given
        mockDatabaseMgr.when(() -> DatabaseMgr.compact(any(), anyBoolean())).thenAnswer(invocationOnMock -> null);
        String configFile = "config-persistent.ttl";
        server = launchServer(configFile);
        // when
        HttpResponse<InputStream> compactResponse = makePOSTCallWithPath(server, "$/compactall");
        // then
        assertEquals(200, compactResponse.statusCode());
    }

    @Test
    void test_compactLabels_notABAC() {
        // given
        DatasetGraph dsgNotABAC = TDB1Factory.createDatasetGraph(Location.mem());
        // when
        // then
        assertDoesNotThrow(()-> compactLabels(dsgNotABAC));
    }

    @Test
    void test_compactLabels_notRocksDB() {
        // given
        DatasetGraphABAC dsgABAC = ABAC.authzDataset(DatasetGraphFactory.createTxnMem(),
                SysABAC.ALLOW_LABEL,
                Labels.createLabelsStoreMem(),
                SysABAC.DENY_LABEL,
                new AttributesStoreLocal());
        // when
        // then
        assertDoesNotThrow(()->compactLabels(dsgABAC));
    }

    @Test
    void test_configured_exception() {
        // given
        FMod_InitialCompaction fModInitialCompaction = new FMod_InitialCompaction();
        // when
        // then
        FusekiServer.Builder mockBuilder = mock(FusekiServer.Builder.class);

        ArgumentCaptor<HttpServlet> servletCaptor = ArgumentCaptor.forClass(HttpServlet.class);
        when(mockBuilder.addServlet(any(), servletCaptor.capture())).thenReturn(mockBuilder);

        fModInitialCompaction.configured(mockBuilder, null, null);

        verify(mockBuilder).addServlet(eq("/$/compactall"), any(HttpServlet.class));
        HttpServlet capturedServlet = servletCaptor.getValue();


        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");

        HttpServletResponse response = mock(HttpServletResponse.class);
        assertThrows(ActionErrorException.class, () ->capturedServlet.service(request, response));
    }
}
