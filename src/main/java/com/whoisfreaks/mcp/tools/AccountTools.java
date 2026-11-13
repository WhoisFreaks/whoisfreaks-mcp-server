package com.whoisfreaks.mcp.tools;

import com.whoisfreaks.mcp.WhoisFreaksService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.function.Function;

/**
 * MCP tools for the WhoisFreaks account behind the API key.
 *
 * Tools:
 *   apiKeyUsage — Credits and subscription requests used by the API key
 *
 * The API's key rotation endpoint is left out on purpose: it would replace the key this server is
 * using with a new one and hand that key to the AI client. Rotate keys in the billing dashboard.
 */
public class AccountTools {

    private final Function<McpTransportContext, WhoisFreaksService> services;

    public AccountTools(Function<McpTransportContext, WhoisFreaksService> services) {
        this.services = services;
    }

    public List<McpStatelessServerFeatures.SyncToolSpecification> specs() {
        return List.of(apiKeyUsageSpec());
    }

    // =========================================================================
    // apiKeyUsage
    // =========================================================================

    private McpStatelessServerFeatures.SyncToolSpecification apiKeyUsageSpec() {
        String schema = """
                {
                  "type": "object",
                  "properties": {}
                }
                """;
        return new McpStatelessServerFeatures.SyncToolSpecification(
                new McpSchema.Tool(
                        "apiKeyUsage",
                        """
                        Show how much of the WhoisFreaks API key's allowance is used: pay-as-you-go credits bought and used, and
                        the subscription's status, request limit, requests used and surcharge requests.
                        Use this before a large job (a bulk lookup, many pages of reverse WHOIS or typosquatting results) to check
                        there is enough left, or when a lookup fails with a plan or credit limit error.
                        Returns the key (masked), its credits and its subscription usage; a part the account does not have is left
                        out.
                        """,
                        schema),
                (context, request) -> result(services.apply(context).apiKeyUsage())
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private McpSchema.CallToolResult result(String text) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(text)), false);
    }
}
