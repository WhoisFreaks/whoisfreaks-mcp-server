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
                        Retrieve real-time DNS records for any domain using the WhoisFreaks DNS API.
                        Supports all major record types:
                        - A: IPv4 address records
                        - AAAA: IPv6 address records
                        - MX: Mail server records
                        - NS: Name server records
                        - CNAME: Canonical name (alias) records
                        - SOA: Start of Authority records
                        - TXT: Text records (includes SPF, DKIM, DMARC)
                        - SPF: Sender Policy Framework records
                        - all: Retrieve all record types at once
                        Parameters:
                        - domainName (required): Domain to query. Example: google.com
                        - type (optional): Record type — A | AAAA | MX | NS | CNAME | SOA | TXT | SPF | all. Default: all
                        - format (optional): json (default) or xml
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
                        Retrieve historical DNS records for a domain from the WhoisFreaks DNS history database.
                        Shows how DNS records have changed over time, useful for:
                        - Tracking IP address changes for a domain
                        - Investigating historical mail server configurations
                        - Researching past name server assignments
                        - Domain migration analysis
                        Parameters:
                        - domainName (required): Domain to query history for. Example: whoisfreaks.com
                        - type (optional): Record type — A | AAAA | MX | NS | CNAME | SOA | TXT | SPF | all. Default: all
                        - page (optional): Page number for pagination. Default: 1
                        - format (optional): json (default) or xml
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
                        Search the WhoisFreaks DNS database by record value to find all domains pointing to
                        a specific IP address, mail server, or name server.
                        Common use cases:
                        - Find all domains hosted on a given IP address (A record reverse lookup)
                        - Find all domains using a specific mail server (MX record reverse lookup)
                        - Find all domains using a specific name server (NS record reverse lookup)
                        - Discover domain infrastructure and hosting patterns
                        Parameters:
                        - value (required): The value to search for.
                          Examples: 8.8.8.8 (for A records) | mail.google.com (for MX) | ns1.example.com (for NS)
                          Use '*' as wildcard when exact=false. Example: *.cloudflare.com
                        - type (optional): Record type to search — A | AAAA | MX | NS | CNAME | SOA | TXT. Default: A
                        - exact (optional): true for exact match (default), false for wildcard/pattern matching
                        - page (optional): Page number for pagination. Default: 1
                        - format (optional): json (default) or xml
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
