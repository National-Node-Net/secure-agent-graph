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

import uk.gov.dbt.ndtp.backup.FMod_BackupData;
import uk.gov.dbt.ndtp.graphql.FMod_IANodeGraphQL;
import uk.gov.dbt.ndtp.jena.abac.fuseki.FMod_ABAC;
import uk.gov.dbt.ndtp.otel.FMod_OpenTelemetry;
import uk.gov.dbt.ndtp.secure.agent.configuration.Configurator;
import uk.gov.dbt.ndtp.secure.agent.configuration.auth.AuthConstants;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.cmds.FusekiMain;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yamlconfig.ConfigStruct;
import yamlconfig.RDFConfigGenerator;
import yamlconfig.YAMLConfigParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SecureAgentGraph {
    /**
     * Software version taken from the jar file.
     */
    public static final String VERSION = version();

    public final static Logger log = LoggerFactory.getLogger("fuseki-yaml-config");

    private static String version() {
        return Version.versionForClass(SecureAgentGraph.class).orElse("<development>");
    }

    /**
     * FusekiServer.Builder for a Fuseki server configured for SecureAgentGraph
     */
    public static FusekiServer.Builder secureAgentGraphBuilder() {
        return FusekiServer.create().fusekiModules(modules());
    }

    /**
     * Builder for a Fuseki server configured for SecureAgentGraph
     */
    public static FusekiServer construct(String... args) {
        convertYamlConfigToRDF(args);
        FusekiModules fmods = modules();
        FusekiServer server = FusekiMain
                .builder(args)
                .fusekiModules(fmods)
                .enablePing(true)
                .build();
        return server;
    }

    /**
     * Set up a builder for a Fuseki/SecureAgentGraph (used when embedding and testing SecureAgentGraph)
     */
    public static FusekiServer.Builder serverBuilder() {
        FusekiModules fmods = modules();
        return FusekiServer.create().fusekiModules(fmods).enablePing(true);
    }

    /**
     * The fixed set of Fuseki Modules that are explicitly configured for the FusekiServer instance built for SAG
     * <p>
     * Only modules in this list are loaded. Use of {@link java.util.ServiceLoader} and
     * {@link org.apache.jena.fuseki.main.sys.FusekiAutoModule} for loading additional modules is not supported.
     * </p>
     *
     * @return FusekiModules
     */
    public static FusekiModules modules() {
        List<FusekiModule> mods = new ArrayList<>();
        mods.add(new FMod_CQRS());
        // Only add the ABAC mode when authentication is enabled
        if (isAuthEnabled()) {
            mods.add(new FMod_ABAC());
        }

        // Initial compaction gets applied twice, once before Kafka module and once after, this is because we want to
        // clean up any bloat from previous instances of the service before we read new data from Kafka.  Then we want
        // to clean up any further bloat created by catching up with Kafka.
        // The same instance of the module is used in both places so it can use an instance variable to track the
        // database sizes and see if further compactions are needed
        FMod_InitialCompaction compaction = new FMod_InitialCompaction();
        if (isInitialCompactionEnabled()) {
            mods.add(compaction);
        }

        mods.addAll(List.of(
                new FMod_FusekiKafkaSAG()
                , new FMod_JwtServletAuth()
                , new FMod_OpenTelemetry()
                , new FMod_IANodeGraphQL()
                , new FMod_RequestIDFilter()
                , new FMod_BackupData()
        ));

        // Initial compaction gets added again per the earlier comments
        if (isInitialCompactionEnabled()) {
            mods.add(compaction);
        }
        return FusekiModules.create(mods);
    }

    private static boolean isAuthEnabled() {
        String jwksUrl = Configurator.get(AuthConstants.ENV_JWKS_URL);
        return !Objects.equals(jwksUrl, AuthConstants.AUTH_DISABLED);
    }

    private static boolean isInitialCompactionEnabled() {
        return !Configurator.get(FMod_InitialCompaction.DISABLE_INITIAL_COMPACTION, Boolean::parseBoolean, false);
    }

    private static void convertYamlConfigToRDF(String... args) {
        YAMLConfigParser ycp = new YAMLConfigParser();
        RDFConfigGenerator rcg = new RDFConfigGenerator();
        String pattern = ".*\\.(yaml|yml)$";
        Pattern regex = Pattern.compile(pattern);

        for (int i = 0; i < args.length; i++) {
            if (isConfigArgument(args, i)) {
                String configPath = args[i + 1];
                if (regex.matcher(configPath).matches()) {
                    processConfigPath(ycp, rcg, args, i, configPath);
                }
            }
        }
    }

    private static boolean isConfigArgument(String[] args, int index) {
        return StringUtils.equalsAnyIgnoreCase(args[index], "--config", "--conf") && index + 1 < args.length;
    }

    private static void processConfigPath(YAMLConfigParser ycp, RDFConfigGenerator rcg, String[] args, int index, String configPath) {
        try {
            ConfigStruct configStruct = ycp.runYAMLParser(configPath);
            Model configModel = rcg.createRDFModel(configStruct);

            // Create a secure temp directory and pass it to the temp file
            String homeDir = System.getProperty("user.home");
            File secureTempPath = new File(homeDir, ".sag-temp");

            if (!secureTempPath.exists()) {
                if (!secureTempPath.mkdirs()) {
                    throw new IOException("Could not create secure temp directory");
                }
            }

            File rdfConfigPath = File.createTempFile( "generated-config-", ".ttl", secureTempPath);
            rdfConfigPath.deleteOnExit();

            try (FileOutputStream out = new FileOutputStream(rdfConfigPath)) {
                configModel.write(out, "TTL", new File(configPath).getAbsoluteFile().getParentFile().toURI().toString());
                args[index + 1] = rdfConfigPath.getPath();
            } catch (IOException e) {
                log.error(e.getMessage());
                throw new RuntimeException(e.getMessage());
            }
        } catch (RuntimeException | IOException e) {
            throw new RuntimeException("Failure parsing the YAML config file: " + e.getMessage());
        }
    }
}
