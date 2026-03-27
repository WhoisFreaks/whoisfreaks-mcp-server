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
                        Retrieve geolocation and network information for any IPv4 or IPv6 address using the
                        WhoisFreaks IP Geolocation API.
                        Returns enriched location and network data including:
                        - Country, region, city, and postal code
                        - Latitude and longitude coordinates
                        - Timezone information
                        - ISP and organization name
                        - AS number and network details
                        - Connection type (broadband, mobile, satellite, etc.)
                        Parameters:
                        - ip (required): IPv4 or IPv6 address. Examples: 8.8.8.8 | 2606:4700:4700::1111
                        Note: Returns HTTP 423 for bogon/non-routable IP ranges (10.x.x.x, 192.168.x.x, etc.)
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
                        Retrieve detailed threat intelligence and security risk data for any IPv4 or IPv6
                        address using the WhoisFreaks IP Security API.
                        Returns comprehensive security signals including:
                        - VPN detection (commercial VPN services)
                        - Proxy detection (residential proxies, SOCKS, HTTP proxies)
                        - Tor exit node detection
                        - Bot and spam activity flags
                        - Cloud provider identification (AWS, GCP, Azure, etc.)
                        - Geolocation and network metadata
                        - Threat score and risk classification
                        Useful for fraud detection, access control, and threat analysis.
                        Parameters:
                        - ip (required): IPv4 or IPv6 address to analyze.
                          Examples: 1.1.1.1 | 185.220.101.0 (Tor exit node)
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
