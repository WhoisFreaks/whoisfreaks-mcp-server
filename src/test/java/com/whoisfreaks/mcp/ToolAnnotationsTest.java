package com.whoisfreaks.mcp;

import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The annotations and behaviour note every tool advertises, which plugin directories review: every tool only reads,
 * and every tool but apiKeyUsage, which reads the caller's own account, looks up open-ended internet data.
 */
class ToolAnnotationsTest {

    private final List<McpSchema.Tool> tools = WhoisFreaksMcpServer.toolSpecs(context -> null).stream()
            .map(McpStatelessServerFeatures.SyncToolSpecification::tool)
            .toList();

    @Test
    void everyToolIsReadOnlyAndNotDestructive() {
        assertEquals(22, tools.size());
        for (McpSchema.Tool tool : tools) {
            assertTrue(tool.annotations().readOnlyHint(), tool.name());
            assertFalse(tool.annotations().destructiveHint(), tool.name());
            assertTrue(tool.annotations().idempotentHint(), tool.name());
        }
    }

    @Test
    void onlyTheAccountToolIsClosedWorld() {
        for (McpSchema.Tool tool : tools) {
            assertEquals(!tool.name().equals("apiKeyUsage"), tool.annotations().openWorldHint(), tool.name());
        }
    }

    @Test
    void everyBehaviorNoteCoversOAuth() {
        for (McpSchema.Tool tool : tools) {
            assertTrue(tool.description().contains("or the account connected with OAuth"), tool.name());
        }
    }
}
