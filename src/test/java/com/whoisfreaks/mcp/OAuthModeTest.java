package com.whoisfreaks.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.whoisfreaks.mcp.oauth.OAuthSettings;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OAuth mode end to end: the real Jetty server with its filters, against a local stand-in for the
 * authorization server's JWKS and billing's API key lookup. No tool here reaches api.whoisfreaks.com.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OAuthModeTest {

    private static final String RESOURCE = "https://mcp-oauth.test";
    private static final String SERVER_TOKEN = "server-token";
    private static final String API_KEY = "0123456789abcdef0123456789abcdef";
    private static final String INITIALIZE = "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
            + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}";
    private static final ObjectMapper JSON = new ObjectMapper();

    private record BillingAnswer(int status, String body) {
    }

    private final Map<String, BillingAnswer> billingAnswers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> billingCalls = new ConcurrentHashMap<>();
    private final HttpClient client = HttpClient.newHttpClient();

    private RSAKey signingKey;
    private HttpServer stub;
    private Server oauthServer;
    private Server apiKeyServer;
    private String issuer;
    private String oauthBase;

    @BeforeAll
    void start() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/oauth2/jwks", exchange ->
                respond(exchange, 200, new JWKSet(signingKey.toPublicJWK()).toString()));
        stub.createContext("/server_request/mcp/api-key", this::billing);
        stub.start();
        issuer = "http://127.0.0.1:" + stub.getAddress().getPort();

        OAuthSettings settings = new OAuthSettings(issuer, RESOURCE, URI.create(issuer + "/oauth2/jwks"), issuer, SERVER_TOKEN);
        oauthServer = WhoisFreaksHttpServer.start(0, settings);
        oauthBase = "http://127.0.0.1:" + port(oauthServer);
        apiKeyServer = WhoisFreaksHttpServer.start(0, null);
    }

    @AfterAll
    void stop() throws Exception {
        oauthServer.stop();
        apiKeyServer.stop();
        stub.stop(0);
    }

    @Test
    void metadataNamesTheResourceAndTheAuthorizationServer() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(oauthBase + "/.well-known/oauth-protected-resource")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        JsonNode metadata = JSON.readTree(response.body());
        assertEquals(RESOURCE, metadata.path("resource").asText());
        assertEquals(issuer, metadata.path("authorization_servers").path(0).asText());
        assertEquals("lookups", metadata.path("scopes_supported").path(0).asText());
    }

    @Test
    void requestWithoutTokenGetsTheChallenge() throws Exception {
        HttpResponse<String> response = post(null, "initialize", INITIALIZE);

        assertEquals(401, response.statusCode());
        String challenge = response.headers().firstValue("WWW-Authenticate").orElse("");
        assertTrue(challenge.startsWith("Bearer "), challenge);
        assertTrue(challenge.contains("resource_metadata=\"" + RESOURCE + "/.well-known/oauth-protected-resource\""), challenge);
        assertTrue(challenge.contains("scope=\"lookups\""), challenge);
        assertTrue(response.headers().firstValue("Access-Control-Expose-Headers").orElse("").contains("WWW-Authenticate"));
    }

    @Test
    void apiKeyHeadersDontGetIn() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(oauthBase + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("X-API-Key", API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(rpc("initialize", INITIALIZE)))
                .build();

        assertEquals(401, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void preflightNeedsNoToken() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(oauthBase + "/mcp"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        assertEquals(204, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims(c -> { }));
        jwt.sign(new RSASSASigner(otherKey));

        assertInvalidToken(post(jwt.serialize(), "initialize", INITIALIZE));
    }

    @Test
    void tokenForAnotherResourceIsRejected() throws Exception {
        assertInvalidToken(post(token(c -> c.audience("https://elsewhere.test")), "initialize", INITIALIZE));
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        assertInvalidToken(post(token(c -> c.issuer("https://elsewhere.test")), "initialize", INITIALIZE));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        assertInvalidToken(post(token(c -> c.expirationTime(Date.from(Instant.now().minusSeconds(120)))),
                "initialize", INITIALIZE));
    }

    @Test
    void tokenWithoutTheLookupsScopeIsForbidden() throws Exception {
        HttpResponse<String> response = post(token(c -> c.claim("scope", List.of("profile"))), "initialize", INITIALIZE);

        assertEquals(403, response.statusCode());
        assertTrue(response.headers().firstValue("WWW-Authenticate").orElse("").contains("error=\"insufficient_scope\""));
    }

    @Test
    void validTokenListsToolsAndBillingIsAskedOncePerMinute() throws Exception {
        String token = token(c -> { });

        HttpResponse<String> first = post(token, "tools/list", "{}");
        HttpResponse<String> second = post(token, "tools/list", "{}");

        assertEquals(200, first.statusCode(), first.body());
        assertTrue(first.body().contains("liveWhoisLookup"), first.body());
        assertEquals(200, second.statusCode());
        assertEquals(1, billingCalls.get(token).get());
    }

    @Test
    void tokenBillingNoLongerAcceptsGetsTheChallenge() throws Exception {
        String token = token(c -> { });
        billingAnswers.put(token, new BillingAnswer(401, "{\"error\":\"invalid_token\"}"));

        assertInvalidToken(post(token, "tools/list", "{}"));
    }

    @Test
    void inactiveAccountGetsAToolErrorNotAChallenge() throws Exception {
        String token = token(c -> { });
        billingAnswers.put(token, new BillingAnswer(403, "{\"error\":\"user not active\"}"));

        HttpResponse<String> list = post(token, "tools/list", "{}");
        HttpResponse<String> call = post(token, "tools/call",
                "{\"name\":\"liveWhoisLookup\",\"arguments\":{\"domainName\":\"example.com\"}}");

        assertEquals(200, list.statusCode());
        assertEquals(200, call.statusCode(), call.body());
        JsonNode result = JSON.readTree(call.body()).path("result");
        assertTrue(result.path("isError").asBoolean(), call.body());
        assertTrue(result.path("content").path(0).path("text").asText().contains("billing.whoisfreaks.com"), call.body());
    }

    @Test
    void billingRejectingThisServerIsAServerError() throws Exception {
        String token = token(c -> { });
        billingAnswers.put(token, new BillingAnswer(401, "{\"error\":\"unauthorized\"}"));

        HttpResponse<String> response = post(token, "tools/list", "{}");

        assertEquals(503, response.statusCode());
        assertFalse(response.headers().firstValue("WWW-Authenticate").isPresent());
    }

    @Test
    void apiKeyModeIsUnchanged() throws Exception {
        String base = "http://127.0.0.1:" + port(apiKeyServer);
        HttpRequest initialize = HttpRequest.newBuilder(URI.create(base + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(rpc("initialize", INITIALIZE)))
                .build();
        HttpRequest metadata = HttpRequest.newBuilder(URI.create(base + "/.well-known/oauth-protected-resource")).GET().build();

        HttpResponse<String> response = client.send(initialize, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertFalse(response.headers().firstValue("WWW-Authenticate").isPresent());
        assertEquals(404, client.send(metadata, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private void assertInvalidToken(HttpResponse<String> response) {
        assertEquals(401, response.statusCode(), response.body());
        String challenge = response.headers().firstValue("WWW-Authenticate").orElse("");
        assertTrue(challenge.contains("error=\"invalid_token\""), challenge);
        assertTrue(challenge.contains("resource_metadata="), challenge);
    }

    private HttpResponse<String> post(String token, String method, String params) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(oauthBase + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(rpc(method, params)));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String rpc(String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params + "}";
    }

    /** A token as the authorization server issues it, with {@code changes} applied; unique, so billing calls can be counted. */
    private String token(Consumer<JWTClaimsSet.Builder> changes) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claims(changes));
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private JWTClaimsSet claims(Consumer<JWTClaimsSet.Builder> changes) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(RESOURCE)
                .subject("42")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", List.of("lookups"));
        changes.accept(claims);
        return claims.build();
    }

    private void billing(HttpExchange exchange) throws IOException {
        if (!SERVER_TOKEN.equals(exchange.getRequestHeaders().getFirst("X-Mcp-Server-Token"))) {
            respond(exchange, 401, "{\"error\":\"unauthorized\"}");
            return;
        }
        String token = JSON.readTree(exchange.getRequestBody()).path("accessToken").asText();
        billingCalls.computeIfAbsent(token, t -> new AtomicInteger()).incrementAndGet();
        BillingAnswer answer = billingAnswers.getOrDefault(token,
                new BillingAnswer(200, "{\"apiKey\":\"" + API_KEY + "\",\"ipRestricted\":false}"));
        respond(exchange, answer.status(), answer.body());
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static int port(Server server) {
        return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }
}
