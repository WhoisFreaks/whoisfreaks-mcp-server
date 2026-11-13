package com.whoisfreaks.mcp.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * This server's OAuth protected resource metadata (RFC 9728), at {@link OAuthSettings#metadataPath()}.
 * Apps find it through the 401 challenge, and learn from it which authorization server signs users in.
 */
public class ProtectedResourceMetadataServlet extends HttpServlet {

    private static final String DOCUMENTATION = "https://github.com/WhoisFreaks/whoisfreaks-mcp-server";

    private final String body;

    public ProtectedResourceMetadataServlet(OAuthSettings settings) {
        ObjectMapper json = new ObjectMapper();
        ObjectNode metadata = json.createObjectNode();
        metadata.put("resource", settings.resource());
        metadata.putArray("authorization_servers").add(settings.issuer());
        metadata.putArray("scopes_supported").add(OAuthSettings.LOOKUPS_SCOPE);
        metadata.putArray("bearer_methods_supported").add("header");
        metadata.put("resource_name", "WhoisFreaks");
        metadata.put("resource_documentation", DOCUMENTATION);
        this.body = metadata.toString();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Browser-based MCP clients read this cross-origin.
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(this.body);
    }
}
