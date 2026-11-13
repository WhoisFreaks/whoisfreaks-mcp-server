package com.whoisfreaks.mcp.tools;

import com.whoisfreaks.mcp.WhoisFreaksService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP tools for WhoisFreaks DNS APIs.
 *
 * Tools:
 *   dnsLookup        — Real-time DNS records for a domain, or PTR records for an IP address
 *   bulkDnsLookup    — Real-time DNS records for up to 100 domains and 100 IP addresses at once
 *   dnsHistory       — Historical DNS records from the WhoisFreaks database
 *   reverseDnsLookup — Search DNS database by IP address or record value
 */
public class DnsTools {

    private final Function<McpTransportContext, WhoisFreaksService> services;

    public DnsTools(Function<McpTransportContext, WhoisFreaksService> services) {
        this.services = services;
    }

    public List<McpStatelessServerFeatures.SyncToolSpecification> specs() {
        return List.of(
                dnsLookupSpec(),
                bulkDnsSpec(),
                dnsHistorySpec(),
                reverseDnsSpec()
        );
    }

    // =========================================================================
    // dnsLookup
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification dnsLookupSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "domainName": {
                      "type": "string",
                      "description": "Domain name to query DNS records for. Example: google.com. Optional when ipAddress is given."
                    },
                    "ipAddress": {
                      "type": "string",
                      "description": "IP address for a reverse (PTR) lookup. Example: 8.8.8.8. Pass it instead of, or together with, domainName."
                    },
                    "type": {
                      "type": "string",
                      "description": "Record types to return: 'all' (the default), one type such as MX, or a comma-separated list such as a,mx,ns. Supported: A, AAAA, MX, NS, CNAME, SOA, TXT, SPF."
                    },
                    "cnameDepth": {
                      "type": "integer",
                      "minimum": 0,
                      "maximum": 4,
                      "description": "How many CNAME hops to follow before collecting records, 0 to 4. Default: 0"
                    },
                    "relativeRecords": {
                      "type": "boolean",
                      "description": "true (the default): include the records of CNAME targets. false: only the records on the queried name."
                    },
                    "format": {
                      "type": "string",
                      "enum": ["json", "xml"],
                      "description": "Response format. Default: json"
                    }
                  }
                }
                """;
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "dnsLookup",
                        """
                        Resolve a domain's current DNS records live: A, AAAA, MX, NS, CNAME, SOA, TXT and SPF. With ipAddress it
                        also does a reverse (PTR) lookup of an IP address.
                        Use this to see where a domain points now: its IP addresses, mail servers, name servers, or TXT records
                        such as SPF, DKIM and DMARC. For how records changed over time use dnsHistory. To go the other way and
                        find domains that use a given IP, mail server or name server use reverseDnsLookup. For many domains or IPs
                        at once use bulkDnsLookup.
                        type=all (the default) returns every record type in one call; pass one type or a short list such as mx,txt
                        to keep the result small. cnameDepth follows CNAME chains, for example for www hosts behind a CDN.
                        Returns the records grouped by type. A domain with no records of the requested type returns an empty list
                        or a "no data found" message.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String domain = str(args, "domainName");
                    String ip     = str(args, "ipAddress");
                    if (domain == null && ip == null) return result("Error: Provide 'domainName', 'ipAddress', or both.");
                    Integer cnameDepth       = args.get("cnameDepth") instanceof Number n ? n.intValue() : null;
                    Boolean relativeRecords  = args.get("relativeRecords") instanceof Boolean b ? b : null;
                    return result(services.apply(context).dnsLookup(
                            domain, ip, str(args, "type"), cnameDepth, relativeRecords, str(args, "format")));
                }
        );
    }

    // =========================================================================
    // bulkDnsLookup
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification bulkDnsSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "domainNames": {
                      "type": "array",
                      "items": { "type": "string" },
                      "maxItems": 100,
                      "description": "Domain names to resolve, at most 100. Example: [\\"whoisfreaks.com\\", \\"jfreaks.com\\"]"
                    },
                    "ipAddresses": {
                      "type": "array",
                      "items": { "type": "string" },
                      "maxItems": 100,
                      "description": "IP addresses for reverse (PTR) lookups, at most 100. Example: [\\"1.1.1.1\\", \\"8.8.8.8\\"]"
                    },
                    "type": {
                      "type": "string",
                      "description": "Record types to return: 'all' (the default), one type such as MX, or a comma-separated list such as a,mx,ns. Supported: A, AAAA, TXT, NS, MX, SOA, SPF."
                    },
                    "cnameDepth": {
                      "type": "integer",
                      "minimum": 0,
                      "maximum": 4,
                      "description": "How many CNAME hops to follow before collecting records, 0 to 4. Default: 0"
                    },
                    "format": {
                      "type": "string",
                      "enum": ["json", "xml"],
                      "description": "Response format. Default: json"
                    }
                  }
                }
                """;
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "bulkDnsLookup",
                        """
                        Resolve the current DNS records of up to 100 domains, and the PTR records of up to 100 IP addresses, in one
                        request.
                        Use this instead of calling dnsLookup many times, for example to check the mail servers of a list of
                        domains or to name a list of IPs from logs. Provide domainNames, ipAddresses, or both. For one domain or IP
                        use dnsLookup. Lists longer than 100 go in several calls; files of up to 3 million entries are handled by
                        the Bulk Tool in the WhoisFreaks billing dashboard, not by this tool.
                        Costs 1 credit per domain or IP. A large batch can take a few minutes.
                        Returns one entry per domain or IP, with the same record fields as dnsLookup.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    List<String> domains = ToolArgs.stringList(args, "domainNames");
                    List<String> ips     = ToolArgs.stringList(args, "ipAddresses");
                    if (domains.isEmpty() && ips.isEmpty()) {
                        return result("Error: Provide 'domainNames', 'ipAddresses', or both: lists of up to 100 each.");
                    }
                    if (domains.size() > WhoisFreaksService.BULK_LIMIT || ips.size() > WhoisFreaksService.BULK_LIMIT) {
                        return result("Error: one request takes at most " + WhoisFreaksService.BULK_LIMIT + " domains and "
                                + WhoisFreaksService.BULK_LIMIT + " IP addresses (got " + domains.size() + " and " + ips.size()
                                + "). Split the lists into several calls.");
                    }
                    Integer cnameDepth = args.get("cnameDepth") instanceof Number n ? n.intValue() : null;
                    return result(services.apply(context).bulkDns(domains, ips, str(args, "type"), cnameDepth, str(args, "format")));
                }
        );
    }

    // =========================================================================
    // dnsHistory
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification dnsHistorySpec() {
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
                      "description": "Record types to return: 'all' (the default), one type such as A, or a comma-separated list such as a,mx,ns. Supported: A, AAAA, MX, NS, CNAME, SOA, TXT."
                    },
                    "page": {
                      "type": "integer",
                      "minimum": 1,
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
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "dnsHistory",
                        """
                        Get the historical DNS records of a domain, showing how its A, MX, NS and other records changed over time.
                        Use this for investigations and migrations: earlier hosting IPs (for example the origin IP behind a CDN),
                        past mail servers, or name server changes. For the current records only use dnsLookup. For ownership
                        changes use whoisHistory.
                        Costs 2 credits per page. Returns past records with the dates they were observed. Results are paginated:
                        raise page to get more, and filter by type to reduce output.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String domain = str(args, "domainName");
                    if (domain == null) return result("Error: 'domainName' is required.");
                    Integer page = args.get("page") instanceof Number n ? n.intValue() : null;
                    return result(services.apply(context).dnsHistory(domain, str(args, "type"), page, str(args, "format")));
                }
        );
    }

    // =========================================================================
    // reverseDnsLookup
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification reverseDnsSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "value": {
                      "type": "string",
                      "description": "The value to search for in DNS records: an IP address or CIDR range for A/AAAA (8.8.8.8 | 104.16.0.0/16), a host name for MX, NS or CNAME (mail.example.com), or text for TXT and SOA. With exact=false, '*' matches any characters."
                    },
                    "type": {
                      "type": "string",
                      "enum": ["a", "aaaa", "mx", "ns", "cname", "txt", "soa"],
                      "description": "One DNS record type to search within. Default: a"
                    },
                    "exact": {
                      "type": "boolean",
                      "description": "false (the default): the value is matched as a pattern, where '*' matches any characters. true: exact match. Ignored for A and AAAA, which always match the IP or range."
                    },
                    "page": {
                      "type": "integer",
                      "minimum": 1,
                      "description": "Page number for paginated results. Default: 1"
                    },
                    "sort": {
                      "type": "string",
                      "enum": ["asc", "desc"],
                      "description": "Sort the results by sortBy, ascending or descending. Omit for unsorted results."
                    },
                    "sortBy": {
                      "type": "string",
                      "enum": ["firstSeen", "lastSeen"],
                      "description": "Date that sort orders by. Only used together with sort. Default: lastSeen"
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
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "reverseDnsLookup",
                        """
                        Find domains whose DNS records point to a given value: an IP address or range, mail server, name server or
                        other record value.
                        Use this to see which other sites share a hosting IP, which domains use a mail server or name server, or to
                        map infrastructure during an investigation. Set type to match the value: a for an IPv4 address or range
                        (default), aaaa for IPv6, mx for a mail server, ns for a name server. For a domain's own records use
                        dnsLookup. For the registered owner of an IP block use ipWhoisLookup.
                        exact=false (the default) enables '*' wildcard matching, for example *.cloudflare.com. sort=desc with
                        sortBy=lastSeen puts the most recently seen domains first.
                        Costs 5 credits per page of up to 100 domains. Popular IPs and name servers can match millions of
                        domains, so use page and stop once you have enough.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String value = str(args, "value");
                    if (value == null) return result("Error: 'value' is required.");
                    Boolean exact = args.get("exact") instanceof Boolean b ? b : null;
                    Integer page  = args.get("page") instanceof Number n ? n.intValue() : null;
                    return result(services.apply(context).reverseDns(value, str(args, "type"), exact, page,
                            str(args, "sort"), str(args, "sortBy"), str(args, "format")));
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
