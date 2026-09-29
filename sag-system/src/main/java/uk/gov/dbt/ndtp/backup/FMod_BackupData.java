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

package uk.gov.dbt.ndtp.backup;

import uk.gov.dbt.ndtp.backup.servlets.*;
import uk.gov.dbt.ndtp.backup.services.DatasetBackupService;
import uk.gov.dbt.ndtp.secure.agent.configuration.Configurator;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiAutoModule;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.rdf.model.Model;
import uk.gov.dbt.ndtp.backup.servlets.BackupServlet;
import uk.gov.dbt.ndtp.backup.servlets.DeleteServlet;
import uk.gov.dbt.ndtp.backup.servlets.ListBackupsServlet;
import uk.gov.dbt.ndtp.backup.servlets.RestoreServlet;

/**
 * Fuseki module responsible for the creating, restoring and administration of backup-data from the server.
 */
public class FMod_BackupData implements FusekiAutoModule {

    /**
     * Configuration flag to enable/disable functionality.
     */
    public static final String ENABLE_BACKUPS = "ENABLE_BACKUPS";

    @Override
    public String name() {
        return "Dataset Backups";
    }

    DatasetBackupService getBackupService(DataAccessPointRegistry dapRegistry) {
        return new DatasetBackupService(dapRegistry);
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        if (isBackupEnabled()) {
            DatasetBackupService backupService = getBackupService(dapRegistry);
            serverBuilder.addServlet("/$/backups/create/*", new BackupServlet(backupService));
            serverBuilder.addServlet("/$/backups/list/*", new ListBackupsServlet(backupService));
            serverBuilder.addServlet("/$/backups/restore/*", new RestoreServlet(backupService));
            serverBuilder.addServlet("/$/backups/delete/*", new DeleteServlet(backupService));
        }
    }

    private static boolean isBackupEnabled() {
        return Configurator.get(FMod_BackupData.ENABLE_BACKUPS, Boolean::parseBoolean, false);
    }
}
