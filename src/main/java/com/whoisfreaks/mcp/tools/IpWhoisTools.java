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
                        Get the registry WHOIS record for an IPv4 or IPv6 address: who the IP block is allocated to.
                        Use this to find the owning organisation, CIDR range, AS number and abuse contact of an IP, for example to
                        report abuse. For city, ISP and coordinates use ipGeolocation. For VPN, proxy, Tor and threat signals use
                        ipSecurity. For details about the autonomous system itself use asnWhoisLookup. For domains hosted on the
                        IP use reverseDnsLookup.
                        Returns the network range (inetnum/inet6num) and CIDR, owning organisation and ASN, admin, technical and
                        abuse contacts, route objects, and the raw WHOIS text.
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
                        Get the registry WHOIS record for an autonomous system (ASN): the network operator behind a set of IP routes.
                        Use this when you have an AS number (for example from ipWhoisLookup or ipGeolocation output) and need the
                        operator's name, country, contacts or the IP prefixes it announces. If you only have an IP address, call
                        ipWhoisLookup first to get its ASN.
                        Returns the organisation name and description, country of registration, announced IPv4 and IPv6 prefixes,
                        peering information, admin and technical contacts, and AS sets and blocks.
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
