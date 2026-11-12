package com.whoisfreaks.mcp.tools;

import com.whoisfreaks.mcp.WhoisFreaksService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MCP tools for WhoisFreaks Domain APIs.
 *
 * Tools:
 *   domainAvailability — Check if a domain is available for registration, with suggestions
 *   subdomainLookup    — Find all known subdomains for a domain
 *   domainDiscovery    — Discover domains matching a keyword or pattern (typos/typosquatting)
 */
public class DomainTools {

    private final Function<McpTransportContext, WhoisFreaksService> services;

    public DomainTools(Function<McpTransportContext, WhoisFreaksService> services) {
        this.services = services;
    }

    public List<McpStatelessServerFeatures.SyncToolSpecification> specs() {
        return List.of(
                domainAvailabilitySpec(),
                subdomainLookupSpec(),
                domainDiscoverySpec()
        );
    }

    // =========================================================================
    // domainAvailability
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification domainAvailabilitySpec() {
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
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "domainAvailability",
                        """
                        Check whether a domain name is available to register, using both DNS and WHOIS data.
                        Use this before suggesting or buying a domain name. Set suggestions=true to also get available alternative
                        names when the domain is taken; count sets how many (1-100). For details about who owns a registered
                        domain use liveWhoisLookup. To find existing registered domains containing a keyword use domainDiscovery.
                        Returns the status (available, taken or reserved) and which source decided it. Availability can change at
                        any moment, and this check does not reserve the domain.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String domain = str(args, "domain");
                    if (domain == null) return result("Error: 'domain' is required.");
                    Boolean suggestions = args.get("suggestions") instanceof Boolean b ? b : null;
                    Integer count       = args.get("count") instanceof Number n ? n.intValue() : null;
                    return result(services.apply(context).domainAvailability(domain, suggestions, count, str(args, "format")));
                }
        );
    }

    // =========================================================================
    // subdomainLookup
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification subdomainLookupSpec() {
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
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "subdomainLookup",
                        """
                        List the known subdomains of a domain (for example api.example.com or mail.example.com) from the WhoisFreaks
                        subdomain database.
                        Use this for attack-surface mapping, finding exposed services, or auditing a company's hosts. It looks up
                        subdomains already discovered by WhoisFreaks rather than scanning the target, so it only knows subdomains
                        that have been observed. To get a subdomain's DNS records use dnsLookup. To find other domains (not
                        subdomains) with a similar name use domainDiscovery.
                        status=active keeps only subdomains that currently resolve; after and before (YYYY-MM-DD) filter by when a
                        subdomain was discovered. Results are paginated, so large domains may need several pages.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String domain = str(args, "domain");
                    if (domain == null) return result("Error: 'domain' is required.");
                    Integer page = args.get("page") instanceof Number n ? n.intValue() : null;
                    return result(services.apply(context).subdomainLookup(
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

    private McpStatelessServerFeatures.SyncToolSpecification domainDiscoverySpec() {
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
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "domainDiscovery",
                        """
                        Find registered domains whose name contains a keyword or matches a wildcard pattern, across the WhoisFreaks
                        database of 888M+ domains.
                        Use this for brand protection (typosquats such as g00gle.com), keyword research, or tracking look-alike
                        registrations. Provide keyword or pattern. keyword matches domain names containing that exact string;
                        pattern supports '*' for any characters and '?' for one character, for example *goog* or g?ogle.
                        labelLength caps the length of the name before the TLD. To find domains by owner rather than by name use
                        reverseWhoisLookup. To check one specific name use domainAvailability.
                        Returns matching domain names. Results are paginated: raise page to get more.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String keyword = str(args, "keyword");
                    String pattern = str(args, "pattern");
                    if (keyword == null && pattern == null) {
                        return result("Error: Provide at least one of 'keyword' or 'pattern'.");
                    }
                    Integer labelLength = args.get("labelLength") instanceof Number n ? n.intValue() : null;
                    Integer page        = args.get("page") instanceof Number n ? n.intValue() : null;
                    return result(services.apply(context).domainDiscovery(keyword, pattern, labelLength, page, str(args, "format")));
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
