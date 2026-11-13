package com.whoisfreaks.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.whoisfreaks.mcp.oauth.AccessTokenVerifier;
import com.whoisfreaks.mcp.oauth.BillingApiKeys;
import com.whoisfreaks.mcp.oauth.OAuthFilter;
import com.whoisfreaks.mcp.oauth.OAuthSettings;
import com.whoisfreaks.mcp.oauth.ProtectedResourceMetadataServlet;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hosted, multi-user MCP server over Streamable HTTP (stateless).
 *
 * Endpoints:
 *   POST /mcp     — MCP JSON-RPC endpoint
 *   POST /        — the same endpoint, so the bare host name works as the server URL
 *   GET  /        — a short description of the server, for someone opening it in a browser
 *   GET  /health  — liveness check, returns {"status":"ok"}
 *
 * Every tool call must carry the caller's own WhoisFreaks API key, in one of:
 *   Authorization: Bearer <key>      (Claude Code, Cursor, VS Code, ...)
 *   X-API-Key: <key>
 *   ?apiKey=<key> query parameter    (clients that cannot send headers, e.g. claude.ai custom connectors)
 *
 * The key is forwarded to api.whoisfreaks.com for that request only; it is never stored or logged.
 *
 * Connecting and listing tools work without a key; a tool call without one returns an error that
 * tells the user how to add it. (Answering 401 instead makes MCP clients start an OAuth flow that
 * this server does not offer, which hides the real problem from the user.)
 *
 * OAuth mode (--oauth, or MCP_AUTH=oauth) is the other way round, for the WhoisFreaks plugin in
 * ChatGPT and Codex, which can only sign users in with OAuth. It runs on its own host name, so
 * clients sending API keys never get its 401s:
 *   - every request needs an access token from the authorization server, else 401 with a challenge
 *     ({@link OAuthFilter}); the token is traded for the account's API key at billing
 *   - GET /.well-known/oauth-protected-resource (plus MCP_RESOURCE's path, if any) describes it (RFC 9728)
 * API keys sent the other ways are ignored. Settings come from the environment: see {@link OAuthSettings}.
 */
public class WhoisFreaksHttpServer {

    private static final String API_KEY = "whoisfreaksApiKey";
    // OAuth mode: why the connected account can't run lookups, or "".
    private static final String KEY_PROBLEM = "whoisfreaksKeyProblem";
    private static final String MCP_PATH = "/mcp";
    // The servlet mapping for exactly the context root ("/"), which serves the same endpoint.
    private static final String ROOT_MAPPING = "";
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    // Requests the stateless server handles; anything else gets a JSON-RPC "Method not found".
    private static final Set<String> METHODS = Set.of("initialize", "ping", "tools/list", "tools/call");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String MISSING_KEY = """
            No WhoisFreaks API key was sent with this request, so the lookup was not run.
            Add your key to this MCP server's configuration in one of these ways, then reconnect:
            - an 'Authorization: Bearer <key>' header
            - an 'X-API-Key: <key>' header
            - an '?apiKey=<key>' query parameter on the server URL
            Get a free key at https://billing.whoisfreaks.com/signup""";

    /** Serves until the process stops; {@code oauth} is null for the API-key mode. */
    public static void run(int port, OAuthSettings oauth) throws Exception {
        start(port, oauth).join();
    }

    static Server start(int port, OAuthSettings oauth) throws Exception {
        HttpServletStatelessServerTransport transport = HttpServletStatelessServerTransport.builder()
                .objectMapper(WhoisFreaksMcpServer.objectMapper())
                .messageEndpoint(MCP_PATH)
                .contextExtractor(request -> oauth == null
                        ? McpTransportContext.create(Map.of(API_KEY, extractApiKey(request)))
                        : McpTransportContext.create(Map.of(
                                API_KEY, attribute(request, OAuthFilter.API_KEY_ATTRIBUTE),
                                KEY_PROBLEM, attribute(request, OAuthFilter.KEY_PROBLEM_ATTRIBUTE))))
                .build();

        List<McpStatelessServerFeatures.SyncToolSpecification> tools = WhoisFreaksMcpServer
                .toolSpecs(context -> new WhoisFreaksService((String) context.get(API_KEY)))
                .stream()
                .map(oauth == null ? WhoisFreaksHttpServer::requireApiKey : WhoisFreaksHttpServer::requireUsableAccount)
                .toList();

        McpStatelessSyncServer mcp = McpServer.sync(transport)
                .serverInfo(WhoisFreaksMcpServer.SERVER_NAME, WhoisFreaksMcpServer.serverVersion())
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(tools)
                .build();

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");

        ServletHolder mcpHolder = new ServletHolder(transport);
        mcpHolder.setAsyncSupported(true);
        context.addServlet(mcpHolder, MCP_PATH);
        context.addServlet(mcpHolder, ROOT_MAPPING);
        context.addServlet(new ServletHolder(new HealthServlet()), "/health");

        FilterHolder mcpFilter = new FilterHolder(new McpFilter(oauth != null));
        mcpFilter.setAsyncSupported(true);
        context.addFilter(mcpFilter, MCP_PATH, EnumSet.of(DispatcherType.REQUEST));
        context.addFilter(mcpFilter, ROOT_MAPPING, EnumSet.of(DispatcherType.REQUEST));

        if (oauth != null) {
            // After McpFilter, so its answers get the CORS headers and preflights skip the token check.
            FilterHolder oauthFilter = new FilterHolder(
                    new OAuthFilter(new AccessTokenVerifier(oauth), new BillingApiKeys(oauth), oauth));
            oauthFilter.setAsyncSupported(true);
            context.addFilter(oauthFilter, MCP_PATH, EnumSet.of(DispatcherType.REQUEST));
            context.addFilter(oauthFilter, ROOT_MAPPING, EnumSet.of(DispatcherType.REQUEST));
            context.addServlet(new ServletHolder(new ProtectedResourceMetadataServlet(oauth)), oauth.metadataPath());
        }

        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(false);
        Server jetty = new Server();
        ServerConnector connector = new ServerConnector(jetty, new HttpConnectionFactory(httpConfig));
        connector.setPort(port);
        jetty.addConnector(connector);
        jetty.setHandler(context);
        jetty.setStopAtShutdown(true);
        jetty.start();

        System.err.println("[whoisfreaks-mcp] Registered " + tools.size() + " tools.");
        System.err.println("[whoisfreaks-mcp] Server started. Listening on http://0.0.0.0:" + connector.getLocalPort()
                + MCP_PATH + " (and on / as well)");
        if (oauth != null) {
            System.err.println("[whoisfreaks-mcp] OAuth mode: resource " + oauth.resource() + ", issuer " + oauth.issuer()
                    + ", metadata at " + oauth.metadataUrl());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("[whoisfreaks-mcp] Shutting down.");
            try { mcp.close(); } catch (Exception ignored) {}
        }));

        return jetty;
    }

    /** Makes a tool answer with setup instructions instead of calling the API when the request has no key. */
    private static McpStatelessServerFeatures.SyncToolSpecification requireApiKey(
            McpStatelessServerFeatures.SyncToolSpecification spec) {
        return new McpStatelessServerFeatures.SyncToolSpecification(spec.tool(), (context, request) -> {
            if (((String) context.get(API_KEY)).isEmpty()) {
                return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(MISSING_KEY)), true);
            }
            return spec.callHandler().apply(context, request);
        });
    }

    /** OAuth mode: makes a tool say why the connected account can't run lookups instead of calling the API. */
    private static McpStatelessServerFeatures.SyncToolSpecification requireUsableAccount(
            McpStatelessServerFeatures.SyncToolSpecification spec) {
        return new McpStatelessServerFeatures.SyncToolSpecification(spec.tool(), (context, request) -> {
            String problem = (String) context.get(KEY_PROBLEM);
            if (!problem.isEmpty()) {
                return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(problem)), true);
            }
            return spec.callHandler().apply(context, request);
        });
    }

    private static String attribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value instanceof String text ? text : "";
    }

    /** Returns the caller's API key from the Authorization header, X-API-Key header, or apiKey query parameter. */
    static String extractApiKey(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7) && !auth.substring(7).isBlank()) {
            return auth.substring(7).trim();
        }
        String header = request.getHeader("X-API-Key");
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String query = request.getParameter("apiKey");
        if (query != null && !query.isBlank()) {
            return query.trim();
        }
        return "";
    }

    /**
     * Adds CORS headers (for browser-based MCP clients) and keeps error responses clean: unknown methods get a JSON-RPC error, and SDK errors
     * (which would otherwise be serialized with their Java stack trace) are reduced to their message.
     */
    static class McpFilter implements Filter {

        private final boolean oauth;

        McpFilter(boolean oauth) {
            this.oauth = oauth;
        }

        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;

            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers",
                    "Authorization, Content-Type, Accept, X-API-Key, Mcp-Session-Id, Mcp-Protocol-Version, Last-Event-ID");
            // OAuth mode: browser-based clients read the challenge from WWW-Authenticate.
            response.setHeader("Access-Control-Expose-Headers", oauth ? "Mcp-Session-Id, WWW-Authenticate" : "Mcp-Session-Id");

            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }

            // Someone opening the bare host name in a browser gets a description instead of an error.
            // An MCP client asking for an event stream still gets the 405 it expects.
            String accept = request.getHeader("Accept");
            if ("GET".equalsIgnoreCase(request.getMethod()) && "/".equals(request.getRequestURI())
                    && (accept == null || !accept.contains("text/event-stream"))) {
                ObjectNode info = JSON.createObjectNode();
                info.put("name", WhoisFreaksMcpServer.SERVER_NAME);
                info.put("version", WhoisFreaksMcpServer.serverVersion());
                if (oauth) {
                    info.put("description", "WhoisFreaks MCP server for the WhoisFreaks plugin in ChatGPT and Codex. "
                            + "It signs users in with OAuth: install the plugin and connect your WhoisFreaks account.");
                    info.put("transport", "streamable-http");
                    info.put("documentation", "https://github.com/WhoisFreaks/whoisfreaks-mcp-server");
                } else {
                    info.put("description", "WhoisFreaks MCP server. Add this URL to an MCP client and send your "
                            + "WhoisFreaks API key as 'Authorization: Bearer <key>', an 'X-API-Key' header, "
                            + "or an '?apiKey=' query parameter.");
                    info.put("transport", "streamable-http");
                    info.put("documentation", "https://github.com/WhoisFreaks/whoisfreaks-mcp-server");
                    info.put("apiKey", "https://billing.whoisfreaks.com/signup");
                }
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(JSON.writeValueAsString(info));
                return;
            }

            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                response.setHeader("Allow", "POST, OPTIONS");
                writeError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, null, -32600,
                        "Method not allowed. This server is stateless: send JSON-RPC requests with POST.");
                return;
            }

            byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, null, -32600, "Request body too large.");
                return;
            }

            JsonNode message;
            try {
                message = JSON.readTree(body);
            } catch (IOException e) {
                message = null;
            }
            if (message == null || !message.isObject()) {
                writeError(response, HttpServletResponse.SC_BAD_REQUEST, null, -32700,
                        "Parse error: body must be a single JSON-RPC message.");
                return;
            }
            JsonNode id = message.get("id");
            String method = message.path("method").asText("");
            if (id != null && !id.isNull() && !METHODS.contains(method)) {
                writeError(response, HttpServletResponse.SC_OK, id, -32601, "Method not found: " + method);
                return;
            }

            BufferedResponse buffered = new BufferedResponse(response);
            chain.doFilter(new CachedBodyRequest(request, body), buffered);

            String out = buffered.body();
            if (buffered.getStatus() >= 400 && out.contains("\"stackTrace\"")) {
                String detail;
                try {
                    detail = JSON.readTree(out).path("message").asText("Request failed.");
                } catch (IOException e) {
                    detail = "Request failed.";
                }
                boolean serverError = buffered.getStatus() >= 500;
                writeError(response, buffered.getStatus(), id, serverError ? -32603 : -32600, detail);
            } else if (!out.isEmpty()) {
                response.getWriter().write(out);
            }
        }

        private static void writeError(HttpServletResponse response, int status, JsonNode id, int code, String message)
                throws IOException {
            ObjectNode error = JSON.createObjectNode();
            error.put("jsonrpc", "2.0");
            error.set("id", id != null ? id : JSON.nullNode());
            error.putObject("error").put("code", code).put("message", message);
            response.setStatus(status);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(JSON.writeValueAsString(error));
        }
    }

    /**
     * Replays a request body that the filter has already read, and presents every request as one
     * for MCP_PATH: the SDK transport only answers requests whose URI ends with its endpoint, and
     * the same endpoint is also served at the root.
     */
    static class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public String getRequestURI() {
            return MCP_PATH;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return in.read(); }
                @Override public int read(byte[] b, int off, int len) { return in.read(b, off, len); }
                @Override public boolean isFinished() { return in.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }

    /** Holds the response body in memory so the filter can inspect it before it is sent. */
    static class BufferedResponse extends HttpServletResponseWrapper {
        private final StringWriter buffer = new StringWriter();
        private final PrintWriter writer = new PrintWriter(buffer);

        BufferedResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public PrintWriter getWriter() {
            return writer;
        }

        @Override
        public void flushBuffer() {
            // Nothing is sent until the filter has looked at the body.
        }

        String body() {
            writer.flush();
            return buffer.toString();
        }
    }

    static class HealthServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"ok\"}");
        }
    }
}
