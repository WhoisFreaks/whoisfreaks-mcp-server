package com.whoisfreaks.mcp.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * OAuth mode's gate in front of the MCP endpoint. Every request needs a bearer access token
 * from the authorization server; without a usable one the answer is 401 with a challenge
 * pointing to this server's protected resource metadata, which is how apps such as ChatGPT
 * learn to sign the user in (403 for a token without the lookups scope).
 *
 * A valid token is traded for the account's API key ({@link BillingApiKeys}), passed on as a
 * request attribute. An account that can't run lookups still gets through, with the reason as
 * an attribute instead, so tools explain it rather than sending the app back to sign in.
 *
 * Runs after WhoisFreaksHttpServer's McpFilter, which answers CORS preflights and malformed
 * requests, so these answers also carry the CORS headers browser-based clients need.
 */
public class OAuthFilter implements Filter {

    /** Request attributes the MCP transport's context extractor reads. */
    public static final String API_KEY_ATTRIBUTE = "whoisfreaks.apiKey";
    public static final String KEY_PROBLEM_ATTRIBUTE = "whoisfreaks.keyProblem";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AccessTokenVerifier verifier;
    private final BillingApiKeys apiKeys;
    private final String metadataUrl;

    public OAuthFilter(AccessTokenVerifier verifier, BillingApiKeys apiKeys, OAuthSettings settings) {
        this.verifier = verifier;
        this.apiKeys = apiKeys;
        this.metadataUrl = settings.metadataUrl();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String token = bearerToken(request);
        if (token == null) {
            challenge(response, HttpServletResponse.SC_UNAUTHORIZED, null,
                    "Sign in through the WhoisFreaks plugin, or send an OAuth access token as 'Authorization: Bearer <token>'.");
            return;
        }

        AccessTokenVerifier.VerifiedToken verified;
        try {
            verified = this.verifier.verify(token);
        } catch (AccessTokenVerifier.InvalidTokenException e) {
            if (e.insufficientScope()) {
                challenge(response, HttpServletResponse.SC_FORBIDDEN, "insufficient_scope", e.getMessage());
            } else {
                challenge(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token", e.getMessage());
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
            challenge(response, HttpServletResponse.SC_UNAUTHORIZED, "invalid_token", "The access token was revoked.");
            return;
        } else {
            serverError(response, ((BillingApiKeys.Failed) resolution).message());
            return;
        }
        chain.doFilter(request, response);
    }

    private static String bearerToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = auth.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    /** 401 or 403 with a Bearer challenge (RFC 6750, RFC 9728). */
    private void challenge(HttpServletResponse response, int status, String error, String description) throws IOException {
        StringBuilder header = new StringBuilder("Bearer ");
        if (error != null) {
            header.append("error=\"").append(error).append("\", ");
        }
        header.append("resource_metadata=\"").append(this.metadataUrl).append("\", scope=\"")
                .append(OAuthSettings.LOOKUPS_SCOPE).append('"');
        response.setStatus(status);
        response.setHeader("WWW-Authenticate", header.toString());
        ObjectNode body = JSON.createObjectNode();
        body.put("error", error != null ? error : "unauthorized");
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
