package com.whoisfreaks.mcp.tools;

import com.whoisfreaks.mcp.WhoisFreaksService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;

/**
 * MCP tools for WhoisFreaks Domain APIs.
 *
 * Tools:
 *   domainAvailability — Check if a domain is available for registration, with suggestions
 *   subdomainLookup    — Find all known subdomains for a domain
 *   domainDiscovery    — Discover domains matching a keyword or pattern (typos/typosquatting)
 */
public class DomainTools {

    private final WhoisFreaksService service;

    public DomainTools(WhoisFreaksService service) {
        this.service = service;
    }

    public List<McpServerFeatures.SyncToolSpecification> specs() {
        return List.of(
                domainAvailabilitySpec(),
                subdomainLookupSpec(),
                domainDiscoverySpec()
        );
    }

    // =========================================================================
    // domainAvailability
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification domainAvailabilitySpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "domain": {
                      "type": "string",
                      "description": "Domain name to check availability for. Example: whoisfreaks.com"
                    },
                    "suggestions": {
                      "type": "boolean",
                      "description": "If true, also return alternative domain name suggestions. Default: false"
                    },
                    "count": {
                      "type": "integer",
                      "description": "Number of suggestions to return when suggestions=true. Range: 1–100. Default: 5"
                    },
                    "format": {
                      "type": "string",
                      "enum": ["json", "xml"],
                      "description": "Response format. Default: json"
                    }
                  },
                  "required": ["domain"]
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "domainAvailability",
                        """
                        Check whether a domain name is available for registration using the WhoisFreaks
                        Domain Availability API. Checks both DNS and WHOIS sources for accurate status.
                        Optionally returns alternative domain suggestions when the requested domain is taken.
                        Returns:
                        - Domain availability status (available/taken/reserved)
                        - Registration source (DNS-based, WHOIS-based, or both)
                        - Alternative domain suggestions (when suggestions=true)
                        Parameters:
                        - domain (required): Domain to check. Example: mycompany.com
                        - suggestions (optional): true to get alternative domain suggestions. Default: false
                        - count (optional): Number of suggestions to return (1–100). Default: 5
                        - format (optional): json (default) or xml
                        """,
                        schema),
                (exchange, args) -> {
                    String domain = str(args, "domain");
                    if (domain == null) return result("Error: 'domain' is required.");
                    Boolean suggestions = args.get("suggestions") instanceof Boolean b ? b : null;
                    Integer count       = args.get("count") instanceof Number n ? n.intValue() : null;
                    return result(service.domainAvailability(domain, suggestions, count, str(args, "format")));
                }
        );
    }

    // =========================================================================
    // subdomainLookup
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification subdomainLookupSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "domain": {
                      "type": "string",
                      "description": "Root domain to find subdomains for. Example: whoisfreaks.com"
                    },
                    "status": {
                      "type": "string",
                      "enum": ["active", "inactive"],
                      "description": "Filter by subdomain status. Omit to return both active and inactive."
                    },
                    "after": {
                      "type": "string",
                      "description": "Return subdomains first seen after this date (ISO format: YYYY-MM-DD). Example: 2023-01-01"
                    },
                    "before": {
                      "type": "string",
                      "description": "Return subdomains first seen before this date (ISO format: YYYY-MM-DD). Example: 2024-01-01"
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
                  "required": ["domain"]
                }
                """;
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "subdomainLookup",
                        """
                        Discover all known subdomains for a given domain using the WhoisFreaks Subdomains API.
                        Useful for:
                        - Attack surface mapping and security reconnaissance
                        - Discovering internal services exposed publicly
                        - Competitive intelligence on domain infrastructure
                        - Tracking subdomain changes over time
                        Results can be filtered by active/inactive status and discovery date range.
                        Parameters:
                        - domain (required): Root domain to enumerate subdomains for. Example: whoisfreaks.com
                        - status (optional): 'active' or 'inactive'. Omit for all subdomains.
                        - after (optional): Only return subdomains discovered after this date (YYYY-MM-DD)
                        - before (optional): Only return subdomains discovered before this date (YYYY-MM-DD)
                        - page (optional): Page number for pagination. Default: 1
                        - format (optional): json (default) or xml
                        """,
                        schema),
                (exchange, args) -> {
                    String domain = str(args, "domain");
                    if (domain == null) return result("Error: 'domain' is required.");
                    Integer page = args.get("page") instanceof Number n ? n.intValue() : null;
                    return result(service.subdomainLookup(
                            domain,
                            str(args, "status"),
                            str(args, "after"),
                            str(args, "before"),
                            page,
                            str(args, "format")
                    ));
                }
        );
    }

    // =========================================================================
    // domainDiscovery (Typos / Typosquatting)
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification domainDiscoverySpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "keyword": {
                      "type": "string",
                      "description": "Search for domains containing this keyword in their label. Examples: google | youtube | whoisfreaks"
                    },
                    "pattern": {
                      "type": "string",
                      "description": "Search for domains matching this pattern. Use '*' as wildcard. Examples: *google* | g00gle | *freaks*"
                    },
                    "labelLength": {
                      "type": "integer",
                      "description": "Filter results to domains whose label (without TLD) has length less than or equal to this value."
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
        return new McpServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "domainDiscovery",
                        """
                        Search the WhoisFreaks database (888M+ domains) to discover registered domains matching
                        a keyword or pattern. Powers typo and typosquatting domain discovery.
                        Use cases:
                        - Brand protection: Find typosquatted variants of your domain (g00gle.com, googel.com)
                        - Keyword research: Find all registered domains containing a keyword
                        - Pattern matching: Discover domains matching a specific naming pattern
                        - Competitive intelligence: Track competitor domain registrations
                        Provide keyword OR pattern (not both):
                        - keyword: finds domains whose label contains this exact string. Example: 'youtube'
                        - pattern: supports '*' wildcard. Examples: '*goog*', 'g?ogle', '*freaks*'
                        Parameters:
                        - keyword (optional): Keyword to find in domain labels. Example: google
                        - pattern (optional): Wildcard pattern with '*'. Example: *google* | g00gle
                        - labelLength (optional): Max label length filter (excludes TLD). Example: 8
                        - page (optional): Page number for pagination. Default: 1
                        - format (optional): json or xml. Default: json
                        Note: Provide at least one of 'keyword' or 'pattern'.
                        """,
                        schema),
                (exchange, args) -> {
                    String keyword = str(args, "keyword");
                    String pattern = str(args, "pattern");
                    if (keyword == null && pattern == null) {
                        return result("Error: Provide at least one of 'keyword' or 'pattern'.");
                    }
                    Integer labelLength = args.get("labelLength") instanceof Number n ? n.intValue() : null;
                    Integer page        = args.get("page") instanceof Number n ? n.intValue() : null;
                    return result(service.domainDiscovery(keyword, pattern, labelLength, page, str(args, "format")));
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
