package com.whoisfreaks.mcp.tools;

import com.whoisfreaks.mcp.WhoisFreaksService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * MCP tools for WhoisFreaks SSL Certificate API.
 *
 * Tools:
 *   sslLookup — Retrieve SSL/TLS certificate data for any domain
 */
public class SslTools {

    private final WhoisFreaksService service;

    public SslTools(WhoisFreaksService service) {
        this.service = service;
    }

    public List<McpServerFeatures.SyncToolSpecification> specs() {
        return List.of(sslLookupSpec());
    }

    // =========================================================================
    // sslLookup
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification sslLookupSpec() {
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
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "sslLookup",
                        """
                        Retrieve real-time SSL/TLS certificate information for any domain using the
                        WhoisFreaks SSL Certificate API.
                        Returns detailed certificate data including:
                        - Certificate subject (Common Name, SAN, Organization)
                        - Issuer information (CA name, organization)
                        - Validity period (issued date, expiry date)
                        - Certificate serial number and fingerprints (SHA-1, SHA-256)
                        - Key algorithm and size (RSA, ECDSA)
                        - TLS version and cipher suite details
                        - Optionally: full certificate chain (end-user → intermediate → root CA)
                        - Optionally: raw OpenSSL output
                        Parameters:
                        - domainName (required): Domain to retrieve SSL certificate for. Example: github.com
                        - chain (optional): true to include full certificate chain. Default: false
                        - sslRaw (optional): true to include raw OpenSSL response. Default: false
                        - format (optional): json (default) or xml
                        Note: Returns HTTP 404 if no SSL certificate exists for the domain.
                        """,
                        schema),
                (exchange, args) -> {
                    String domain = str(args, "domainName");
                    if (domain == null) return result("Error: 'domainName' is required.");
                    Boolean chain  = args.get("chain") instanceof Boolean b ? b : null;
                    Boolean sslRaw = args.get("sslRaw") instanceof Boolean b ? b : null;
                    return result(service.sslLookup(domain, chain, sslRaw, str(args, "format")));
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
