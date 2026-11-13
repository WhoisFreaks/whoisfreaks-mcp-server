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
 *   domainAvailability     — Check if a domain is available for registration, with suggestions
 *   bulkDomainAvailability — Check up to 100 domains, or one name across up to 100 TLDs
 *   subdomainLookup        — Find all known subdomains for a domain
 *   domainDiscovery        — Find registered domains that are typo variants of a brand (typosquatting)
 *   subdomainTyposquatting — Find subdomains on any domain that contain a typo variant of a brand
 *   domainReputation       — Risk verdict, trust score and threat intelligence for a domain
 */
public class DomainTools {

    private final Function<McpTransportContext, WhoisFreaksService> services;

    public DomainTools(Function<McpTransportContext, WhoisFreaksService> services) {
        this.services = services;
    }

    public List<McpStatelessServerFeatures.SyncToolSpecification> specs() {
        return List.of(
                domainAvailabilitySpec(),
                bulkDomainAvailabilitySpec(),
                subdomainLookupSpec(),
                domainDiscoverySpec(),
                subdomainTyposquattingSpec(),
                domainReputationSpec()
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
                      "description": "If true, also return alternative domain names with other extensions and whether each is available. Default: false"
                    },
                    "count": {
                      "type": "integer",
                      "minimum": 5,
                      "maximum": 100,
                      "description": "Number of suggestions to return when suggestions=true, 5 to 100. Default: 5"
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
                        Use this before suggesting or buying a domain name. Set suggestions=true to also get alternative names with
                        other extensions, each marked available or not; count sets how many (5-100). To check a list of names, or
                        one name across many extensions, use bulkDomainAvailability. For details about who owns a registered
                        domain use liveWhoisLookup.
                        Costs 1 credit, plus 1 credit per 5 suggestions.
                        Returns the availability (AVAILABLE or UNAVAILABLE), or with suggestions a list of names each with
                        domainAvailability true or false. Availability can change at any moment, and this check does not reserve
                        the domain.
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
    // bulkDomainAvailability
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification bulkDomainAvailabilitySpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "domainNames": {
                      "type": "array",
                      "items": { "type": "string" },
                      "maxItems": 100,
                      "description": "Full domain names to check, at most 100. Example: [\\"whoisfreaks.io\\", \\"jfreaks.pk\\"]. Leave out when you pass domain and tlds."
                    },
                    "domain": {
                      "type": "string",
                      "description": "The name to check across the extensions in tlds. Example: whoisfreaks.com. Use together with tlds."
                    },
                    "tlds": {
                      "type": "array",
                      "items": { "type": "string" },
                      "maxItems": 100,
                      "description": "Extensions (TLDs) to check the domain against, without the leading dot, at most 100. Example: [\\"io\\", \\"dev\\", \\"ai\\"]"
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
                        "bulkDomainAvailability",
                        """
                        Check whether many domain names can be registered, in one request: either up to 100 full domain names, or
                        one name across up to 100 extensions (TLDs).
                        Use this to pick an available name across extensions such as .com, .io and .ai (pass domain and tlds), or
                        to check a list of candidate names (pass domainNames). For one name, or for name ideas, use
                        domainAvailability with suggestions=true.
                        Uses API credits for each domain checked. An invalid name returns its own error entry while the other names
                        still return results.
                        Returns each domain with its availability: AVAILABLE, UNAVAILABLE or UNKNOWN. Availability can change at
                        any moment, and this check does not reserve anything.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    List<String> domains = ToolArgs.stringList(args, "domainNames");
                    String domain        = str(args, "domain");
                    List<String> tlds    = ToolArgs.stringList(args, "tlds").stream()
                            .map(t -> t.replaceFirst("^\\.+", ""))
                            .filter(t -> !t.isEmpty())
                            .toList();
                    if (!domains.isEmpty() && (domain != null || !tlds.isEmpty())) {
                        return result("Error: Pass either 'domainNames', or 'domain' with 'tlds', not both. Use two calls to do both.");
                    }
                    if (domains.isEmpty() && (domain == null || tlds.isEmpty())) {
                        return result("Error: Pass 'domainNames' (up to 100 full domain names), or 'domain' together with 'tlds' (up to 100 extensions).");
                    }
                    if (domains.size() > WhoisFreaksService.BULK_LIMIT || tlds.size() > WhoisFreaksService.BULK_LIMIT) {
                        return result("Error: one request takes at most " + WhoisFreaksService.BULK_LIMIT
                                + " domains or TLDs. Split the list into several calls.");
                    }
                    return result(services.apply(context).bulkDomainAvailability(domains, domain, tlds, str(args, "format")));
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
                      "description": "Domain to find subdomains for. Example: whoisfreaks.com. A subdomain such as api.whoisfreaks.com returns the hosts beneath it."
                    },
                    "status": {
                      "type": "string",
                      "enum": ["active", "inactive"],
                      "description": "Filter by subdomain status. Omit to return both active and inactive."
                    },
                    "after": {
                      "type": "string",
                      "description": "Return subdomains first seen after this date (ISO format: YYYY-MM-DD). Must not be in the future or later than before. Example: 2023-01-01"
                    },
                    "before": {
                      "type": "string",
                      "description": "Return subdomains first seen before this date (ISO format: YYYY-MM-DD). Must not be in the future. Example: 2024-01-01"
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
                        that have been observed. To get a subdomain's DNS records use dnsLookup. To find look-alike domains of a
                        brand use domainDiscovery, and look-alike subdomains on other domains use subdomainTyposquatting.
                        status=active keeps only subdomains that currently resolve; after and before (YYYY-MM-DD) filter by when a
                        subdomain was discovered. Costs 2 credits per page. Results are paginated, so large domains may need
                        several pages.
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
    // domainDiscovery (Domain Typosquatting)
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification domainDiscoverySpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "keyword": {
                      "type": "string",
                      "minLength": 3,
                      "maxLength": 63,
                      "pattern": "^[A-Za-z0-9-]+$",
                      "description": "Brand or name to find typo variants of: 3 to 63 letters, digits or hyphens, one label without a dot. Example: paypal (not paypal.com)"
                    },
                    "pattern": {
                      "type": "string",
                      "minLength": 5,
                      "maxLength": 63,
                      "description": "Wildcard search instead of keyword: '*' matches any characters, at most 3 asterisks, at least 5 other characters and 63 in total. Example: *paypa*"
                    },
                    "pageToken": {
                      "type": "string",
                      "description": "nextPageToken from the previous result, to get the next page. Pass the same keyword or pattern with it."
                    },
                    "fetchSize": {
                      "type": "integer",
                      "minimum": 1,
                      "maximum": 10000,
                      "description": "Domains per page, 1 to 10000. Default: 100"
                    }
                  }
                }
                """;
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "domainDiscovery",
                        """
                        Find registered domains that look like typo variants of a brand name (typosquats such as paypa1.com or
                        paypal-secure.net), or that match a wildcard pattern, across more than 1,500 TLDs.
                        Use this for brand protection, phishing detection, or tracking look-alike registrations. Provide exactly one
                        of keyword or pattern. For subdomains that imitate a brand on unrelated domains, such as
                        login.paypa1.example.com, use subdomainTyposquatting. To find domains whose name simply contains a word use
                        reverseWhoisLookup with keyword. To judge how risky a result is use domainReputation; for its owner use
                        liveWhoisLookup.
                        Costs 5 credits per page (100 domains by default). A search can take up to two minutes.
                        Returns each domain with its creation, expiry and last-seen dates and whether it has been dropped, plus the
                        total count. When there are more, the result has a nextPageToken: pass it as pageToken, with the same
                        keyword or pattern, to get the next page.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String keyword = str(args, "keyword");
                    String pattern = str(args, "pattern");
                    if (keyword == null && pattern == null) {
                        return result("Error: Provide one of 'keyword' or 'pattern'.");
                    }
                    if (keyword != null && pattern != null) {
                        return result("Error: Provide 'keyword' or 'pattern', not both; search for each in its own call.");
                    }
                    Integer fetchSize = args.get("fetchSize") instanceof Number n ? n.intValue() : null;
                    return result(services.apply(context).domainTyposquats(keyword, pattern, str(args, "pageToken"), fetchSize));
                }
        );
    }

    // =========================================================================
    // subdomainTyposquatting
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification subdomainTyposquattingSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "keyword": {
                      "type": "string",
                      "minLength": 3,
                      "maxLength": 63,
                      "pattern": "^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$",
                      "description": "Brand or name to find typo variants of inside subdomains: 3 to 63 letters, digits or hyphens, one label without a dot, not starting or ending with a hyphen. Example: paypal"
                    },
                    "pattern": {
                      "type": "string",
                      "minLength": 5,
                      "maxLength": 63,
                      "description": "Wildcard search instead of keyword: '*' matches any characters and '?' exactly one, at most 2 wildcards, at least 5 other characters including 3 in a row, 63 in total. Example: payp?l*"
                    },
                    "pageToken": {
                      "type": "string",
                      "description": "nextPageToken from the previous result, to get the next page. Pass the same keyword or pattern with it."
                    },
                    "fetchSize": {
                      "type": "integer",
                      "minimum": 1,
                      "maximum": 10000,
                      "description": "Subdomains per page, 1 to 10000. Default: 100"
                    }
                  }
                }
                """;
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "subdomainTyposquatting",
                        """
                        Find subdomains, on any domain, that contain a typo variant of a brand name or match a wildcard pattern as
                        one of their labels, for example login.paypa1.example.com.
                        Phishing pages often hide a brand inside a subdomain of an unrelated domain, so these hosts never show up in
                        domainDiscovery. Use this for brand protection and phishing hunting. Provide exactly one of keyword or
                        pattern. For the subdomains of one domain use subdomainLookup. To judge how risky a result's domain is use
                        domainReputation.
                        Costs 5 credits per page (100 subdomains by default).
                        Returns each subdomain with its registrable domain, whether it is active, and its first-seen, last-seen and
                        inactive-from dates, plus the total count. When there are more, the result has a nextPageToken: pass it as
                        pageToken, with the same keyword or pattern, to get the next page.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String keyword = str(args, "keyword");
                    String pattern = str(args, "pattern");
                    if (keyword == null && pattern == null) {
                        return result("Error: Provide one of 'keyword' or 'pattern'.");
                    }
                    if (keyword != null && pattern != null) {
                        return result("Error: Provide 'keyword' or 'pattern', not both; search for each in its own call.");
                    }
                    Integer fetchSize = args.get("fetchSize") instanceof Number n ? n.intValue() : null;
                    return result(services.apply(context).subdomainTyposquats(keyword, pattern, str(args, "pageToken"), fetchSize));
                }
        );
    }

    // =========================================================================
    // domainReputation
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification domainReputationSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "domainName": {
                      "type": "string",
                      "maxLength": 253,
                      "description": "Domain to assess. Example: example.com (a URL also works)"
                    }
                  },
                  "required": ["domainName"]
                }
                """;
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "domainReputation",
                        """
                        Assess how risky a domain is, live: a verdict (safe, low_risk, suspicious, malicious or unknown) with its
                        confidence and severity, a trust score with the positive and negative signals behind it, DGA
                        (algorithmically generated name) analysis, matches in threat-intelligence feeds, email security (SPF,
                        DKIM, DMARC and mail servers), and a recommended action.
                        Use this to triage a suspicious domain from an email, a log or a domainDiscovery result before deciding
                        whether to block it. For the domain's owner use liveWhoisLookup, for its certificate sslLookup, and for an
                        IP address ipSecurity.
                        Costs 5 credits. An unknown or unregistered domain still returns an assessment; lookups that failed are
                        listed under errors and only leave out their signals.
                        """,
                        schema),
                (context, request) -> {
                    Map<String, Object> args = request.arguments() != null ? request.arguments() : Map.of();
                    String domain = str(args, "domainName");
                    if (domain == null) return result("Error: 'domainName' is required.");
                    return result(services.apply(context).domainReputation(domain));
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
