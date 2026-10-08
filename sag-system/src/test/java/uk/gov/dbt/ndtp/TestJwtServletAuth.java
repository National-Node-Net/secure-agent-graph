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

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import uk.gov.dbt.ndtp.core.FMod_InitialCompaction;
import uk.gov.dbt.ndtp.core.FMod_JwtServletAuth;
import uk.gov.dbt.ndtp.core.SecureAgentGraph;
import uk.gov.dbt.ndtp.jena.abac.lib.Attributes;
import uk.gov.dbt.ndtp.servlet.auth.jwt.PathExclusion;
import uk.gov.dbt.ndtp.servlet.auth.jwt.verification.JwtVerifier;
import uk.gov.dbt.ndtp.servlet.auth.jwt.verifier.aws.AwsConstants;
import uk.gov.dbt.ndtp.secure.agent.configuration.Configurator;
import uk.gov.dbt.ndtp.secure.agent.configuration.sources.PropertiesSource;
import uk.gov.dbt.ndtp.secure.agent.configuration.auth.AuthConstants;
import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.cmds.FusekiMain;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.http.HttpEnv;
import org.apache.jena.http.HttpLib;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Properties;

import static graphql.Assert.assertNotEmpty;
import static uk.gov.dbt.ndtp.servlet.auth.jwt.JwtServletConstants.ATTRIBUTE_JWT_VERIFIER;
import static uk.gov.dbt.ndtp.servlet.auth.jwt.JwtServletConstants.ATTRIBUTE_PATH_EXCLUSIONS;
import static org.apache.jena.graph.Graph.emptyGraph;
import static org.apache.jena.http.HttpLib.*;
import static org.apache.jena.riot.web.HttpNames.METHOD_GET;
import static org.apache.jena.riot.web.HttpNames.METHOD_POST;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class TestJwtServletAuth {

    @AfterEach
    void clear() {
        Configurator.reset();
    }

    @Test
    void test_name() {
        // given
        FMod_JwtServletAuth jwtServletAuth = new FMod_JwtServletAuth();
        // when
        String name = jwtServletAuth.name();
        // then
        assertNotNull(name);
    }

    @Test
    void test_prepare_disabledAuth() {
        // given
        disableAuth();
        FMod_JwtServletAuth jwtServletAuth = new FMod_JwtServletAuth();

        FusekiServer.Builder mockBuilder = mock(FusekiServer.Builder.class);
        Model mockConfig = mock(Model.class);

        // when
        jwtServletAuth.prepare(mock(), null, mockConfig);

        // then
        verifyNoInteractions(mockBuilder, mockConfig);
    }

    @Test
    void test_prepare_noVerifier() {
        FMod_JwtServletAuth jwtServletAuth = new FMod_JwtServletAuth();

        FusekiServer.Builder mockBuilder = mock(FusekiServer.Builder.class);
        Model mockConfig = mock(Model.class);

        // when
        Throwable actualException = null;
        try {
            jwtServletAuth.prepare(mock(), null, mockConfig);
        } catch (Exception e) {
            actualException = e;
        }

        // then
        verifyNoInteractions(mockBuilder, mockConfig);
        assertInstanceOf(RuntimeException.class, actualException);
    }

    @Test
    @SuppressWarnings("unchecked")
    void test_prepare_happyPath() {
        // given
        List<PathExclusion> expectedList = List.of(
                new PathExclusion("/$/ping"),
                new PathExclusion("/$/metrics"),
                new PathExclusion("/\\$/stats/*"),
                new PathExclusion("/$/compactall")
        );
        FMod_JwtServletAuth jwtServletAuth = new FMod_JwtServletAuth();
        FusekiServer.Builder builder = SecureAgentGraph.serverBuilder().addServletAttribute(ATTRIBUTE_JWT_VERIFIER, new TestJwtVerifier());
        // when
        jwtServletAuth.prepare(builder, null, null);
        // then
        Object actualExclusions = builder.getServletAttribute(ATTRIBUTE_PATH_EXCLUSIONS);
        assertNotNull(actualExclusions);
        List<PathExclusion> actualList = (List<PathExclusion>) actualExclusions;
        assertNotEmpty(actualList);
        assertExclusionListsEqual(expectedList, actualList);
    }

    @Test
    void test_exclusionLogic() {
        FMod_JwtServletAuth jwtServletAuth = new FMod_JwtServletAuth();
        FMod_InitialCompaction initialCompaction = new FMod_InitialCompaction ();
        Attributes.buildStore(emptyGraph);
        FusekiServer server = FusekiMain.builder("--port=0", "--empty")
                .fusekiModules(FusekiModules.create(List.of(jwtServletAuth, initialCompaction)))
                .addServletAttribute(ATTRIBUTE_JWT_VERIFIER, new TestJwtVerifier())
                .enablePing(true)
                .enableMetrics(true)
                .enableStats(true)
                .build().start();

        // Correct path
        HttpResponse<InputStream> pingResponse = makePOSTCallWithPath(server, "$/ping");
        assertEquals(200, pingResponse.statusCode());

        // Correct path
        HttpResponse<InputStream> metricsResponse = makePOSTCallWithPath(server, "$/metrics");
        assertEquals(200, metricsResponse.statusCode());

        // Correct path
        HttpResponse<InputStream> compactResponse = makePOSTCallWithPath(server, "$/compactall");
        assertEquals(200, compactResponse.statusCode());

        // Fails - due to missing path but NOT due to Auth.
        HttpResponse<InputStream> statsResponse = makePOSTCallWithPath(server, "$/stats/unrecognised");
        assertEquals(404, statsResponse.statusCode());
        HttpException httpException = assertThrows(HttpException.class, () -> handleResponseNoBody(statsResponse));
        assertTrue(httpException.getResponse().startsWith("/unrecognised"));

        // Fails - due to Auth not path which is missing
        HttpResponse<InputStream> otherResponse = makePOSTCallWithPath(server, "$/unrecognisedPath");
        assertEquals(401, otherResponse.statusCode());
        httpException = assertThrows(HttpException.class, () -> handleResponseNoBody(otherResponse));
        assertTrue(httpException.getResponse().contains("Unauthorized"));

        server.stop();
    }

    private void disableAuth() {
        Properties properties = new Properties();
        properties.put(AuthConstants.ENV_JWKS_URL, AuthConstants.AUTH_DISABLED);
        Configurator.addSource(new PropertiesSource(properties));
    }

    private static class TestJwtVerifier implements JwtVerifier {

        @Override
        public Jws<Claims> verify(String s) {
            return null;
        }
    }

    private static void assertExclusionListsEqual(List<PathExclusion> expected, List<PathExclusion> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).getPattern(), actual.get(i).getPattern());
        }
    }

    public static HttpResponse<InputStream> makePOSTCallWithPath(FusekiServer server, String path) {
        HttpRequest.Builder builder =
                HttpLib.requestBuilderFor(server.serverURL())
                        .uri(toRequestURI(server.serverURL()+ path))
                        .method(METHOD_POST, HttpRequest.BodyPublishers.noBody());
        return execute(HttpEnv.getDftHttpClient(), builder.build());
    }
    public static HttpResponse<InputStream> makeAuthPOSTCallWithPath(FusekiServer server, String path, String user) {
        return makeAuthCallWithPathForMethod(server, path, user, METHOD_POST);
    }

    public static HttpResponse<InputStream> makeAuthGETCallWithPath(FusekiServer server, String path, String user) {
        return makeAuthCallWithPathForMethod(server, path, user, METHOD_GET);
    }

    public static HttpResponse<InputStream> makeAuthCallWithPathForMethod(FusekiServer server, String path, String user, String method) {
        HttpRequest.Builder builder =
                HttpLib.requestBuilderFor(server.serverURL())
                        .uri(toRequestURI(server.serverURL() + path))
                        .headers(AwsConstants.HEADER_DATA, LibTestsSAG.tokenForUser(user))
                        .method(method, HttpRequest.BodyPublishers.noBody());
        return execute(HttpEnv.getDftHttpClient(), builder.build());
    }
}
