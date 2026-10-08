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

package uk.gov.dbt.ndtp.backup.services;

import com.fasterxml.jackson.databind.node.ObjectNode;
import uk.gov.dbt.ndtp.jena.abac.labels.LabelsStoreRocksDB;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.sparql.core.DatasetGraph;

import java.util.HashMap;
import java.util.Map;

/**
 * Class that extends DatasetBackupService purely for testing purposes
 */
public class DatasetBackupService_Helper extends DatasetBackupService {
    public static final String BACKUP_TDB = "executeBackupTDB";
    public static final String BACKUP_LABELS = "executeBackupLabelStore";
    public static final String RESTORE_TDB = "executeRestoreTDB";
    public static final String RESTORE_LABELS = "executeRestoreLabelStore";
    public static final String DELETE_BACKUP_DIR = "executeDeleteBackup";

    public static Map<String, Integer> callCounts = new HashMap<>();
    public static Map<String, String> throwExceptions = new HashMap<>();

    public static void setupExceptionForMethod(String method, String message) {
        throwExceptions.put(method, message);
    }

    public static void throwExceptionIfNeeded(String method) {
        if (throwExceptions.containsKey(method)) {
            throw new RuntimeException(throwExceptions.get(method));
        }
    }

    public static void incrementMethodCall(String methodName) {
        callCounts.compute(methodName, (key, value) -> value == null ? 1 : value + 1);
    }

    public static int getCallCount(String methodName) {
        return callCounts.getOrDefault(methodName, 0);
    }

    // Review and replace with a mock
    public DatasetBackupService_Helper(DataAccessPointRegistry dapRegistry) {
        super(dapRegistry);
    }

    public static void clear() {
        callCounts.clear();
        throwExceptions.clear();
    }

    @Override
    void executeBackupTDB(DatasetGraph dsg, String backupFile, ObjectNode node) {
        // NO-OP
        incrementMethodCall(BACKUP_TDB);
        throwExceptionIfNeeded(BACKUP_TDB);
        node.put("success", true);
    }

    @Override
    void executeBackupLabelStore(LabelsStoreRocksDB rocksDB, String labelBackupPath, ObjectNode node) {
        // NO-OP
        incrementMethodCall(BACKUP_LABELS);
        throwExceptionIfNeeded(BACKUP_LABELS);
        node.put("success", true);
    }

    @Override
    void executeRestoreTDB(DatasetGraph dsg, String tdbRestoreFile) {
        // NO-OP
        incrementMethodCall(RESTORE_TDB);
        throwExceptionIfNeeded(RESTORE_TDB);
    }

    @Override
    void executeRestoreLabelStore(LabelsStoreRocksDB rocksDB, String labelRestorePath, ObjectNode node) {
        // NO-OP
        incrementMethodCall(RESTORE_LABELS);
        throwExceptionIfNeeded(RESTORE_LABELS);
        node.put("success", true);
    }

    @Override
    void executeDeleteBackup(String deletePath) {
        // NO-OP
        incrementMethodCall(DELETE_BACKUP_DIR);
        throwExceptionIfNeeded(DELETE_BACKUP_DIR);
    }
}
