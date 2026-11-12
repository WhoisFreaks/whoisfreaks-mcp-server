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
import java.util.Map;
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
 *   stdio (default)       — single user; the API key comes from the WHOISFREAKS_API_KEY
 *                           environment variable. Without it the server still starts and
 *                           lists its tools, but tool calls return an error.
 *   HTTP  (--http flag or MCP_TRANSPORT=http)
 *                         — hosted, multi-user; every request carries the caller's own API key
 *                           (see {@link WhoisFreaksHttpServer})
 */
public class WhoisFreaksMcpServer {

    static final String SERVER_NAME = "whoisfreaks-mcp-server";

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

    /**
     * All tool specifications, with their titles, annotations and the shared behaviour note;
     * {@code services} supplies the WhoisFreaksService to use for each call.
     */
    static List<McpStatelessServerFeatures.SyncToolSpecification> toolSpecs(
            Function<McpTransportContext, WhoisFreaksService> services) {
        List<McpStatelessServerFeatures.SyncToolSpecification> allTools = new ArrayList<>();
        allTools.addAll(new WhoisTools(services).specs());     // liveWhoisLookup, whoisHistory, reverseWhoisLookup
        allTools.addAll(new IpWhoisTools(services).specs());   // ipWhoisLookup, asnWhoisLookup
        allTools.addAll(new DnsTools(services).specs());       // dnsLookup, dnsHistory, reverseDnsLookup
        allTools.addAll(new IpTools(services).specs());        // ipGeolocation, ipSecurity
        allTools.addAll(new DomainTools(services).specs());    // domainAvailability, subdomainLookup, domainDiscovery
        allTools.addAll(new SslTools(services).specs());       // sslLookup
        return allTools.stream().map(WhoisFreaksMcpServer::withBehaviorInfo).toList();
    }

    static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Version reported to MCP clients. Read from the jar manifest
     * (Implementation-Version, set from pom.xml by the shade plugin) so it
     * always matches the released version. Falls back to "dev" when running
     * from an IDE or unpackaged classes.
     */
    static String serverVersion() {
        String version = WhoisFreaksMcpServer.class.getPackage().getImplementationVersion();
        return (version == null || version.isBlank()) ? "dev" : version;
    }

    private static void runStdio() {
        // Start even without a key, so MCP clients and registries (e.g. Glama)
        // can still list the tools. Tool calls then return a clear error.
        String apiKey = System.getenv("WHOISFREAKS_API_KEY");
        boolean hasApiKey = apiKey != null && !apiKey.isBlank();
        if (!hasApiKey) {
            System.err.println("[whoisfreaks-mcp] WARNING: WHOISFREAKS_API_KEY environment variable is not set.");
            System.err.println("[whoisfreaks-mcp] Tools are listed, but every tool call will return an error until the key is set.");
            System.err.println("[whoisfreaks-mcp] Get a free key at https://billing.whoisfreaks.com/signup");
        }

        WhoisFreaksService service = new WhoisFreaksService(hasApiKey ? apiKey : "");
        List<McpStatelessServerFeatures.SyncToolSpecification> allTools = toolSpecs(context -> service);
        if (!hasApiKey) {
            allTools = allTools.stream().map(WhoisFreaksMcpServer::requireApiKey).toList();
        }
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
                .serverInfo(SERVER_NAME, serverVersion())
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

    private static final String MISSING_API_KEY_MESSAGE =
            "WHOISFREAKS_API_KEY is not set. Get a free API key at https://billing.whoisfreaks.com/signup, "
            + "add it as the WHOISFREAKS_API_KEY environment variable in your MCP client config, "
            + "then restart the server.";

    /** Behaviour shared by every tool, appended to each description. */
    private static final String BEHAVIOR_NOTE = """

            Behavior: read-only. Makes one request to the WhoisFreaks API using your WhoisFreaks API key \
            (the WHOISFREAKS_API_KEY environment variable, or on the hosted server the key sent with the \
            request) and uses that account's API credits; nothing is created or changed. \
            If the call fails, the result is a short message instead of data, for example: key missing \
            or invalid, no data found (HTTP 404), rate limit reached (HTTP 429), or plan or credit limit \
            reached (HTTP 412/413).""";

    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("liveWhoisLookup", "Live WHOIS lookup"),
            Map.entry("whoisHistory", "WHOIS history"),
            Map.entry("reverseWhoisLookup", "Reverse WHOIS search"),
            Map.entry("ipWhoisLookup", "IP WHOIS lookup"),
            Map.entry("asnWhoisLookup", "ASN WHOIS lookup"),
            Map.entry("dnsLookup", "DNS lookup"),
            Map.entry("dnsHistory", "DNS history"),
            Map.entry("reverseDnsLookup", "Reverse DNS search"),
            Map.entry("ipGeolocation", "IP geolocation"),
            Map.entry("ipSecurity", "IP security check"),
            Map.entry("domainAvailability", "Domain availability check"),
            Map.entry("subdomainLookup", "Subdomain lookup"),
            Map.entry("domainDiscovery", "Domain discovery search"),
            Map.entry("sslLookup", "SSL certificate lookup"));

    /**
     * Adds a human-readable title, MCP tool annotations (read-only, non-destructive,
     * calls an external API) and the shared behaviour note to a tool definition.
     */
    private static McpStatelessServerFeatures.SyncToolSpecification withBehaviorInfo(
            McpStatelessServerFeatures.SyncToolSpecification spec) {
        McpSchema.Tool tool = spec.tool();
        String title = TITLES.getOrDefault(tool.name(), tool.name());
        McpSchema.Tool annotated = McpSchema.Tool.builder()
                .name(tool.name())
                .title(title)
                .description(tool.description().strip() + BEHAVIOR_NOTE)
                .inputSchema(tool.inputSchema())
                .annotations(new McpSchema.ToolAnnotations(title, true, false, true, true, null))
                .build();
        return new McpStatelessServerFeatures.SyncToolSpecification(annotated, spec.callHandler());
    }

    /**
     * Keeps the tool's definition (name, description, input schema) so it is
     * still listed, but makes every call return an error explaining that the
     * API key is missing instead of calling the WhoisFreaks API.
     */
    private static McpStatelessServerFeatures.SyncToolSpecification requireApiKey(
            McpStatelessServerFeatures.SyncToolSpecification spec) {
        return new McpStatelessServerFeatures.SyncToolSpecification(spec.tool(),
                (context, request) -> new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent(MISSING_API_KEY_MESSAGE)), true));
    }
}
