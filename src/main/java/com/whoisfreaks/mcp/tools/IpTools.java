package com.whoisfreaks.mcp.tools;

import com.whoisfreaks.mcp.WhoisFreaksService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP tools for WhoisFreaks IP intelligence APIs.
 *
 * Tools:
 *   ipGeolocation     — Location, ISP, and network data for any IP address
 *   bulkIpGeolocation — Location, ISP, and network data for up to 100 IP addresses at once
 *   ipSecurity        — Threat intelligence and security risk data (IP reputation) for any IP address
 *   bulkIpSecurity    — IP reputation for up to 100 IP addresses at once
 */
public class IpTools {

    private final Function<McpTransportContext, WhoisFreaksService> services;

    public IpTools(Function<McpTransportContext, WhoisFreaksService> services) {
        this.services = services;
    }

    public List<McpStatelessServerFeatures.SyncToolSpecification> specs() {
        return List.of(
                ipGeolocationSpec(),
                bulkIpGeolocationSpec(),
                ipSecuritySpec(),
                bulkIpSecuritySpec()
        );
    }

    // =========================================================================
    // ipGeolocation
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification ipGeolocationSpec() {
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
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "ipGeolocation",
                        """
                        Look up where an IPv4 or IPv6 address is located and which network it belongs to.
                        Use this for country, region, city, coordinates, ISP, ASN, connection type and local currency, for example
                        to localise a visitor or see where a server is hosted. The location is an estimate and is most reliable at
                        country level. For many IPs at once use bulkIpGeolocation. For VPN, proxy, Tor and threat signals use
                        ipSecurity. For the registered owner of the IP block and its abuse contact use ipWhoisLookup.
                        Private and reserved addresses (10.x, 192.168.x, 127.x and similar) cannot be geolocated and return a
                        "non-routable IP" (HTTP 423) message.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String ip = str(args, "ip");
                    if (ip == null) return result("Error: 'ip' is required.");
                    return result(services.apply(context).ipGeolocation(ip));
                }
        );
    }

    // =========================================================================
    // bulkIpGeolocation
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification bulkIpGeolocationSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "ips": {
                      "type": "array",
                      "items": { "type": "string" },
                      "minItems": 1,
                      "maxItems": 100,
                      "description": "IPv4 or IPv6 addresses to geolocate, at most 100. Example: [\\"1.1.1.1\\", \\"8.8.8.8\\"]"
                    }
                  },
                  "required": ["ips"]
                }
                """;
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "bulkIpGeolocation",
                        """
                        Look up the location and network of up to 100 IPv4 or IPv6 addresses in one request.
                        Use this instead of calling ipGeolocation many times, for example to map the visitors or attackers in a
                        log. For one IP use ipGeolocation. For threat signals on many IPs use bulkIpSecurity.
                        Costs 1 credit per IP answered.
                        Returns one entry per IP, with the same fields as ipGeolocation.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    List<String> ips = ToolArgs.stringList(args, "ips");
                    String error = checkIps(ips);
                    if (error != null) return result(error);
                    return result(services.apply(context).bulkIpGeolocation(ips));
                }
        );
    }

    // =========================================================================
    // ipSecurity
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification ipSecuritySpec() {
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
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "ipSecurity",
                        """
                        Check the reputation of an IPv4 or IPv6 address: VPN, proxy (including residential), Tor exit node, privacy
                        relay, spam and known-attacker activity, bots by type and operator, and whether it belongs to a cloud
                        provider or a corporate secure web gateway.
                        Use this for fraud checks, sign-up or login screening, or triaging a suspicious IP from logs. For many IPs
                        at once use bulkIpSecurity. For location and ISP only use ipGeolocation. For the registered owner and
                        abuse contact use ipWhoisLookup.
                        Returns a threat score from 0 to 100, a flag and confidence score for each signal, provider names where
                        known, and the IP's location, network (route, anycast, CDN) and ASN. A clean result means no signals were
                        found, not that the IP is guaranteed safe.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String ip = str(args, "ip");
                    if (ip == null) return result("Error: 'ip' is required.");
                    return result(services.apply(context).ipSecurity(ip));
                }
        );
    }

    // =========================================================================
    // bulkIpSecurity
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification bulkIpSecuritySpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "ips": {
                      "type": "array",
                      "items": { "type": "string" },
                      "minItems": 1,
                      "maxItems": 100,
                      "description": "IPv4 or IPv6 addresses to check, at most 100. Example: [\\"1.1.1.1\\", \\"185.220.101.45\\"]"
                    }
                  },
                  "required": ["ips"]
                }
                """;
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "bulkIpSecurity",
                        """
                        Check the reputation of up to 100 IPv4 or IPv6 addresses in one request: VPN, proxy, Tor, relay, spam,
                        known-attacker and bot signals for each.
                        Use this instead of calling ipSecurity many times, for example to screen the IPs behind a wave of sign-ups or
                        failed logins. For one IP use ipSecurity. For location only use bulkIpGeolocation.
                        Costs 1 credit per IP answered. An invalid IP returns its own error entry while the other IPs still return
                        results.
                        Returns one entry per IP, with the same fields as ipSecurity.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    List<String> ips = ToolArgs.stringList(args, "ips");
                    String error = checkIps(ips);
                    if (error != null) return result(error);
                    return result(services.apply(context).bulkIpSecurity(ips));
                }
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Why a bulk list of IPs cannot be sent, or null when it can. */
    private String checkIps(List<String> ips) {
        if (ips.isEmpty()) return "Error: 'ips' is required: a list of up to 100 IP addresses.";
        if (ips.size() > WhoisFreaksService.BULK_LIMIT) {
            return "Error: 'ips' has " + ips.size() + " addresses; one request takes at most "
                    + WhoisFreaksService.BULK_LIMIT + ". Split the list into several calls.";
        }
        return null;
    }

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
