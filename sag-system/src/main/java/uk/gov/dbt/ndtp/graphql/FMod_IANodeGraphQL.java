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

package uk.gov.dbt.ndtp.graphql;

import java.util.Set;

import uk.gov.dbt.ndtp.jena.abac.fuseki.ServerABAC;
import uk.gov.dbt.ndtp.jena.graphql.execution.GraphQLOverDatasetExecutor;
import uk.gov.dbt.ndtp.jena.graphql.fuseki.FMod_GraphQL;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.servlets.ActionProcessor;
import org.apache.jena.rdf.model.Model;

/**
 * A Fuseki module that provides a new IANode GraphQL operation
 */
public class FMod_IANodeGraphQL extends FMod_GraphQL {

    private static final String VERSION = Version.versionForClass(FMod_GraphQL.class).orElse("<unset>");

    /**
     * Creates the IANode GraphQL module
     */
    public FMod_IANodeGraphQL() {
        super();
    }

    @Override
    public String name() {
        return "IANode GraphQL Schema";
    }

    @Override
    public void prepare(FusekiServer.Builder serverBuilder, Set<String> datasetNames, Model configModel) {
        FmtLog.info(Fuseki.configLog, "IANode GraphQL Fuseki Module (%s)", VERSION);
    }

    @Override
    protected ActionProcessor createActionProcessor(GraphQLOverDatasetExecutor executor) {
        return new ActionIANodeGraphQL(executor, ServerABAC.userForRequest());
    }
}
