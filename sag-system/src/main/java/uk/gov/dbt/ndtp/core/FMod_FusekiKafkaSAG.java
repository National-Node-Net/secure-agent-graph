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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.node.ObjectNode;
import uk.gov.dbt.ndtp.backup.services.DatasetBackupService;
import org.apache.jena.atlas.io.IOX;
import org.apache.jena.atlas.lib.Pair;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.kafka.FKBatchProcessor;
import org.apache.jena.fuseki.kafka.FKProcessor;
import org.apache.jena.fuseki.kafka.FKS;
import org.apache.jena.fuseki.kafka.FMod_FusekiKafka;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.servlets.ActionProcessor;
import org.apache.jena.kafka.KConnectorDesc;
import org.apache.jena.kafka.SysJenaKafka;
import org.apache.jena.kafka.common.DataState;
import org.apache.jena.kafka.common.PersistentState;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.core.DatasetGraph;

import static org.apache.jena.kafka.FusekiKafka.LOG;

/**
 * Extension of {@link FMod_FusekiKafka} that directly applies a Kafka message batch.
 */
public class FMod_FusekiKafkaSAG extends FMod_FusekiKafka {

    public static final String NODE_SUCCESS = "success";
    private static final String NODE_REASON = "reason";
    public FMod_FusekiKafkaSAG() { super(); }

    Map<String, KConnectorDesc> connectors = new HashMap<>();

    private static void init() {
        //default implementation override
    }

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> names, Model configModel) {
        super.prepare(builder, names, configModel);
        DatasetBackupService.registerMethods("kafka", this::backupKafka, this::restoreKafka);
    }

        @Override
    protected String logMessage() {
        return String.format("Fuseki-Kafka Connector Module SAG (%s)", SysJenaKafka.VERSION);
    }

    @Override
    public void serverBeforeStarting(FusekiServer server) {
        // No-op, intentionally DON'T start connectors prior to server start
        // Otherwise the interaction with our batch processor means we can get into a crash restart
        // loop if SAG is significantly behind the Kafka topic(s) it is configured to read
    }

    @Override
    public void serverAfterStarting(FusekiServer server) {
        // Start the connectors after the server has started otherwise we can find ourselves in a
        // crash restart loop if SAG is significantly behind the Kafka topic(s) it is configured to read
        super.startKafkaConnectors(server);

        // Still need to call the regular serverAfterStarting() as that does some clean up
        super.serverAfterStarting(server);
    }

    @Override
    protected FKBatchProcessor makeFKBatchProcessor(KConnectorDesc conn, FusekiServer server) {
        String dispatchPath = conn.getLocalDispatchPath();
        connectors.put(dispatchPath, conn);
        DatasetGraph dsg = determineDataset(server, dispatchPath);
        FKProcessor requestProcessor = new FKProcessorSAG(dsg, dispatchPath, server);
        // Pass dsg as the transactional. Each batch will executed by
        // requestProcessor inside a single transaction.
        // See FKBatchProcessor.batchProcess.
        return new FKBatchProcessor(dsg, requestProcessor);
    }

    /** Find the dataset for direct operation, not via an endpoint */
    private static DatasetGraph determineDataset(FusekiServer server, String dispatchPath) {
        Optional<DatasetGraph> optDSG = FKS.findDataset(server, dispatchPath);
        if ( optDSG.isPresent() )
            return optDSG.get();

        // Not dataset directly.
        Pair<ActionProcessor, DatasetGraph> deliveryPoint = FKS.findActionProcessorDataset(server, dispatchPath);
        // Use the HTTP operation.
        // (Note: 2023-07) Reinstate code when the configurations are all changed.
//        if ( deliveryPoint.car() == null )
//            return super.makeFKBatchProcessor(conn, server);

        // Legacy - used a HTTP endpoint for the destination of Kafka messages.
        // We find the dataset itself, ignore the ActionProcessor, and
        // do directly with FKProcessorSAG, with a batch of operations
        // wrapped in a single transaction by FKBatchProcessor.
        return deliveryPoint.cdr();
    }

    public void backupKafka(DataAccessPoint dataAccessPoint, String path, ObjectNode resultNode) {
        String dataset = dataAccessPoint.getName();
        KConnectorDesc conn = connectors.get(dataset);
        if (conn != null) {
            String filename = path + "/" + dataset + ".json";
            IOX.copy(conn.getStateFile(), filename);
            resultNode.put("source", conn.getStateFile());
            resultNode.put("destination", filename);
            resultNode.put(NODE_SUCCESS, true);
        } else {
            String errorMessage = String.format("Unable to back up Kafka as dataset %s not recognised. ", dataset);
            FmtLog.info(LOG, errorMessage);
            resultNode.put(NODE_SUCCESS, false);
            resultNode.put(NODE_REASON, errorMessage);
        }
    }

    public void restoreKafka(DataAccessPoint dataAccessPoint, String path, ObjectNode resultNode) {
        String dataset = dataAccessPoint.getName();
        if (connectors.get(dataset) != null) {
            String filename = path + "/" + dataset + ".json";
            PersistentState persistentState = new PersistentState(filename);
            if ( persistentState.getBytes().length == 0 ) {
                String errorMessage = String.format("Unable to restore Kafka for dataset (%s) as restore file (%s) not suitable. ", dataset, filename);
                FmtLog.info(LOG, errorMessage);
                resultNode.put(NODE_SUCCESS, false);
                resultNode.put(NODE_REASON, errorMessage);
                return;
            }
            DataState dataState = DataState.create(persistentState);
            FKS.restoreOffsetForDataset(dataset, dataState.getLastOffset());
            resultNode.put("offset", dataState.getLastOffset());
            resultNode.put(NODE_SUCCESS, true);
        } else {
            String errorMessage = String.format("Unable to restore Kafka as dataset %s not recognised. ", dataset);
            FmtLog.info(LOG, errorMessage);
            resultNode.put(NODE_SUCCESS, false);
            resultNode.put(NODE_REASON, errorMessage);
        }
    }
}
