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

package uk.gov.dbt.ndtp.backup.servlets;

import com.fasterxml.jackson.databind.node.ObjectNode;
import uk.gov.dbt.ndtp.backup.services.DatasetBackupService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.jena.atlas.lib.DateTimeUtils;
import uk.gov.dbt.ndtp.backup.utils.BackupUtils;

/**
 * Servlet class responsible for the deletion of backups.
 */
public class DeleteServlet extends HttpServlet {
    private final DatasetBackupService backupService;

    public DeleteServlet(DatasetBackupService backupService) {
        this.backupService = backupService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) {
        ObjectNode resultNode = BackupUtils.MAPPER.createObjectNode();
        try {
            String deleteId = request.getPathInfo();
            resultNode.put("delete-id", deleteId);
            resultNode.put("date", DateTimeUtils.nowAsString("yyyy-MM-dd_HH-mm-ss"));
            resultNode.set("delete", backupService.deleteBackup(deleteId));
            BackupUtils.processResponse(response, resultNode);
        } catch (Exception exception) {
            BackupUtils.handleError(response, resultNode, exception);
        }
    }
}
