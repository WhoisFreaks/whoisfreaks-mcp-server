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
 * One server taking API keys and OAuth access tokens side by side: the real Jetty server with its
 * filters, against a local stand-in for the authorization server's JWKS and billing's API key lookup.
 * No request here reaches api.whoisfreaks.com: only tools/list runs with a key.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OAuthSignInTest {

    private static final String RESOURCE = "https://mcp.test";
    private static final String SERVER_TOKEN = "server-token";
    private static final String API_KEY = "0123456789abcdef0123456789abcdef";
    private static final String INITIALIZE = "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
            + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}";
    private static final String CALL_LOOKUP = "{\"name\":\"liveWhoisLookup\",\"arguments\":{\"domainName\":\"example.com\"}}";
    private static final ObjectMapper JSON = new ObjectMapper();

    private record BillingAnswer(int status, String body) {
    }

    private final Map<String, BillingAnswer> billingAnswers = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> billingCalls = new ConcurrentHashMap<>();
    private final HttpClient client = HttpClient.newHttpClient();

    private RSAKey signingKey;
    private HttpServer stub;
    private Server server;
    private Server apiKeyOnlyServer;
    private String issuer;
    private String base;

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
        server = WhoisFreaksHttpServer.start(0, settings);
        base = "http://127.0.0.1:" + port(server);
        apiKeyOnlyServer = WhoisFreaksHttpServer.start(0, null);
    }

    @AfterAll
    void stop() throws Exception {
        server.stop();
        apiKeyOnlyServer.stop();
        stub.stop(0);
    }

    // ---- Discovery

    @Test
    void metadataNamesTheResourceAndTheAuthorizationServer() throws Exception {
        JsonNode root = JSON.readTree(get("/.well-known/oauth-protected-resource").body());
        JsonNode mcp = JSON.readTree(get("/.well-known/oauth-protected-resource/mcp").body());

        assertEquals(RESOURCE, root.path("resource").asText());
        assertEquals(RESOURCE + "/mcp", mcp.path("resource").asText());
        assertEquals(issuer, root.path("authorization_servers").path(0).asText());
        assertEquals("lookups", root.path("scopes_supported").path(0).asText());
    }

    // ---- API keys and no credentials: as without OAuth

    @Test
    void requestWithoutCredentialsIsServedAsBefore() throws Exception {
        HttpResponse<String> initialize = post(Map.of(), "initialize", INITIALIZE);
        HttpResponse<String> call = post(Map.of(), "tools/call", CALL_LOOKUP);

        assertEquals(200, initialize.statusCode(), initialize.body());
        assertFalse(initialize.headers().firstValue("WWW-Authenticate").isPresent());
        JsonNode result = JSON.readTree(call.body()).path("result");
        assertTrue(result.path("isError").asBoolean(), call.body());
        String text = result.path("content").path(0).path("text").asText();
        assertTrue(text.contains("X-API-Key") && text.contains("WhoisFreaks plugin"), text);
    }

    @Test
    void apiKeysWorkAlongsideOAuth() throws Exception {
        HttpResponse<String> header = post(Map.of("X-API-Key", API_KEY), "tools/list", "{}");
        HttpResponse<String> bearer = post(Map.of("Authorization", "Bearer " + API_KEY), "tools/list", "{}");

        assertEquals(200, header.statusCode(), header.body());
        assertEquals(200, bearer.statusCode(), bearer.body());
        assertTrue(bearer.body().contains("liveWhoisLookup"));
        assertTrue(billingCalls.isEmpty(), "billing is only asked about access tokens");
    }

    @Test
    void preflightNeedsNoCredentials() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/mcp"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(204, response.statusCode());
        assertTrue(response.headers().firstValue("Access-Control-Expose-Headers").orElse("").contains("WWW-Authenticate"));
    }

    // ---- Access tokens

    @Test
    void validTokenListsToolsAndBillingIsAskedOncePerMinute() throws Exception {
        String token = token(c -> { });

        HttpResponse<String> first = postWithToken(token, "tools/list", "{}");
        HttpResponse<String> second = postWithToken(token, "tools/list", "{}");

        assertEquals(200, first.statusCode(), first.body());
        assertTrue(first.body().contains("liveWhoisLookup"), first.body());
        assertEquals(200, second.statusCode());
        assertEquals(1, billingCalls.get(token).get());
    }

    @Test
    void tokenForTheMcpPathIsAccepted() throws Exception {
        HttpResponse<String> response = postWithToken(token(c -> c.audience(RESOURCE + "/mcp")), "tools/list", "{}");

        assertEquals(200, response.statusCode(), response.body());
    }

    @Test
    void tokenSignedWithAnotherKeyGetsTheChallenge() throws Exception {
        RSAKey otherKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims(c -> { }));
        jwt.sign(new RSASSASigner(otherKey));

        assertInvalidToken(postWithToken(jwt.serialize(), "initialize", INITIALIZE));
    }

    @Test
    void tokenForAnotherResourceGetsTheChallenge() throws Exception {
        assertInvalidToken(postWithToken(token(c -> c.audience("https://elsewhere.test")), "initialize", INITIALIZE));
    }

    @Test
    void tokenFromAnotherIssuerGetsTheChallenge() throws Exception {
        assertInvalidToken(postWithToken(token(c -> c.issuer("https://elsewhere.test")), "initialize", INITIALIZE));
    }

    @Test
    void expiredTokenGetsTheChallenge() throws Exception {
        assertInvalidToken(postWithToken(token(c -> c.expirationTime(Date.from(Instant.now().minusSeconds(120)))),
                "initialize", INITIALIZE));
    }

    @Test
    void tokenWithoutTheLookupsScopeIsForbidden() throws Exception {
        HttpResponse<String> response = postWithToken(token(c -> c.claim("scope", List.of("profile"))), "initialize", INITIALIZE);

        assertEquals(403, response.statusCode());
        assertTrue(response.headers().firstValue("WWW-Authenticate").orElse("").contains("error=\"insufficient_scope\""));
    }

    @Test
    void tokenBillingNoLongerAcceptsGetsTheChallenge() throws Exception {
        String token = token(c -> { });
        billingAnswers.put(token, new BillingAnswer(401, "{\"error\":\"invalid_token\"}"));

        assertInvalidToken(postWithToken(token, "tools/list", "{}"));
    }

    @Test
    void inactiveAccountGetsAToolErrorNotAChallenge() throws Exception {
        String token = token(c -> { });
        billingAnswers.put(token, new BillingAnswer(403, "{\"error\":\"user not active\"}"));

        HttpResponse<String> list = postWithToken(token, "tools/list", "{}");
        HttpResponse<String> call = postWithToken(token, "tools/call", CALL_LOOKUP);

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

        HttpResponse<String> response = postWithToken(token, "tools/list", "{}");

        assertEquals(503, response.statusCode());
        assertFalse(response.headers().firstValue("WWW-Authenticate").isPresent());
    }

    // ---- Without OAuth settings

    @Test
    void withoutOAuthSettingsATokenIsJustABadKey() throws Exception {
        String apiKeyOnly = "http://127.0.0.1:" + port(apiKeyOnlyServer);
        HttpRequest initialize = HttpRequest.newBuilder(URI.create(apiKeyOnly + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("Authorization", "Bearer " + token(c -> { }))
                .POST(HttpRequest.BodyPublishers.ofString(rpc("initialize", INITIALIZE)))
                .build();
        HttpRequest metadata = HttpRequest.newBuilder(URI.create(apiKeyOnly + "/.well-known/oauth-protected-resource"))
                .GET().build();

        HttpResponse<String> response = client.send(initialize, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertFalse(response.headers().firstValue("WWW-Authenticate").isPresent());
        assertEquals(404, client.send(metadata, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    private void assertInvalidToken(HttpResponse<String> response) {
        assertEquals(401, response.statusCode(), response.body());
        String challenge = response.headers().firstValue("WWW-Authenticate").orElse("");
        assertTrue(challenge.startsWith("Bearer error=\"invalid_token\""), challenge);
        assertTrue(challenge.contains("resource_metadata=\"" + RESOURCE + "/.well-known/oauth-protected-resource\""), challenge);
        assertTrue(challenge.contains("scope=\"lookups\""), challenge);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), path);
        return response;
    }

    private HttpResponse<String> postWithToken(String token, String method, String params) throws Exception {
        return post(Map.of("Authorization", "Bearer " + token), method, params);
    }

    private HttpResponse<String> post(Map<String, String> headers, String method, String params) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(rpc(method, params)));
        headers.forEach(request::header);
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
