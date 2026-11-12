package com.whoisfreaks.mcp.tools;

import com.whoisfreaks.mcp.WhoisFreaksService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP tools for WhoisFreaks SSL Certificate API.
 *
 * Tools:
 *   sslLookup — Retrieve SSL/TLS certificate data for any domain
 */
public class SslTools {

    private final Function<McpTransportContext, WhoisFreaksService> services;

    public SslTools(Function<McpTransportContext, WhoisFreaksService> services) {
        this.services = services;
    }

    public List<McpStatelessServerFeatures.SyncToolSpecification> specs() {
        return List.of(sslLookupSpec());
    }

    // =========================================================================
    // sslLookup
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification sslLookupSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "domainName": {
                      "type": "string",
                      "description": "Domain name to retrieve SSL certificate for. Accepts: example.com | https://example.com | http://example.com"
                    },
                    "chain": {
                      "type": "boolean",
                      "description": "If true, return the full certificate chain (end-user certificate through intermediate CA to root CA). Default: false"
                    },
                    "sslRaw": {
                      "type": "boolean",
                      "description": "If true, include the raw OpenSSL response in the output. Default: false"
                    },
                    "format": {
                      "type": "string",
                      "enum": ["json", "xml"],
                      "description": "Response format. Default: json"
                    }
                  },
                  "required": ["domainName"]
                }
                """;
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "sslLookup",
                        """
                        Get the SSL/TLS certificate a domain currently serves, fetched live.
                        Use this to check expiry dates, the issuer, covered hostnames (SANs) or key type, or to debug HTTPS errors.
                        Set chain=true to include intermediate and root certificates when diagnosing trust-chain problems, and
                        sslRaw=true only when you need the raw OpenSSL output, which is long. For DNS records use dnsLookup; for
                        domain ownership use liveWhoisLookup.
                        Returns the subject (common name, SANs, organisation), issuer, validity dates, serial number, SHA-1 and
                        SHA-256 fingerprints, key algorithm and size, and TLS version and cipher. A domain with no certificate
                        returns a "no data found" (HTTP 404) message.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String domain = str(args, "domainName");
                    if (domain == null) return result("Error: 'domainName' is required.");
                    Boolean chain  = args.get("chain") instanceof Boolean b ? b : null;
                    Boolean sslRaw = args.get("sslRaw") instanceof Boolean b ? b : null;
                    return result(services.apply(context).sslLookup(domain, chain, sslRaw, str(args, "format")));
                }
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private McpSchema.CallToolResult result(String text) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(text)), false);
    }

    private String str(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) return null;
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
