package com.whoisfreaks.mcp.tools;

import com.whoisfreaks.mcp.WhoisFreaksService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP tools for WhoisFreaks WHOIS APIs.
 *
 * Tools:
 *   liveWhoisLookup    — Real-time WHOIS data for any domain
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
                        Get the current WHOIS registration record for a domain, queried live.
                        Use this to find who owns a domain now, its registrar, creation and expiry dates, name servers or status.
                        For past owners and changes over time use whoisHistory. To find domains owned by a person, email or company
                        use reverseWhoisLookup. To check whether an unregistered name can be bought use domainAvailability.
                        Pass a bare domain (example.com), not a URL.
                        Returns registrar and registry details, creation/update/expiry dates, registrant, admin and technical
                        contacts (often privacy-redacted), name servers and domain status flags.
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
                        Get the historical WHOIS records of a domain: deduplicated snapshots ordered from newest to oldest.
                        Use this to see previous owners, registrar transfers, past contacts, name server changes, or when a domain
                        expired and was re-registered. For only the current record use liveWhoisLookup. For DNS changes over time
                        use dnsHistory.
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
                      "description": "Search by registrant email address. Example: admin@example.com"
                    },
                    "keyword": {
                      "type": "string",
                      "description": "Search by keyword found anywhere in WHOIS records. Example: google"
                    },
                    "owner": {
                      "type": "string",
                      "description": "Search by registrant/owner name. Example: John Doe"
                    },
                    "company": {
                      "type": "string",
                      "description": "Search by company/organization name. Example: Alphabet Inc"
                    },
                    "mode": {
                      "type": "string",
                      "enum": ["mini", "default"],
                      "description": "Response detail level. 'mini' returns only domain names; 'default' returns full WHOIS records. Default: mini"
                    },
                    "exact": {
                      "type": "boolean",
                      "description": "If true, requires an exact match. If false, allows pattern/partial matching. Default: true"
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
                  }
                }
                """;
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "reverseWhoisLookup",
                        """
                        Find domains whose WHOIS records contain a given email, registrant name, company or keyword.
                        Use this to map everything a person or organisation has registered, for example when investigating a
                        phishing actor or auditing a company's domain portfolio. Provide exactly one of email, owner, company or
                        keyword. To look up the owner of one known domain use liveWhoisLookup. To find domains by text in the
                        domain name itself use domainDiscovery.
                        mode=mini returns domain names only; mode=default returns the full WHOIS record for each domain.
                        exact=false allows partial matches and returns more, noisier results. Results are paginated: raise page
                        to get more.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String keyword = str(args, "keyword");
                    String email   = str(args, "email");
                    String owner   = str(args, "owner");
                    String company = str(args, "company");

                    if (keyword == null && email == null && owner == null && company == null) {
                        return result("Error: Provide at least one search parameter: email, keyword, owner, or company.");
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
