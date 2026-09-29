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

import uk.gov.dbt.ndtp.servlet.auth.jwt.JwtServletConstants;
import uk.gov.dbt.ndtp.servlet.auth.jwt.PathExclusion;
import uk.gov.dbt.ndtp.servlet.auth.jwt.configuration.AutomatedConfiguration;
import uk.gov.dbt.ndtp.servlet.auth.jwt.servlet5.JwtAuthFilter;
import uk.gov.dbt.ndtp.servlet.auth.jwt.verification.JwtVerifier;
import uk.gov.dbt.ndtp.secure.agent.configuration.Configurator;
import uk.gov.dbt.ndtp.secure.agent.configuration.auth.AuthConstants;
import uk.gov.dbt.ndtp.secure.agent.configuration.auth.IANodeConfigurationAdaptor;
import jakarta.servlet.FilterConfig;
import org.apache.jena.atlas.lib.Version;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.rdf.model.Model;

import java.util.Objects;
import java.util.Set;

public class FMod_JwtServletAuth implements FusekiModule {

    private static final String VERSION = Version.versionForClass(FMod_JwtServletAuth.class).orElse("<development>");

    @Override
    public String name() {
        return "JWT Servlet Authentication";
    }

    @Override
    public void prepare(FusekiServer.Builder serverBuilder, Set<String> datasetNames, Model configModel) {
        FmtLog.info(Fuseki.configLog, "IANode JWT Authentication Module (%s)", VERSION);

        String jwksUrl = Configurator.get(AuthConstants.ENV_JWKS_URL);
        if (Objects.equals(jwksUrl, AuthConstants.AUTH_DISABLED)) {
            Fuseki.configLog.warn(
                    "JWT Authentication explicitly disabled by configuration, no user authentication will be applied");
            return;
        }

        // Configure the JWT Verifier
        FusekiConfigurationAdaptor adaptor = new FusekiConfigurationAdaptor(serverBuilder);
        AutomatedConfiguration.configure(adaptor);
        JwtVerifier jwtVerifier = (JwtVerifier) adaptor.getAttribute(JwtServletConstants.ATTRIBUTE_JWT_VERIFIER);

        if (jwtVerifier == null) {
            FmtLog.error(Fuseki.configLog,
                         "Failed to configure JWT Authentication, %s environment variable was missing or contained invalid value",
                         AuthConstants.ENV_JWKS_URL);
            throw new RuntimeException("Failed to configure JWT Authentication");
        } else {
            FmtLog.info(Fuseki.configLog, "JWT Authentication engine is %s",
                        adaptor.getAttribute(JwtServletConstants.ATTRIBUTE_JWT_ENGINE));
            FmtLog.info(Fuseki.configLog, "JWT Authentication configured with verifier %s", jwtVerifier);
        }

        // Disable authentication for purely informative paths that are useful for health checks and metrics
        // Note some of these URLs aren't actually enabled for SAG currently but useful to future-proof our exclusions
        // should we enable these features in future
        serverBuilder.addServletAttribute(JwtServletConstants.ATTRIBUTE_PATH_EXCLUSIONS,
                                          PathExclusion.parsePathPatterns("/$/ping,/$/metrics,/\\$/stats/*,/$/compactall"));

        // Register the filter
        serverBuilder.addFilter("/*", new FusekiJwtAuthFilter());
    }

    private static final class FusekiJwtAuthFilter extends JwtAuthFilter {

        @Override
        public void init(FilterConfig filterConfig) {
            // Do nothing
            // We explicitly configure the filter at the server setup level so no need to use the default filter
            // behaviour of trying to automatically configure itself from init parameters
        }
    }

    private static final class FusekiConfigurationAdaptor extends IANodeConfigurationAdaptor {

        private final FusekiServer.Builder serverBuilder;

        public FusekiConfigurationAdaptor(FusekiServer.Builder serverBuilder) {
            this.serverBuilder = serverBuilder;
        }

        @Override
        public void setAttribute(String attribute, Object value) {
            this.serverBuilder.addServletAttribute(attribute, value);
        }

        @Override
        public Object getAttribute(String attribute) {
            return this.serverBuilder.getServletAttribute(attribute);
        }
    }
}
