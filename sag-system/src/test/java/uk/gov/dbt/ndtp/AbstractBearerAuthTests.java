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

import io.jsonwebtoken.Jwts;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.http.HttpOp;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetOps;
import org.apache.jena.sparql.exec.http.QueryExecHTTPBuilder;
import org.apache.jena.web.HttpSC;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static uk.gov.dbt.ndtp.TestSecureAgentGraphIntegration.launchServer;

public abstract class AbstractBearerAuthTests {
    protected static final String DIR = "target/databases";
    private static final String QUERY = "SELECT * {?s ?p ?o}";
    protected static FusekiServer server;
    protected static String URL;

    protected static void setupFuseki() {
        FileOps.ensureDir(DIR);
        FileOps.clearAll(DIR);
        FusekiLogging.setLogging();
        LibTestsSAG.disableInitialCompaction();
        server = launchServer("config-simple-auth.ttl");
        URL = "http://localhost:" + server.getHttpPort() + "/ds";
    }

    protected static void teardownFuseki() {
        if (null != server) {
            server.stop();
        }
    }

    @Test
    void givenValidToken_whenMakingARequest_thenSuccess() {
        // given
        String validToken = LibTestsSAG.tokenForUser("u1");
        // when
        RowSet rowSet = QueryExecHTTPBuilder.service(URL)
                                            .query(QUERY)
                                            .httpHeader(LibTestsSAG.tokenHeader(),
                                                        LibTestsSAG.tokenHeaderValue(validToken))
                                            .select();
        // then
        assertNotNull(rowSet);
        assertEquals(0, RowSetOps.count(rowSet));
    }


    private static void verifyRequestFailure(String token, String header, int expectedStatus) {
        // given
        Throwable actual = null;
        try {
            // when
            LibTestsSAG.withLevel(Fuseki.actionLog, "ERROR",
                                  () -> QueryExecHTTPBuilder.service(URL)
                                      .query(QUERY)
                                      .httpHeader(header, LibTestsSAG.tokenHeaderValue(token))
                                      .select());
        } catch (Throwable e) {
            actual = e;
        }
        // then
        assertNotNull(actual);
        assertInstanceOf(QueryExceptionHTTP.class, actual);
        QueryExceptionHTTP q = (QueryExceptionHTTP) actual;
        assertEquals(expectedStatus, q.getStatusCode());
    }

    @Test
    void givenValidTokenInWrongHeader_whenMakingARequest_thenUnauthorized() {
        verifyRequestFailure(LibTestsSAG.tokenForUser("u2"), "X-Custom", HttpSC.UNAUTHORIZED_401);
    }

    @Test
    void givenExpiredToken_whenMakingARequest_thenUnauthorized() {
        verifyRequestFailure(LibTestsSAG.tokenBuilder("u1")
                                        .expiration(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)))
                                        .compact(), LibTestsSAG.tokenHeader(), HttpSC.UNAUTHORIZED_401);
    }

    @Test
    void givenFutureDatedToken_whenMakingARequest_thenUnauthorized() {
        verifyRequestFailure(LibTestsSAG.tokenBuilder("u1")
                                        .notBefore(Date.from(Instant.now().plus(5, ChronoUnit.MINUTES)))
                                        .compact(), LibTestsSAG.tokenHeader(), HttpSC.UNAUTHORIZED_401);
    }

    @Test
    void givenTokenSignedWithWrongKey_whenMakingARequest_thenBadRequest() {
        verifyRequestFailure(
                LibTestsSAG.tokenBuilder("u1").signWith(Jwts.SIG.RS512.keyPair().build().getPrivate()).compact(),
                LibTestsSAG.tokenHeader(), HttpSC.BAD_REQUEST_400);
    }

    @Test
    void givenTokenWithIncorrectKeyId_whenMakingARequest_thenUnauthorized() {
        verifyRequestFailure(
                LibTestsSAG.tokenBuilder("u1").header().keyId("wrong").and().compact(),
                LibTestsSAG.tokenHeader(), HttpSC.UNAUTHORIZED_401);
    }

    @Test
    void givenNonJwtToken_whenMakingARequest_thenUnauthorized() {
        verifyRequestFailure("username:password", LibTestsSAG.tokenHeader(), HttpSC.UNAUTHORIZED_401);
    }

    @Test
    void givenValidTokenForNonExistentUser_whenMakingARequest_thenForbidden() {
        verifyRequestFailure(LibTestsSAG.tokenForUser("u3"), LibTestsSAG.tokenHeader(), 403);
    }

    @Test
    void givenNoToken_whenMakingARequest_thenUnauthorized() {
        // given
        Throwable actual = null;
        try {
            // when
            QueryExecHTTPBuilder.service(URL).query(QUERY).select();
        } catch (Throwable e) {
            actual = e;
        }
        // then
        assertNotNull(actual);
        assertInstanceOf(QueryExceptionHTTP.class, actual);
        QueryExceptionHTTP q = (QueryExceptionHTTP) actual;
        assertEquals(401, q.getStatusCode());
        assertEquals("Unauthorized", q.getMessage());
    }

    @Test
    void givenNoToken_whenMakingAPingRequest_thenSuccess() {
        // Given

        // When
        String result = HttpOp.httpGetString("http://localhost:" + server.getHttpPort() + "/$/ping");

        // Then
        assertNotNull(result);
    }
}
