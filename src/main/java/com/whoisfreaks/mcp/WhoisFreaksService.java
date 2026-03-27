package com.whoisfreaks.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * HTTP client for the WhoisFreaks public REST API (api.whoisfreaks.com).
 *
 * All methods return a formatted String suitable for MCP tool responses.
 * Uses Java 11+ built-in HttpClient — no extra dependencies required.
 */
public class WhoisFreaksService {

    private static final String BASE = "https://api.whoisfreaks.com";

    private final String apiKey;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public WhoisFreaksService(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.mapper = new ObjectMapper();
    }

    // =========================================================================
    // WHOIS APIs  (/v1/whois)
    // =========================================================================

    /** Live WHOIS lookup for a domain. */
    public String liveWhois(String domainName, String format) {
        String url = BASE + "/v1.0/whois"
                + "?apiKey=" + enc(apiKey)
                + "&whois=live"
                + "&domainName=" + enc(domainName)
                + "&format=" + def(format, "json");
        return call("Live WHOIS: " + domainName, url);
    }

    /** Historical WHOIS lookup for a domain. */
    public String whoisHistory(String domainName, String format) {
        String url = BASE + "/v1.0/whois"
                + "?apiKey=" + enc(apiKey)
                + "&whois=historical"
                + "&domainName=" + enc(domainName)
                + "&format=" + def(format, "json");
        return call("WHOIS History: " + domainName, url);
    }

    /**
     * Reverse WHOIS lookup.
     * Exactly one of keyword/email/owner/company must be provided.
     */
    public String reverseWhois(String keyword, String email, String owner, String company,
                               String mode, Boolean exact, Integer page, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v1.0/whois"
                + "?apiKey=" + enc(apiKey)
                + "&whois=reverse");

        if (notBlank(keyword)) url.append("&keyword=").append(enc(keyword));
        if (notBlank(email))   url.append("&email=").append(enc(email));
        if (notBlank(owner))   url.append("&owner=").append(enc(owner));
        if (notBlank(company)) url.append("&company=").append(enc(company));
        if (notBlank(mode))    url.append("&mode=").append(enc(mode));
        if (exact != null)     url.append("&exact=").append(exact);
        if (page != null)      url.append("&page=").append(page);
        url.append("&format=").append(def(format, "json"));

        String label = email != null ? email : keyword != null ? keyword : owner != null ? owner : company;
        return call("Reverse WHOIS: " + label, url.toString());
    }

    // =========================================================================
    // IP WHOIS API  (/v1/whois/ip)
    // =========================================================================

    /** WHOIS lookup for an IP address. */
    public String ipWhois(String ip, String format) {
        String url = BASE + "/v1.0/ip-whois"
                + "?apiKey=" + enc(apiKey)
                + "&ip=" + enc(ip)
                + "&format=" + def(format, "json");
        return call("IP WHOIS: " + ip, url);
    }

    // =========================================================================
    // ASN WHOIS API  (/v2/asn/lookup)
    // =========================================================================

    /** WHOIS lookup for an Autonomous System Number. */
    public String asnWhois(String asn, String format) {
        String url = BASE + "/v2.0/asn-whois"
                + "?apiKey=" + enc(apiKey)
                + "&asn=" + enc(asn)
                + "&format=" + def(format, "json");
        return call("ASN WHOIS: " + asn, url);
    }

    // =========================================================================
    // DNS APIs  (/v2/dns, /v2/dns/historical, /v2.1/dns/reverse)
    // =========================================================================

    /**
     * Live DNS lookup.
     * type: A | AAAA | MX | NS | CNAME | SOA | TXT | SPF | all
     */
    public String dnsLookup(String domainName, String type, String format) {
        String url = BASE + "/v2.0/dns/live"
                + "?apiKey=" + enc(apiKey)
                + "&domainName=" + enc(domainName)
                + "&type=" + enc(def(type, "all"))
                + "&format=" + def(format, "json");
        return call("DNS Lookup [" + def(type, "all") + "]: " + domainName, url);
    }

    /**
     * Historical DNS lookup.
     * type: A | AAAA | MX | NS | CNAME | SOA | TXT | SPF | all
     */
    public String dnsHistory(String domainName, String type, Integer page, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v2.0/dns/historical"
                + "?apiKey=" + enc(apiKey)
                + "&domainName=" + enc(domainName)
                + "&type=" + enc(def(type, "all")));
        if (page != null) url.append("&page=").append(page);
        url.append("&format=").append(def(format, "json"));
        return call("DNS History [" + def(type, "all") + "]: " + domainName, url.toString());
    }

    /**
     * Reverse DNS lookup — search by IP address, MX host, or NS server.
     * type: A | AAAA | MX | NS | CNAME | SOA | TXT
     */
    public String reverseDns(String value, String type, Boolean exact, Integer page, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v2.1/dns/reverse"
                + "?apiKey=" + enc(apiKey)
                + "&value=" + enc(value)
                + "&type=" + enc(def(type, "A")));
        if (exact != null) url.append("&exact=").append(exact);
        if (page != null)  url.append("&page=").append(page);
        url.append("&format=").append(def(format, "json"));
        return call("Reverse DNS [" + def(type, "A") + "]: " + value, url.toString());
    }

    // =========================================================================
    // IP Geolocation API  (/v1/ip/geolocation)
    // =========================================================================

    /** IP Geolocation lookup. */
    public String ipGeolocation(String ip) {
        String url = BASE + "/v1.0/geolocation"
                + "?apiKey=" + enc(apiKey)
                + "&ip=" + enc(ip);
        return call("IP Geolocation: " + ip, url);
    }

    // =========================================================================
    // IP Security API  (/v1/ip-security)
    // =========================================================================

    /** IP Security / threat intelligence lookup. */
    public String ipSecurity(String ip) {
        String url = BASE + "/v1.0/security"
                + "?apiKey=" + enc(apiKey)
                + "&ip=" + enc(ip);
        return call("IP Security: " + ip, url);
    }

    // =========================================================================
    // SSL Certificate API  (/v1/ssl)
    // =========================================================================

    /** SSL certificate lookup for a domain. */
    public String sslLookup(String domainName, Boolean chain, Boolean sslRaw, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v1.0/ssl/live"
                + "?apiKey=" + enc(apiKey)
                + "&domainName=" + enc(domainName));
        if (chain != null)  url.append("&chain=").append(chain);
        if (sslRaw != null) url.append("&sslRaw=").append(sslRaw);
        url.append("&format=").append(def(format, "json"));
        return call("SSL Lookup: " + domainName, url.toString());
    }

    // =========================================================================
    // Domain Availability API  (/v1/domain/availability)
    // =========================================================================

    /** Check if a domain is available, with optional suggestions. */
    public String domainAvailability(String domain, Boolean suggestions, Integer count, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v1.0/domain/availability"
                + "?apiKey=" + enc(apiKey)
                + "&domain=" + enc(domain));
        if (suggestions != null) url.append("&sug=").append(suggestions);
        if (count != null)       url.append("&count=").append(count);
        url.append("&format=").append(def(format, "json"));
        return call("Domain Availability: " + domain, url.toString());
    }

    // =========================================================================
    // Subdomains API  (/v1/subdomains)
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
        return call("Subdomains: " + domain, url.toString());
    }

    // =========================================================================
    // Domain Discovery / Typos API  (/v1/domain/discovery)
    // =========================================================================

    /**
     * Find domains matching a keyword or pattern (typos/typosquatting discovery).
     * Use '*' as a wildcard in pattern. Example: "*googl*", "g0ogle"
     */
    public String domainDiscovery(String keyword, String pattern, Integer labelLength,
                                  Integer page, String format) {
        StringBuilder url = new StringBuilder(BASE + "/v1.0/domain/taken"
                + "?apiKey=" + enc(apiKey));
        if (notBlank(keyword)) url.append("&keyword=").append(enc(keyword));
        if (notBlank(pattern)) url.append("&pattern=").append(enc(pattern));
        if (labelLength != null) url.append("&label_length=").append(labelLength);
        if (page != null)        url.append("&page=").append(page);
        if (notBlank(format))    url.append("&format=").append(enc(format));
        String label = notBlank(keyword) ? keyword : pattern;
        return call("Domain Discovery: " + label, url.toString());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String call(String title, String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return formatResponse(title, response.statusCode(), response.body());

        } catch (Exception e) {
            return title + "\n\nError: " + e.getMessage();
        }
    }

    private String formatResponse(String title, int status, String body) {
        if (status == 401 || status == 403) {
            return title + "\n\nUnauthorized (HTTP " + status + "). Check your WHOISFREAKS_API_KEY and subscription status.";
        }
        if (status == 404) {
            return title + "\n\nNo data found (HTTP 404). The requested resource does not exist in the database.";
        }
        if (status == 408) {
            return title + "\n\nTimeout (HTTP 408). Unable to fetch data from upstream WHOIS/DNS servers.";
        }
        if (status == 412) {
            return title + "\n\nAPI plan request limit exceeded (HTTP 412). Upgrade your plan or wait for reset.";
        }
        if (status == 413) {
            return title + "\n\nCredit/surcharge limit exceeded (HTTP 413). Add more credits to continue.";
        }
        if (status == 423) {
            return title + "\n\nBogon/non-routable IP address (HTTP 423). This IP is not publicly routable.";
        }
        if (status == 429) {
            return title + "\n\nRate limit reached (HTTP 429). Slow down requests or upgrade your plan.";
        }
        if (status >= 500) {
            return title + "\n\nServer error (HTTP " + status + "). The WhoisFreaks API is temporarily unavailable.";
        }
        if (status >= 400) {
            return title + "\n\nError (HTTP " + status + "): " + body;
        }

        // 200 / 206 — pretty-print JSON
        try {
            JsonNode node = mapper.readTree(body);
            return title + "\n\n" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return title + "\n\n" + body;
        }
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
