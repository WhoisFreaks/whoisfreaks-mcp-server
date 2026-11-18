package com.whoisfreaks.mcp.oauth;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles OAuth access tokens on the hosted HTTP server. A Bearer value with three non-empty
 * dot-separated parts is treated as a JWT access token; key-style Bearer values, X-API-Key headers
 * and ?apiKey query parameters continue through the API-key path, whether or not OAuth is enabled.
 * A Bearer value with dots that is not a well-formed JWT gets 401 invalid_token. Requests with no
 * credentials receive a 401 protected-resource challenge so clients can discover the sign-in flow.
 *
 * An unusable token gets 401 with a challenge pointing to this server's protected resource
 * metadata, so apps such as Claude or ChatGPT refresh it or sign the user in again (403 for a token
 * without the lookups scope). A valid token is traded for the account's API key
 * ({@link BillingApiKeys}), passed on as a request attribute. An account that can't run lookups
 * still gets through, with the reason as an attribute instead, so tools explain it rather than
 * sending the app back to sign in.
 *
 * Runs after WhoisFreaksHttpServer's McpFilter, which answers CORS preflights and malformed
 * requests, so these answers also carry the CORS headers browser-based clients need.
 */
public class OAuthFilter implements Filter {

    /** Request attributes the MCP transport's context extractor reads. */
    public static final String API_KEY_ATTRIBUTE = "whoisfreaks.apiKey";
    public static final String KEY_PROBLEM_ATTRIBUTE = "whoisfreaks.keyProblem";
    public static final String ORIGINAL_REQUEST_URI_ATTRIBUTE = "whoisfreaks.originalRequestUri";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AccessTokenVerifier verifier;
    private final BillingApiKeys apiKeys;
    private final OAuthSettings settings;

    public OAuthFilter(AccessTokenVerifier verifier, BillingApiKeys apiKeys, OAuthSettings settings) {
        this.verifier = verifier;
        this.apiKeys = apiKeys;
        this.settings = settings;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String token = accessToken(request);
        if (token == null) {
            if (hasApiKey(request)) {
                chain.doFilter(request, response);
            } else {
                String auth = request.getHeader("Authorization");
                if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)
                        && !auth.substring(7).trim().isEmpty()) {
                    // The Bearer value was meant as an access token but isn't a well-formed JWT.
                    challenge(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token",
                            "The access token is malformed.", request);
                } else {
                    challenge(response, HttpServletResponse.SC_UNAUTHORIZED, null,
                            "A WhoisFreaks API key or OAuth access token is required.", request);
                }
            }
            return;
        }

        AccessTokenVerifier.VerifiedToken verified;
        try {
            verified = this.verifier.verify(token);
        } catch (AccessTokenVerifier.InvalidTokenException e) {
            if (e.insufficientScope()) {
                challenge(response, HttpServletResponse.SC_FORBIDDEN, "insufficient_scope", e.getMessage(), request);
            } else {
                challenge(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token", e.getMessage(), request);
            }
            return;
        } catch (AccessTokenVerifier.KeysUnavailableException e) {
            System.err.println("[whoisfreaks-mcp] OAuth: couldn't fetch the authorization server's keys: " + e.getCause().getMessage());
            serverError(response, BillingApiKeys.UNAVAILABLE);
            return;
        }

        BillingApiKeys.Resolution resolution = this.apiKeys.resolve(token, verified.expiresAt());
        if (resolution instanceof BillingApiKeys.Found found) {
            request.setAttribute(API_KEY_ATTRIBUTE, found.apiKey());
        } else if (resolution instanceof BillingApiKeys.Refused refused) {
            request.setAttribute(KEY_PROBLEM_ATTRIBUTE, refused.message());
        } else if (resolution instanceof BillingApiKeys.InvalidToken) {
            challenge(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token", "The access token was revoked.", request);
            return;
        } else if (resolution instanceof BillingApiKeys.Failed failed) {
            serverError(response, failed.message());
            return;
        } else {
            serverError(response, BillingApiKeys.UNAVAILABLE);
            return;
        }
        chain.doFilter(request, response);
    }

    /** A JWT is three non-empty dot-separated parts; anything else is not an access token candidate. */
    private static String accessToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = auth.substring(7).trim();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return null;
        }
        for (String part : parts) {
            if (part.isEmpty()) {
                return null;
            }
        }
        return token;
    }

    /** All three API-key forms keep working alongside OAuth, so legacy clients need no change. */
    private static boolean hasApiKey(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)
                && !auth.substring(7).trim().isEmpty() && !auth.substring(7).contains(".")) {
            return true;
        }
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return true;
        }
        String queryKey = request.getParameter("apiKey");
        return queryKey != null && !queryKey.isBlank();
    }

    /** 401 or 403 with a Bearer challenge (RFC 6750, RFC 9728). */
    private void challenge(HttpServletResponse response, int status, String error, String description,
                          HttpServletRequest request) throws IOException {
        response.setStatus(status);
        String requestUri = request.getAttribute(ORIGINAL_REQUEST_URI_ATTRIBUTE) instanceof String original
            ? original : request.getRequestURI();
        String resource = this.settings.resource();
        if ("/mcp".equals(requestUri)) {
            resource += "/mcp";
        }
        StringBuilder challenge = new StringBuilder("Bearer ");
        if (error != null) {
            challenge.append("error=\"").append(error).append("\", ");
        }
        challenge.append("resource_metadata=\"").append(this.settings.metadataUrl(resource))
                .append("\", scope=\"").append(OAuthSettings.LOOKUPS_SCOPE).append('"');
        response.setHeader("WWW-Authenticate", challenge.toString());
        ObjectNode body = JSON.createObjectNode();
        body.put("error", error == null ? "unauthorized" : error);
        body.put("error_description", description);
        write(response, body);
    }

    private static void serverError(HttpServletResponse response, String message) throws IOException {
        ObjectNode body = JSON.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.putNull("id");
        body.putObject("error").put("code", -32603).put("message", message);
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        write(response, body);
    }

    private static void write(HttpServletResponse response, ObjectNode body) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.writeValueAsString(body));
    }
}
