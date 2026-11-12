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
                        Retrieve real-time WHOIS data for any domain using the WhoisFreaks Live WHOIS API.
                        Returns complete domain registration information including:
                        - Registrar and registry details
                        - Domain creation, update, and expiry dates
                        - Registrant, admin, and technical contact information
                        - Name servers
                        - Domain status flags
                        Parameters:
                        - domainName (required): The domain to look up. Example: google.com
                        - format (optional): json (default) or xml
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
                        Retrieve the complete historical WHOIS ownership timeline for a domain using the
                        WhoisFreaks WHOIS History API.
                        Returns deduplicated WHOIS snapshots ordered from most recent to oldest, showing:
                        - All past registrant/owner changes
                        - Historical registrar transfers
                        - Previous contact information
                        - Past name server changes
                        - Domain status over time
                        Parameters:
                        - domainName (required): Domain to look up history for. Example: whoisfreaks.com
                        - format (optional): json (default) or xml
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
                        Search the WhoisFreaks WHOIS database using contact information to find all domains
                        associated with a specific email, person, company, or keyword.
                        Provide exactly ONE of: email, keyword, owner, or company.
                        Returns a list of matching domains with optional full WHOIS records.
                        Parameters:
                        - email (optional): Registrant email address. Example: admin@example.com
                        - keyword (optional): Keyword found in WHOIS records. Example: google
                        - owner (optional): Registrant/owner name. Example: John Doe
                        - company (optional): Organization name. Example: Alphabet Inc
                        - mode (optional): 'mini' (domain names only) or 'default' (full records). Default: mini
                        - exact (optional): true for exact match, false for partial/pattern match. Default: true
                        - page (optional): Page number for pagination. Default: 1
                        - format (optional): json or xml. Default: json
                        Note: Provide exactly one of email, keyword, owner, or company.
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
