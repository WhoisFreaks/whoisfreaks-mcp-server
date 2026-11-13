package com.whoisfreaks.mcp.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trades an access token for the API key of the account it was issued for, at billing's
 * POST /server_request/mcp/api-key. Tokens never carry the key, and billing answers only
 * while the connection exists, so revoking it there stops this server within a minute.
 *
 * Found keys are kept for up to a minute (never past the token's expiry), keyed by the token's
 * SHA-256, so a conversation's tool calls don't each ask billing again. Keys are never logged.
 */
public class BillingApiKeys {

    public sealed interface Resolution permits Found, InvalidToken, Refused, Failed {
    }

    /**
     * The account's API key: run the lookup with it. Billing also says whether the key only works
     * from listed IPs; that's left to the API, which knows whether this server's address is listed.
     */
    public record Found(String apiKey) implements Resolution {
    }

    /** Billing no longer accepts the token (revoked, or replaced by a refresh): the app has to sign in again. */
    public record InvalidToken() implements Resolution {
    }

    /** The account can't run lookups; signing in again won't change that, so tools explain it instead. */
    public record Refused(String message) implements Resolution {
    }

    /** Billing couldn't answer (unreachable, or rejected this server's token). */
    public record Failed(String message) implements Resolution {
    }

    static final String ACCOUNT_INACTIVE = "Your WhoisFreaks account can't run lookups right now: it's inactive, deleted, "
            + "or still needs a new password. Sign in at https://billing.whoisfreaks.com to check it, then try again.";
    static final String NO_API_KEY = "Your WhoisFreaks account has no active API key. Check it at "
            + "https://billing.whoisfreaks.com, then try again.";
    static final String UNAVAILABLE = "WhoisFreaks can't check your account right now. Try again in a few minutes.";

    private static final String LOOKUP_PATH = "/server_request/mcp/api-key";
    private static final String SERVER_TOKEN_HEADER = "X-Mcp-Server-Token";
    private static final Duration CACHE_TIME = Duration.ofSeconds(60);
    private static final int MAX_CACHED = 10_000;

    private static final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper json = new ObjectMapper();

    private record Cached(String apiKey, Instant until) {
    }

    private final URI lookupUri;
    private final String serverToken;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public BillingApiKeys(OAuthSettings settings) {
        this(settings, Clock.systemUTC());
    }

    BillingApiKeys(OAuthSettings settings, Clock clock) {
        this.lookupUri = URI.create(settings.billingUrl() + LOOKUP_PATH);
        this.serverToken = settings.billingServerToken();
        this.clock = clock;
    }

    public Resolution resolve(String accessToken, Instant tokenExpiresAt) {
        Instant now = this.clock.instant();
        String id = sha256(accessToken);
        Cached cached = this.cache.get(id);
        if (cached != null && now.isBefore(cached.until())) {
            return new Found(cached.apiKey());
        }

        Resolution resolution = lookUp(accessToken);
        if (resolution instanceof Found found) {
            Instant until = now.plus(CACHE_TIME);
            if (tokenExpiresAt.isBefore(until)) {
                until = tokenExpiresAt;
            }
            if (this.cache.size() >= MAX_CACHED) {
                this.cache.values().removeIf(entry -> !now.isBefore(entry.until()));
            }
            if (now.isBefore(until) && this.cache.size() < MAX_CACHED) {
                this.cache.put(id, new Cached(found.apiKey(), until));
            }
        } else {
            this.cache.remove(id);
        }
        return resolution;
    }

    private Resolution lookUp(String accessToken) {
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(this.lookupUri)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header(SERVER_TOKEN_HEADER, this.serverToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json.createObjectNode().put("accessToken", accessToken).toString()))
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[whoisfreaks-mcp] OAuth: billing's API key lookup failed: " + e.getMessage());
            return new Failed(UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Failed(UNAVAILABLE);
        }

        int status = response.statusCode();
        JsonNode body = parse(response.body());
        String error = body.path("error").asText("");
        String apiKey = body.path("apiKey").asText("");
        if (status == 200 && !apiKey.isBlank()) {
            return new Found(apiKey);
        }
        if (status == 401 && "invalid_token".equals(error)) {
            return new InvalidToken();
        }
        if (status == 403) {
            return new Refused("user not active".equals(error) ? ACCOUNT_INACTIVE : NO_API_KEY);
        }
        if (status == 401) {
            System.err.println("[whoisfreaks-mcp] OAuth: billing rejected BILLING_SERVER_TOKEN; check it matches billing's oauth.mcp-server-token");
        } else {
            System.err.println("[whoisfreaks-mcp] OAuth: billing's API key lookup answered HTTP " + status);
        }
        return new Failed(UNAVAILABLE);
    }

    private static JsonNode parse(String body) {
        try {
            JsonNode node = json.readTree(body == null ? "" : body);
            return node == null ? json.createObjectNode() : node;
        } catch (IOException e) {
            return json.createObjectNode();
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
