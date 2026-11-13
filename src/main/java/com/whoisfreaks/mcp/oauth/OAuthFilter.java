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
 * Handles the requests that carry an OAuth access token: a bearer value that is a JWT (three
 * dot-separated parts), which an API key never is. Every other request (an API key in any of
 * its forms, or no credentials) passes through untouched to the API-key handling, so clients
 * that send keys see no difference, and none of them ever gets a 401 from here.
 *
 * An unusable token gets 401 with a challenge pointing to this server's protected resource
 * metadata, so apps such as ChatGPT refresh it or sign the user in again (403 for a token
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

        String token = accessToken(request);
        if (token == null) {
            chain.doFilter(request, response);
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

    /** The bearer value if it's a JWT, else null: an API key is 32 characters without dots. */
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

    /** 401 or 403 with a Bearer challenge (RFC 6750, RFC 9728). */
    private void challenge(HttpServletResponse response, int status, String error, String description) throws IOException {
        response.setStatus(status);
        response.setHeader("WWW-Authenticate", "Bearer error=\"" + error + "\", resource_metadata=\""
                + this.metadataUrl + "\", scope=\"" + OAuthSettings.LOOKUPS_SCOPE + '"');
        ObjectNode body = JSON.createObjectNode();
        body.put("error", error);
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
