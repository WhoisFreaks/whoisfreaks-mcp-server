package com.whoisfreaks.mcp.tools;

import com.whoisfreaks.mcp.WhoisFreaksService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * MCP tools for WhoisFreaks DNS APIs.
 *
 * Tools:
 *   dnsLookup       — Real-time DNS records for a domain (A, AAAA, MX, NS, CNAME, SOA, TXT, SPF, all)
 *   dnsHistory      — Historical DNS records from the WhoisFreaks database
 *   reverseDnsLookup — Search DNS database by IP address or record value
 */
public class DnsTools {

    private final WhoisFreaksService service;

    public DnsTools(WhoisFreaksService service) {
        this.service = service;
    }

    public List<McpServerFeatures.SyncToolSpecification> specs() {
        return List.of(
                dnsLookupSpec(),
                dnsHistorySpec(),
                reverseDnsSpec()
        );
    }

    // =========================================================================
    // dnsLookup
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification dnsLookupSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "domainName": {
                      "type": "string",
                      "description": "Domain name to query DNS records for. Example: google.com"
                    },
                    "type": {
                      "type": "string",
                      "enum": ["A", "AAAA", "MX", "NS", "CNAME", "SOA", "TXT", "SPF", "all"],
                      "description": "DNS record type to retrieve. Use 'all' to get all record types. Default: all"
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
                        "dnsLookup",
                        """
                        Resolve a domain's current DNS records live: A, AAAA, MX, NS, CNAME, SOA, TXT and SPF.
                        Use this to see where a domain points now: its IP addresses, mail servers, name servers, or TXT records
                        such as SPF, DKIM and DMARC. For how records changed over time use dnsHistory. To go the other way and
                        find domains that use a given IP, mail server or name server use reverseDnsLookup.
                        type=all (the default) returns every record type in one call; pass a single type to keep the result small.
                        Returns the records grouped by type. A domain with no records of the requested type returns an empty list
                        or a "no data found" message.
                        """,
                        schema),
                (exchange, args) -> {
                    String domain = str(args, "domainName");
                    if (domain == null) return result("Error: 'domainName' is required.");
                    return result(service.dnsLookup(domain, str(args, "type"), str(args, "format")));
                }
        );
    }

    // =========================================================================
    // dnsHistory
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification dnsHistorySpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "domainName": {
                      "type": "string",
                      "description": "Domain name to retrieve historical DNS records for. Example: whoisfreaks.com"
                    },
                    "type": {
                      "type": "string",
                      "enum": ["A", "AAAA", "MX", "NS", "CNAME", "SOA", "TXT", "SPF", "all"],
                      "description": "DNS record type to retrieve history for. Default: all"
                    },
                    "page": {
                      "type": "integer",
                      "description": "Page number for paginated results. Default: 1"
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
                        "dnsHistory",
                        """
                        Get the historical DNS records of a domain, showing how its A, MX, NS and other records changed over time.
                        Use this for investigations and migrations: earlier hosting IPs (for example the origin IP behind a CDN),
                        past mail servers, or name server changes. For the current records only use dnsLookup. For ownership
                        changes use whoisHistory.
                        Returns past records with the dates they were observed. Results are paginated: raise page to get more,
                        and filter by type to reduce output.
                        """,
                        schema),
                (exchange, args) -> {
                    String domain = str(args, "domainName");
                    if (domain == null) return result("Error: 'domainName' is required.");
                    Integer page = args.get("page") instanceof Number n ? n.intValue() : null;
                    return result(service.dnsHistory(domain, str(args, "type"), page, str(args, "format")));
                }
        );
    }

    // =========================================================================
    // reverseDnsLookup
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification reverseDnsSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "value": {
                      "type": "string",
                      "description": "The value to search for in DNS records. For A records: an IP address (8.8.8.8). For MX/NS: a hostname (mail.example.com). Supports wildcard '*' when exact=false."
                    },
                    "type": {
                      "type": "string",
                      "enum": ["A", "AAAA", "MX", "NS", "CNAME", "SOA", "TXT"],
                      "description": "DNS record type to search within. Default: A"
                    },
                    "exact": {
                      "type": "boolean",
                      "description": "true = exact value match (default). false = allow wildcard/pattern matching with '*'."
                    },
                    "page": {
                      "type": "integer",
                      "description": "Page number for paginated results. Default: 1"
                    },
                    "format": {
                      "type": "string",
                      "enum": ["json", "xml"],
                      "description": "Response format. Default: json"
                    }
                  },
                  "required": ["value"]
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "reverseDnsLookup",
                        """
                        Find domains whose DNS records point to a given value: an IP address, mail server, name server or other
                        record value.
                        Use this to see which other sites share a hosting IP, which domains use a mail server or name server, or to
                        map infrastructure during an investigation. Set type to match the value: A for an IPv4 address (default),
                        AAAA for IPv6, MX for a mail server, NS for a name server. For a domain's own records use dnsLookup. For
                        the registered owner of an IP block use ipWhoisLookup.
                        exact=false enables '*' wildcard matching, for example *.cloudflare.com. Popular IPs and name servers can
                        match millions of domains; results are paginated, so use page and stop once you have enough.
                        """,
                        schema),
                (exchange, args) -> {
                    String value = str(args, "value");
                    if (value == null) return result("Error: 'value' is required.");
                    Boolean exact = args.get("exact") instanceof Boolean b ? b : null;
                    Integer page  = args.get("page") instanceof Number n ? n.intValue() : null;
                    return result(service.reverseDns(value, str(args, "type"), exact, page, str(args, "format")));
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
