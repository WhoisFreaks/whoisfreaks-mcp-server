package com.whoisfreaks.mcp.tools;

import com.whoisfreaks.mcp.WhoisFreaksService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * MCP tools for WhoisFreaks IP WHOIS and ASN WHOIS APIs.
 *
 * Tools:
 *   ipWhoisLookup  — Full WHOIS registration data for any IPv4 or IPv6 address
 *   asnWhoisLookup — Full WHOIS data for any Autonomous System Number
 */
public class IpWhoisTools {

    private final WhoisFreaksService service;

    public IpWhoisTools(WhoisFreaksService service) {
        this.service = service;
    }

    public List<McpServerFeatures.SyncToolSpecification> specs() {
        return List.of(
                ipWhoisSpec(),
                asnWhoisSpec()
        );
    }

    // =========================================================================
    // ipWhoisLookup
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification ipWhoisSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "ip": {
                      "type": "string",
                      "description": "IPv4 or IPv6 address to look up. Examples: 8.8.8.8 | 2001:4860:4860::8888"
                    },
                    "format": {
                      "type": "string",
                      "enum": ["json", "xml"],
                      "description": "Response format. Default: json"
                    }
                  },
                  "required": ["ip"]
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "ipWhoisLookup",
                        """
                        Retrieve complete WHOIS registration data for any IPv4 or IPv6 address using the
                        WhoisFreaks IP WHOIS API.
                        Returns detailed network and registration information including:
                        - AS number and organization owning the IP
                        - Network ranges (inetnum / inet6num) and CIDR blocks
                        - Administrative, technical, and abuse contacts
                        - Routing data and route objects
                        - Raw WHOIS and R-WHOIS responses
                        Parameters:
                        - ip (required): IPv4 or IPv6 address. Examples: 8.8.8.8 | 2001:4860:4860::8888
                        - format (optional): json (default) or xml
                        """,
                        schema),
                (exchange, args) -> {
                    String ip = str(args, "ip");
                    if (ip == null) return result("Error: 'ip' is required.");
                    return result(service.ipWhois(ip, str(args, "format")));
                }
        );
    }

    // =========================================================================
    // asnWhoisLookup
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification asnWhoisSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "asn": {
                      "type": "string",
                      "description": "Autonomous System Number to look up. Accepts 'AS15169' or plain '15169'."
                    },
                    "format": {
                      "type": "string",
                      "enum": ["json", "xml"],
                      "description": "Response format. Default: json"
                    }
                  },
                  "required": ["asn"]
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "asnWhoisLookup",
                        """
                        Retrieve complete WHOIS registration data for any Autonomous System Number (ASN)
                        using the WhoisFreaks ASN WHOIS API.
                        Returns detailed ASN information including:
                        - Organization name and description
                        - Country of registration
                        - IP prefix/route announcements (IPv4 and IPv6)
                        - Peering relationships
                        - Administrative and technical contacts
                        - AS sets and AS blocks
                        Parameters:
                        - asn (required): Autonomous System Number. Examples: AS15169 | AS1009 | 15169
                        - format (optional): json (default) or xml
                        """,
                        schema),
                (exchange, args) -> {
                    String asn = str(args, "asn");
                    if (asn == null) return result("Error: 'asn' is required.");
                    return result(service.asnWhois(asn, str(args, "format")));
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
