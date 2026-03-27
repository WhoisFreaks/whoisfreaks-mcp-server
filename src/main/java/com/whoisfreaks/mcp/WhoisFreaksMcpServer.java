package com.whoisfreaks.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whoisfreaks.mcp.tools.DnsTools;
import com.whoisfreaks.mcp.tools.DomainTools;
import com.whoisfreaks.mcp.tools.IpTools;
import com.whoisfreaks.mcp.tools.IpWhoisTools;
import com.whoisfreaks.mcp.tools.SslTools;
import com.whoisfreaks.mcp.tools.WhoisTools;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * WhoisFreaks MCP Server — Pure Java STDIO transport.
 *
 * Exposes all WhoisFreaks public APIs as MCP tools:
 *   WHOIS:   liveWhoisLookup, whoisHistory, reverseWhoisLookup
 *   IP/ASN:  ipWhoisLookup, asnWhoisLookup
 *   DNS:     dnsLookup, dnsHistory, reverseDnsLookup
 *   IP:      ipGeolocation, ipSecurity
 *   Domain:  domainAvailability, subdomainLookup, domainDiscovery
 *   SSL:     sslLookup
 *
 * Required environment variable:
 *   WHOISFREAKS_API_KEY  — Your WhoisFreaks API key (from billing dashboard)
 */
public class WhoisFreaksMcpServer {

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("WHOISFREAKS_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[whoisfreaks-mcp] ERROR: WHOISFREAKS_API_KEY environment variable is not set.");
            System.err.println("[whoisfreaks-mcp] Obtain your key from https://whoisfreaks.com/billing");
            System.exit(1);
        }

        WhoisFreaksService service = new WhoisFreaksService(apiKey);

        // Collect all tool specifications from every category
        List<McpServerFeatures.SyncToolSpecification> allTools = new ArrayList<>();
        allTools.addAll(new WhoisTools(service).specs());     // liveWhoisLookup, whoisHistory, reverseWhoisLookup
        allTools.addAll(new IpWhoisTools(service).specs());   // ipWhoisLookup, asnWhoisLookup
        allTools.addAll(new DnsTools(service).specs());       // dnsLookup, dnsHistory, reverseDnsLookup
        allTools.addAll(new IpTools(service).specs());        // ipGeolocation, ipSecurity
        allTools.addAll(new DomainTools(service).specs());    // domainAvailability, subdomainLookup, domainDiscovery
        allTools.addAll(new SslTools(service).specs());       // sslLookup

        System.err.println("[whoisfreaks-mcp] Registered " + allTools.size() + " tools.");

        StdioServerTransportProvider transport = new StdioServerTransportProvider(new ObjectMapper());

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("whoisfreaks-mcp-server", "2.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .build();

        for (McpServerFeatures.SyncToolSpecification spec : allTools) {
            server.addTool(spec);
        }

        System.err.println("[whoisfreaks-mcp] Server started. Listening on stdio.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("[whoisfreaks-mcp] Shutting down.");
            try { server.close(); } catch (Exception ignored) {}
        }));

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
