package com.whoisfreaks.mcp;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whoisfreaks.mcp.tools.DnsTools;
import com.whoisfreaks.mcp.tools.DomainTools;
import com.whoisfreaks.mcp.tools.IpTools;
import com.whoisfreaks.mcp.tools.IpWhoisTools;
import com.whoisfreaks.mcp.tools.SslTools;
import com.whoisfreaks.mcp.tools.WhoisTools;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * WhoisFreaks MCP Server.
 *
 * Exposes all WhoisFreaks public APIs as MCP tools:
 *   WHOIS:   liveWhoisLookup, whoisHistory, reverseWhoisLookup
 *   IP/ASN:  ipWhoisLookup, asnWhoisLookup
 *   DNS:     dnsLookup, dnsHistory, reverseDnsLookup
 *   IP:      ipGeolocation, ipSecurity
 *   Domain:  domainAvailability, subdomainLookup, domainDiscovery
 *   SSL:     sslLookup
 *
 * Two modes:
 *   stdio (default)       — single user; requires WHOISFREAKS_API_KEY environment variable
 *   HTTP  (--http flag or MCP_TRANSPORT=http)
 *                         — hosted, multi-user; every request carries the caller's own API key
 *                           (see {@link WhoisFreaksHttpServer})
 */
public class WhoisFreaksMcpServer {

    static final String SERVER_NAME = "whoisfreaks-mcp-server";
    static final String SERVER_VERSION = "2.0.0";

    public static void main(String[] args) throws Exception {
        boolean httpMode = Arrays.asList(args).contains("--http")
                || "http".equalsIgnoreCase(System.getenv("MCP_TRANSPORT"));
        if (httpMode) {
            String port = System.getenv("PORT");
            WhoisFreaksHttpServer.run(port != null && !port.isBlank() ? Integer.parseInt(port) : 3100);
        } else {
            runStdio();
        }
    }

    /** All tool specifications; {@code services} supplies the WhoisFreaksService to use for each call. */
    static List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecs(
            Function<McpTransportContext, WhoisFreaksService> services) {
        List<McpStatelessServerFeatures.SyncToolSpecification> allTools = new ArrayList<>();
        allTools.addAll(new WhoisTools(services).specs());     // liveWhoisLookup, whoisHistory, reverseWhoisLookup
        allTools.addAll(new IpWhoisTools(services).specs());   // ipWhoisLookup, asnWhoisLookup
        allTools.addAll(new DnsTools(services).specs());       // dnsLookup, dnsHistory, reverseDnsLookup
        allTools.addAll(new IpTools(services).specs());        // ipGeolocation, ipSecurity
        allTools.addAll(new DomainTools(services).specs());    // domainAvailability, subdomainLookup, domainDiscovery
        allTools.addAll(new SslTools(services).specs());       // sslLookup
        return allTools;
    }

    static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private static void runStdio() {
        String apiKey = System.getenv("WHOISFREAKS_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("[whoisfreaks-mcp] ERROR: WHOISFREAKS_API_KEY environment variable is not set.");
            System.err.println("[whoisfreaks-mcp] Obtain your key from https://whoisfreaks.com/billing");
            System.exit(1);
        }

        WhoisFreaksService service = new WhoisFreaksService(apiKey);
        List<McpStatelessServerFeatures.SyncToolSpecification> allTools = toolSpecs(context -> service);
        System.err.println("[whoisfreaks-mcp] Registered " + allTools.size() + " tools.");

        // Tools are written for the stateless (HTTP) server; adapt them to the stdio session server.
        List<McpServerFeatures.SyncToolSpecification> stdioTools = new ArrayList<>();
        for (McpStatelessServerFeatures.SyncToolSpecification spec : allTools) {
            stdioTools.add(McpServerFeatures.SyncToolSpecification.builder()
                    .tool(spec.tool())
                    .callHandler((exchange, request) -> spec.callHandler().apply(exchange.transportContext(), request))
                    .build());
        }

        StdioServerTransportProvider transport = new StdioServerTransportProvider(objectMapper());

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(stdioTools)
                .build();

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
