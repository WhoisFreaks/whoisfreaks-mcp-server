package com.whoisfreaks.mcp.tools;

import com.whoisfreaks.mcp.WhoisFreaksService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * MCP tools for WhoisFreaks WHOIS APIs.
 *
 * Tools:
 *   liveWhoisLookup    — Real-time WHOIS data for any domain
 *   bulkWhoisLookup    — Real-time WHOIS data for up to 100 domains at once
 *   whoisHistory       — Full ownership history timeline for a domain
 *   reverseWhoisLookup — Search WHOIS database by email, keyword, owner, or company
 */
public class WhoisTools {

    private final Function<McpTransportContext, WhoisFreaksService> services;

    public WhoisTools(Function<McpTransportContext, WhoisFreaksService> services) {
        this.services = services;
    }

    public List<McpStatelessServerFeatures.SyncToolSpecification> specs() {
        return List.of(
                liveWhoisSpec(),
                bulkWhoisSpec(),
                whoisHistorySpec(),
                reverseWhoisSpec()
        );
    }

    // =========================================================================
    // liveWhoisLookup
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification liveWhoisSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "domainName": {
                      "type": "string",
                      "description": "Domain name to look up. Accepts: example.com | https://example.com | http://example.com"
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
                        "liveWhoisLookup",
                        """
                        Get the current WHOIS registration record for a domain, queried live from WHOIS servers and RDAP.
                        Use this to find who owns a domain now, its registrar, creation and expiry dates, name servers or status.
                        For past owners and changes over time use whoisHistory. For many domains at once use bulkWhoisLookup. To
                        find domains owned by a person, email or company use reverseWhoisLookup. To check whether an unregistered
                        name can be bought use domainAvailability.
                        Pass a bare domain (example.com), not a URL.
                        Returns registrar and registry details, creation/update/expiry dates, registrant, admin and technical
                        contacts (often privacy-redacted), name servers, domain status flags and the raw WHOIS text. When the
                        registrar does not answer, the result is marked as a partial record (HTTP 206); when no live lookup works,
                        as the latest stored record (HTTP 210).
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String domain = str(args, "domainName");
                    if (domain == null) return result("Error: 'domainName' is required.");
                    return result(services.apply(context).liveWhois(domain, str(args, "format")));
                }
        );
    }

    // =========================================================================
    // bulkWhoisLookup
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification bulkWhoisSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "domainNames": {
                      "type": "array",
                      "items": { "type": "string" },
                      "minItems": 1,
                      "maxItems": 100,
                      "description": "Domain names to look up, at most 100. Example: [\\"whoisfreaks.com\\", \\"jfreaks.com\\"]"
                    },
                    "format": {
                      "type": "string",
                      "enum": ["json", "xml"],
                      "description": "Response format. Default: json"
                    }
                  },
                  "required": ["domainNames"]
                }
                """;
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "bulkWhoisLookup",
                        """
                        Get the current WHOIS records of up to 100 domains in one request, queried live.
                        Use this instead of calling liveWhoisLookup many times, for example to audit a domain portfolio or enrich
                        a list of domains taken from logs. For one domain use liveWhoisLookup. Lists of more than 100 domains go
                        in several calls; files of up to 3 million domains are handled by the Bulk Tool in the WhoisFreaks billing
                        dashboard, not by this tool.
                        Costs 1 credit per domain. A large batch can take a few minutes.
                        Returns one WHOIS record per domain, with the same fields as liveWhoisLookup.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    List<String> domains = ToolArgs.stringList(args, "domainNames");
                    if (domains.isEmpty()) return result("Error: 'domainNames' is required: a list of up to 100 domain names.");
                    if (domains.size() > WhoisFreaksService.BULK_LIMIT) {
                        return result("Error: 'domainNames' has " + domains.size() + " domains; one request takes at most "
                                + WhoisFreaksService.BULK_LIMIT + ". Split the list into several calls.");
                    }
                    return result(services.apply(context).bulkWhois(domains, str(args, "format")));
                }
        );
    }

    // =========================================================================
    // whoisHistory
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification whoisHistorySpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "domainName": {
                      "type": "string",
                      "description": "Domain name to look up history for. Examples: whoisfreaks.com | google.com"
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
                        "whoisHistory",
                        """
                        Get the historical WHOIS records of a domain: deduplicated snapshots ordered from newest to oldest, going
                        back as far as 1986.
                        Use this to see previous owners, registrar transfers, past contacts, name server changes, or when a domain
                        expired and was re-registered. For only the current record use liveWhoisLookup. For DNS changes over time
                        use dnsHistory.
                        Costs 2 credits. Every record comes back in one response; there are no pages.
                        Returns a list of snapshots, each with the registrar, dates, contacts and name servers recorded at that time.
                        A domain with no recorded history returns a "no data found" message.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String domain = str(args, "domainName");
                    if (domain == null) return result("Error: 'domainName' is required.");
                    return result(services.apply(context).whoisHistory(domain, str(args, "format")));
                }
        );
    }

    // =========================================================================
    // reverseWhoisLookup
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification reverseWhoisSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "email": {
                      "type": "string",
                      "description": "Search by registrant email address, exact or regex match. Example: admin@example.com"
                    },
                    "keyword": {
                      "type": "string",
                      "description": "Search by text in the domain name. With exact=false (the default) it matches every domain whose name contains it. Example: google"
                    },
                    "owner": {
                      "type": "string",
                      "description": "Search by registrant/owner name, matched as a phrase unless exact=true. Example: John Doe"
                    },
                    "company": {
                      "type": "string",
                      "description": "Search by company/organization name, matched as a phrase unless exact=true. Example: Alphabet Inc"
                    },
                    "mode": {
                      "type": "string",
                      "enum": ["default", "mini"],
                      "description": "'default' (the default) returns the full WHOIS record of each domain, 50 per page. 'mini' returns only the domain name, creation/update/expiry dates and the registrant's name, email and company, 100 per page."
                    },
                    "exact": {
                      "type": "boolean",
                      "description": "false (the default): keyword matches every domain that contains it, and owner and company match as a phrase. true: match the value exactly."
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
                  }
                }
                """;
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "reverseWhoisLookup",
                        """
                        Find domains whose WHOIS records contain a given email, registrant name or company, or whose name contains
                        a keyword, across 4.6 billion WHOIS records.
                        Use this to map everything a person or organisation has registered, for example when investigating a
                        phishing actor or auditing a company's domain portfolio. Provide exactly one of email, owner, company or
                        keyword. To look up the owner of one known domain use liveWhoisLookup. To find look-alike domains of a
                        brand (typosquats) use domainDiscovery.
                        mode=mini returns a short summary per domain and 100 domains per page, against 50 full records per page in
                        the default mode. Costs 5 credits per page, so prefer mini when you only need the list of domains.
                        Results are paginated: the response gives the total pages, and page selects the next one.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String keyword = str(args, "keyword");
                    String email   = str(args, "email");
                    String owner   = str(args, "owner");
                    String company = str(args, "company");

                    long given = Stream.of(keyword, email, owner, company).filter(v -> v != null).count();
                    if (given == 0) {
                        return result("Error: Provide one search parameter: email, keyword, owner, or company.");
                    }
                    if (given > 1) {
                        return result("Error: Provide exactly one of email, keyword, owner, or company; search for each in its own call.");
                    }

                    Boolean exact = args.get("exact") instanceof Boolean b ? b : null;
                    Integer page  = args.get("page") instanceof Number n ? n.intValue() : null;
                    String mode   = str(args, "mode");
                    String format = str(args, "format");

                    return result(services.apply(context).reverseWhois(keyword, email, owner, company, mode, exact, page, format));
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
