package com.whoisfreaks.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP client for the WhoisFreaks public REST API (api.whoisfreaks.com), following the OpenAPI spec
 * published at whoisfreaks.com (version 2026-11-13.3).
 *
 * All methods return a formatted String suitable for MCP tool responses.
 * Uses Java 11+ built-in HttpClient — no extra dependencies required.
 */
public class WhoisFreaksService {

    private static final String BASE = "https://api.whoisfreaks.com";

    // Sent with every request so the API can tell that it comes from the MCP server: the caller's
    // own IP address never reaches the API through this server, only this machine's does.
    private static final String APP_PARAM = "&app=mcp-server";

    /** Most items a bulk endpoint takes in one request. */
    public static final int BULK_LIMIT = 100;

    // A single lookup can wait up to a minute on slow upstream WHOIS and DNS servers; a bulk request
    // runs up to 100 of them, and a domain typosquatting search can take two minutes.
    private static final Duration SINGLE_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration LONG_TIMEOUT = Duration.ofSeconds(240);

    // Shared across instances: in HTTP mode a lightweight service is created per request (one per user API key).
    private static final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;

    public WhoisFreaksService(String apiKey) {
        this.apiKey = apiKey;
    }

    // =========================================================================
    // WHOIS APIs  (/v2.0/whois/live, /v2.0/whois/history, /v2.0/whois/reverse, /v2.0/bulkwhois/live)
    // =========================================================================

    /** Live WHOIS lookup for a domain. */
    public String liveWhois(String domainName, String format) {
        String url = BASE + "/v2.0/whois/live"
                + "?apiKey=" + enc(apiKey)
                + "&domainName=" + enc(domainName)
                + "&format=" + def(format, "json");
        return get("Live WHOIS: " + domainName, url, format);
    }

    /** Historical WHOIS lookup for a domain. Not paginated: every record comes in one response. */
    public String whoisHistory(String domainName, String format) {
        String url = BASE + "/v2.0/whois/history"
                + "?apiKey=" + enc(apiKey)
                + "&domainName=" + enc(domainName)
                + "&format=" + def(format, "json");
        return get("WHOIS History: " + domainName, url, format);
    }

    /**
     * Reverse WHOIS lookup.
     * Exactly one of keyword/email/owner/company must be provided.
     */
    public String reverseWhois(String keyword, String email, String owner, String company,
                               String mode, Boolean exact, Integer page, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v2.0/whois/reverse"
                + "?apiKey=" + enc(apiKey));

        if (notBlank(keyword)) url.append("&keyword=").append(enc(keyword));
        if (notBlank(email))   url.append("&email=").append(enc(email));
        if (notBlank(owner))   url.append("&owner=").append(enc(owner));
        if (notBlank(company)) url.append("&company=").append(enc(company));
        if (notBlank(mode))    url.append("&mode=").append(enc(mode));
        if (exact != null)     url.append("&exact=").append(exact);
        if (page != null)      url.append("&page=").append(page);
        url.append("&format=").append(def(format, "json"));

        String label = email != null ? email : keyword != null ? keyword : owner != null ? owner : company;
        return get("Reverse WHOIS: " + label, url.toString(), format);
    }

    /** Live WHOIS for up to 100 domains in one request. */
    public String bulkWhois(List<String> domainNames, String format) {
        String url = BASE + "/v2.0/bulkwhois/live"
                + "?apiKey=" + enc(apiKey)
                + "&format=" + def(format, "json");
        return post("Bulk WHOIS: " + count(domainNames, "domain"), url, Map.of("domainNames", domainNames), format);
    }

    // =========================================================================
    // IP WHOIS API  (/v1.0/ip-whois)
    // =========================================================================

    /** WHOIS lookup for an IP address. */
    public String ipWhois(String ip, String format) {
        String url = BASE + "/v1.0/ip-whois"
                + "?apiKey=" + enc(apiKey)
                + "&ip=" + enc(ip)
                + "&format=" + def(format, "json");
        return get("IP WHOIS: " + ip, url, format);
    }

    // =========================================================================
    // ASN WHOIS API  (/v2.0/asn-whois)
    // =========================================================================

    /** WHOIS lookup for an Autonomous System Number. */
    public String asnWhois(String asn, String format) {
        String url = BASE + "/v2.0/asn-whois"
                + "?apiKey=" + enc(apiKey)
                + "&asn=" + enc(asn)
                + "&format=" + def(format, "json");
        return get("ASN WHOIS: " + asn, url, format);
    }

    // =========================================================================
    // DNS APIs  (/v2.0/dns/live, /v2.0/dns/historical, /v2.1/dns/reverse, /v2.0/dns/bulk/live)
    // =========================================================================

    /**
     * Live DNS lookup of a domain, and/or a reverse (PTR) lookup of an IP address.
     * type: all, or a comma-separated list such as a,mx,ns (A, AAAA, MX, NS, CNAME, SOA, TXT, SPF)
     */
    public String dnsLookup(String domainName, String ipAddress, String type, Integer cnameDepth,
                            Boolean relativeRecords, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v2.0/dns/live"
                + "?apiKey=" + enc(apiKey));
        if (notBlank(domainName))   url.append("&domainName=").append(enc(domainName));
        if (notBlank(ipAddress))    url.append("&ipAddress=").append(enc(ipAddress));
        url.append("&type=").append(enc(def(type, "all")));
        if (cnameDepth != null)      url.append("&cnameDepth=").append(cnameDepth);
        if (relativeRecords != null) url.append("&relativeRecords=").append(relativeRecords);
        url.append("&format=").append(def(format, "json"));
        String target = notBlank(domainName) && notBlank(ipAddress) ? domainName + ", " + ipAddress
                : notBlank(domainName) ? domainName : ipAddress;
        return get("DNS Lookup [" + def(type, "all") + "]: " + target, url.toString(), format);
    }

    /**
     * Historical DNS lookup.
     * type: all, or a comma-separated list such as a,mx,ns (A, AAAA, MX, NS, CNAME, SOA, TXT)
     */
    public String dnsHistory(String domainName, String type, Integer page, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v2.0/dns/historical"
                + "?apiKey=" + enc(apiKey)
                + "&domainName=" + enc(domainName)
                + "&type=" + enc(def(type, "all")));
        if (page != null) url.append("&page=").append(page);
        url.append("&format=").append(def(format, "json"));
        return get("DNS History [" + def(type, "all") + "]: " + domainName, url.toString(), format);
    }

    /**
     * Reverse DNS lookup — search by IP address or CIDR range, MX host, NS server or other record value.
     * type: a | aaaa | mx | ns | cname | txt | soa (one type)
     * sort: asc | desc, by sortBy: firstSeen | lastSeen
     */
    public String reverseDns(String value, String type, Boolean exact, Integer page,
                             String sort, String sortBy, String format) {
        String recordType = def(type, "a").toLowerCase(Locale.ROOT);
        StringBuilder url = new StringBuilder(BASE + "/v2.1/dns/reverse"
                + "?apiKey=" + enc(apiKey)
                + "&value=" + enc(value)
                + "&type=" + enc(recordType));
        if (exact != null)    url.append("&exact=").append(exact);
        if (page != null)     url.append("&page=").append(page);
        if (notBlank(sort))   url.append("&sort=").append(enc(sort));
        if (notBlank(sortBy)) url.append("&sortBy=").append(enc(sortBy));
        url.append("&format=").append(def(format, "json"));
        return get("Reverse DNS [" + recordType.toUpperCase(Locale.ROOT) + "]: " + value, url.toString(), format);
    }

    /**
     * Live DNS for up to 100 domains and 100 IP addresses (PTR) in one request.
     * type: all, or a comma-separated list such as a,mx,ns (A, AAAA, TXT, NS, MX, SOA, SPF)
     */
    public String bulkDns(List<String> domainNames, List<String> ipAddresses, String type,
                          Integer cnameDepth, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v2.0/dns/bulk/live"
                + "?apiKey=" + enc(apiKey)
                + "&type=" + enc(def(type, "all")));
        if (cnameDepth != null) url.append("&cname_depth=").append(cnameDepth);
        url.append("&format=").append(def(format, "json"));

        Map<String, Object> body = new LinkedHashMap<>();
        if (!domainNames.isEmpty()) body.put("domainNames", domainNames);
        if (!ipAddresses.isEmpty()) body.put("ipAddresses", ipAddresses);
        String label = String.join(", ", List.of(count(domainNames, "domain"), count(ipAddresses, "IP")).stream()
                .filter(s -> !s.startsWith("0 ")).toList());
        return post("Bulk DNS [" + def(type, "all") + "]: " + label, url.toString(), body, format);
    }

    // =========================================================================
    // IP Geolocation API  (/v1.0/geolocation)
    // =========================================================================

    /** IP Geolocation lookup. */
    public String ipGeolocation(String ip) {
        String url = BASE + "/v1.0/geolocation"
                + "?apiKey=" + enc(apiKey)
                + "&ip=" + enc(ip);
        return get("IP Geolocation: " + ip, url, null);
    }

    /** IP Geolocation for up to 100 IP addresses in one request. */
    public String bulkIpGeolocation(List<String> ips) {
        String url = BASE + "/v1.0/geolocation"
                + "?apiKey=" + enc(apiKey);
        return post("Bulk IP Geolocation: " + count(ips, "IP"), url, Map.of("ips", ips), null);
    }

    // =========================================================================
    // IP Reputation API  (/v2.0/security)
    // =========================================================================

    /** IP reputation (security / threat intelligence) lookup. */
    public String ipSecurity(String ip) {
        String url = BASE + "/v2.0/security"
                + "?apiKey=" + enc(apiKey)
                + "&ip=" + enc(ip);
        return get("IP Security: " + ip, url, null);
    }

    /** IP reputation for up to 100 IP addresses in one request. */
    public String bulkIpSecurity(List<String> ips) {
        String url = BASE + "/v2.0/security"
                + "?apiKey=" + enc(apiKey);
        return post("Bulk IP Security: " + count(ips, "IP"), url, Map.of("ips", ips), null);
    }

    // =========================================================================
    // Domain Reputation API  (/v1/domain/security)
    // =========================================================================

    /** Risk verdict, trust score, DGA analysis and threat intelligence for a domain. */
    public String domainReputation(String domainName) {
        String url = BASE + "/v1/domain/security"
                + "?apiKey=" + enc(apiKey)
                + "&domainName=" + enc(domainName);
        return get("Domain Reputation: " + domainName, url, null);
    }

    // =========================================================================
    // SSL Certificate API  (/v1.0/ssl/live)
    // =========================================================================

    /** SSL certificate lookup for a domain. */
    public String sslLookup(String domainName, Boolean chain, Boolean sslRaw, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v1.0/ssl/live"
                + "?apiKey=" + enc(apiKey)
                + "&domainName=" + enc(domainName));
        if (chain != null)  url.append("&chain=").append(chain);
        if (sslRaw != null) url.append("&sslRaw=").append(sslRaw);
        url.append("&format=").append(def(format, "json"));
        return get("SSL Lookup: " + domainName, url.toString(), format);
    }

    // =========================================================================
    // Domain Availability API  (/v2.0/domain/availability)
    // =========================================================================

    /** Check if a domain is available, with optional suggestions. */
    public String domainAvailability(String domain, Boolean suggestions, Integer count, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v2.0/domain/availability"
                + "?apiKey=" + enc(apiKey)
                + "&domain=" + enc(domain));
        if (suggestions != null) url.append("&sug=").append(suggestions);
        if (count != null)       url.append("&count=").append(count);
        url.append("&format=").append(def(format, "json"));
        return get("Domain Availability: " + domain, url.toString(), format);
    }

    /**
     * Bulk availability: either up to 100 full domain names, or one domain label checked against
     * up to 100 TLDs (pass domain and tlds, and leave domainNames empty).
     */
    public String bulkDomainAvailability(List<String> domainNames, String domain, List<String> tlds, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v2.0/domain/availability"
                + "?apiKey=" + enc(apiKey));
        String title;
        Map<String, Object> body;
        if (domainNames.isEmpty()) {
            url.append("&domain=").append(enc(domain));
            body = Map.of("tld", tlds);
            title = "Bulk Domain Availability: " + domain + " across " + count(tlds, "TLD");
        } else {
            body = Map.of("domainNames", domainNames);
            title = "Bulk Domain Availability: " + count(domainNames, "domain");
        }
        url.append("&format=").append(def(format, "json"));
        return post(title, url.toString(), body, format);
    }

    // =========================================================================
    // Subdomains API  (/v1.0/subdomains)
    // =========================================================================

    /** Find subdomains for a domain. */
    public String subdomainLookup(String domain, String status, String after, String before,
                                  Integer page, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v1.0/subdomains"
                + "?apiKey=" + enc(apiKey)
                + "&domain=" + enc(domain));
        if (notBlank(status)) url.append("&status=").append(enc(status));
        if (notBlank(after))  url.append("&after=").append(enc(after));
        if (notBlank(before)) url.append("&before=").append(enc(before));
        if (page != null)     url.append("&page=").append(page);
        url.append("&format=").append(def(format, "json"));
        return get("Subdomains: " + domain, url.toString(), format);
    }

    // =========================================================================
    // Typosquatting APIs  (/v3.0/domain/typos, /v3.0/subdomains/typos)
    // =========================================================================

    /**
     * Registered domains that look like typo variants of a brand keyword, or match a wildcard pattern
     * ('*' only). Exactly one of keyword or pattern; pageToken continues from the previous page.
     */
    public String domainTyposquats(String keyword, String pattern, String pageToken, Integer fetchSize) {
        return typos("/v3.0/domain/typos", "Domain Typosquatting: ", keyword, pattern, pageToken, fetchSize);
    }

    /**
     * Subdomains on any domain whose labels contain a typo variant of a brand keyword, or match a
     * wildcard pattern ('*' and '?'). Exactly one of keyword or pattern; pageToken continues.
     */
    public String subdomainTyposquats(String keyword, String pattern, String pageToken, Integer fetchSize) {
        return typos("/v3.0/subdomains/typos", "Subdomain Typosquatting: ", keyword, pattern, pageToken, fetchSize);
    }

    private String typos(String path, String title, String keyword, String pattern, String pageToken, Integer fetchSize) {
        StringBuilder url = new StringBuilder(BASE + path
                + "?apiKey=" + enc(apiKey));
        if (notBlank(keyword))   url.append("&keyword=").append(enc(keyword));
        if (notBlank(pattern))   url.append("&pattern=").append(enc(pattern));
        if (notBlank(pageToken)) url.append("&pageToken=").append(enc(pageToken));
        if (fetchSize != null)   url.append("&fetch_size=").append(fetchSize);
        return get(title + (notBlank(keyword) ? keyword : pattern), url.toString(), null, LONG_TIMEOUT);
    }

    // =========================================================================
    // Account API  (/v1.0/whoisapi/usage)
    // =========================================================================

    /** Credits and subscription requests used by this API key. */
    public String apiKeyUsage() {
        String url = BASE + "/v1.0/whoisapi/usage"
                + "?apiKey=" + enc(apiKey);
        return get("API Key Usage", url, null);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String get(String title, String url, String format) {
        return get(title, url, format, SINGLE_TIMEOUT);
    }

    private String get(String title, String url, String format, Duration timeout) {
        return send(title, HttpRequest.newBuilder()
                .uri(URI.create(url + APP_PARAM))
                .timeout(timeout)
                .header("Accept", accept(format))
                .GET()
                .build());
    }

    private String post(String title, String url, Map<String, Object> body, String format) {
        try {
            return send(title, HttpRequest.newBuilder()
                    .uri(URI.create(url + APP_PARAM))
                    .timeout(LONG_TIMEOUT)
                    .header("Accept", accept(format))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build());
        } catch (Exception e) {
            return title + "\n\nError: " + e.getMessage();
        }
    }

    private String send(String title, HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return formatResponse(title, response.statusCode(), response.body(), response.headers());
        } catch (java.net.http.HttpTimeoutException e) {
            return title + "\n\nError: the WhoisFreaks API did not answer within "
                    + request.timeout().map(Duration::toSeconds).orElse(0L) + " seconds. Try again later.";
        } catch (Exception e) {
            return title + "\n\nError: " + e.getMessage();
        }
    }

    private String formatResponse(String title, int status, String body, HttpHeaders headers) {
        if (status >= 400) {
            return title + "\n\n" + errorMessage(status, body, headers);
        }

        // 206 and 210 carry a record, but not a complete live one.
        String note = "";
        if (status == 206) {
            note = "Partial record (HTTP 206): the registry answered but the registrar did not, so registrar-level"
                    + " details such as contacts may be missing. Retry later for the full record.\n\n";
        } else if (status == 210) {
            note = "Cached record (HTTP 210): the live registry and registrar lookups both failed, so this is the latest"
                    + " record stored in the WhoisFreaks database, not live data.\n\n";
        }

        // 200 / 206 / 210 — pretty-print JSON
        try {
            JsonNode node = mapper.readTree(body);
            return title + "\n\n" + note + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return title + "\n\n" + note + body;
        }
    }

    /** What an error status means (from the OpenAPI spec), followed by the API's own reason. */
    private String errorMessage(int status, String body, HttpHeaders headers) {
        String reason = apiMessage(body);
        String said = reason.isEmpty() ? "" : " The WhoisFreaks API said: " + reason;
        return switch (status) {
            case 400 -> "Invalid request (HTTP 400): a parameter or the request body is missing or invalid." + said;
            case 401 -> "Unauthorized (HTTP 401)." + said
                    + " The API key is invalid or inactive, or its plan, credits or surcharge limit are used up. Check your"
                    + " WhoisFreaks API key and subscription status. If the key only allows certain IP addresses, also allow"
                    + " the address these requests come from: the machine this MCP server runs on (for the hosted server,"
                    + " the IP of mcp.whoisfreaks.com), not your own.";
            case 403 -> "Not supported (HTTP 403): WhoisFreaks does not support this domain extension (TLD), or it is inactive." + said;
            case 404 -> "No data found (HTTP 404): there is no record for this request." + said;
            case 405 -> "Method not allowed (HTTP 405)." + said;
            case 408 -> "Timeout (HTTP 408): the upstream WHOIS or DNS lookup timed out. Try again later." + said;
            case 413 -> "Too large (HTTP 413): a bulk list has more than " + BULK_LIMIT + " items or the request is too big." + said;
            case 423 -> "Non-routable IP (HTTP 423): the address is in a private or reserved (bogon) range and has no public data." + said;
            case 429 -> "Rate limit reached (HTTP 429): too many requests per minute for this API key." + retryAfter(headers) + said;
            case 499 -> "Request closed early (HTTP 499): the connection was closed before the response was complete. Try again." + said;
            default -> status >= 500
                    ? "WhoisFreaks API error (HTTP " + status + "). The service is temporarily unavailable; try again later." + said
                    : "Error (HTTP " + status + ")." + (said.isEmpty() ? " " + shorten(body) : said);
        };
    }

    /** "Try again in N seconds." from the rate-limit header, which counts nanoseconds; "" when absent. */
    private static String retryAfter(HttpHeaders headers) {
        try {
            long nanos = Long.parseLong(headers.firstValue("X-RateLimit-Remaining-Time").orElse("").trim());
            long seconds = Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
            return " Try again in " + seconds + (seconds == 1 ? " second." : " seconds.");
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /** The "message" field of an API error body, or "" when there is none. */
    private String apiMessage(String body) {
        try {
            return mapper.readTree(body).path("message").asText("").trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String shorten(String body) {
        String text = body == null ? "" : body.strip();
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    private static String accept(String format) {
        return "xml".equalsIgnoreCase(format) ? "application/xml" : "application/json";
    }

    private static String count(List<String> items, String noun) {
        return items.size() + " " + noun + (items.size() == 1 ? "" : "s");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String def(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
