package com.whoisfreaks.mcp.tools;

import com.whoisfreaks.mcp.WhoisFreaksService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * MCP tools for WhoisFreaks IP intelligence APIs.
 *
 * Tools:
 *   ipGeolocation — Location, ISP, and network data for any IP address
 *   ipSecurity    — Threat intelligence and security risk data for any IP address
 */
public class IpTools {

    private final WhoisFreaksService service;

    public IpTools(WhoisFreaksService service) {
        this.service = service;
    }

    public List<McpServerFeatures.SyncToolSpecification> specs() {
        return List.of(
                ipGeolocationSpec(),
                ipSecuritySpec()
        );
    }

    // =========================================================================
    // ipGeolocation
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification ipGeolocationSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "ip": {
                      "type": "string",
                      "description": "IPv4 or IPv6 address to geolocate. Examples: 8.8.8.8 | 2606:4700:4700::1111"
                    }
                  },
                  "required": ["ip"]
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "ipGeolocation",
                        """
                        Look up where an IPv4 or IPv6 address is located and which network it belongs to.
                        Use this for country, region, city, coordinates, timezone, ISP, ASN and connection type, for example to
                        localise a visitor or see where a server is hosted. The location is an estimate and is most reliable at
                        country level. For VPN, proxy, Tor and threat signals use ipSecurity. For the registered owner of the IP
                        block and its abuse contact use ipWhoisLookup.
                        Private and reserved addresses (10.x, 192.168.x, 127.x and similar) cannot be geolocated and return a
                        "non-routable IP" (HTTP 423) message.
                        """,
                        schema),
                (exchange, args) -> {
                    String ip = str(args, "ip");
                    if (ip == null) return result("Error: 'ip' is required.");
                    return result(service.ipGeolocation(ip));
                }
        );
    }

    // =========================================================================
    // ipSecurity
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification ipSecuritySpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "ip": {
                      "type": "string",
                      "description": "IPv4 or IPv6 address to run security analysis on. Examples: 1.1.1.1 | 185.220.101.0"
                    }
                  },
                  "required": ["ip"]
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "ipSecurity",
                        """
                        Check an IPv4 or IPv6 address for risk signals: VPN, proxy, Tor exit node, bot or spam activity, and whether
                        it belongs to a cloud or hosting provider.
                        Use this for fraud checks, sign-up or login screening, or triaging a suspicious IP from logs. For location
                        and ISP only use ipGeolocation. For the registered owner and abuse contact use ipWhoisLookup.
                        Returns a flag for each signal, the provider name where known, geolocation and network metadata, and an
                        overall threat score and risk classification. A clean result means no signals were found, not that the IP
                        is guaranteed safe.
                        """,
                        schema),
                (exchange, args) -> {
                    String ip = str(args, "ip");
                    if (ip == null) return result("Error: 'ip' is required.");
                    return result(service.ipSecurity(ip));
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
