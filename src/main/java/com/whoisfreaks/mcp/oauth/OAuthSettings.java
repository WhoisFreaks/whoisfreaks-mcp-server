package com.whoisfreaks.mcp.oauth;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

/**
 * Settings for OAuth sign-in on the hosted HTTP server, read from the environment. OAuth is on
 * when OAUTH_ISSUER is set, and then the others are required too:
 *
 *   OAUTH_ISSUER          the authorization server, e.g. https://auth.whoisfreaks.com
 *   MCP_RESOURCE          this server's address, e.g. https://mcp.whoisfreaks.com; access tokens must
 *                         name it (or it plus /mcp) as their audience
 *   BILLING_URL           where the API key lookup lives, e.g. https://billing-backend.whoisfreaks.com
 *   BILLING_SERVER_TOKEN  the shared secret for that lookup (billing's oauth.mcp-server-token)
 *   OAUTH_JWKS_URI        optional: the issuer's signing keys, by default OAUTH_ISSUER/oauth2/jwks
 */
public record OAuthSettings(String issuer, String resource, URI jwksUri, String billingUrl, String billingServerToken) {

    /** The scope an access token needs to run lookups. */
    public static final String LOOKUPS_SCOPE = "lookups";
    /** The OAuth-only endpoint's path; its own resource identifier and metadata live under this. */
    public static final String OAUTH_PATH = "/oauth";
    private static final String METADATA_PATH = "/.well-known/oauth-protected-resource";
    private static final String MCP_PATH = "/mcp";

    /** The settings, or null when OAUTH_ISSUER isn't set and the server takes API keys only. */
    public static OAuthSettings fromEnvironment() {
        return from(System::getenv);
    }

    static OAuthSettings from(Function<String, String> env) {
        String issuerSetting = env.apply("OAUTH_ISSUER");
        if (issuerSetting == null || issuerSetting.isBlank()) {
            return null;
        }
        String issuer = httpUrl(env, "OAUTH_ISSUER");
        String resource = stripTrailingSlash(httpUrl(env, "MCP_RESOURCE"));
        if (!URI.create(resource).getRawPath().isEmpty()) {
            throw new IllegalStateException("MCP_RESOURCE must be the server's address without a path, e.g. "
                    + "https://mcp.whoisfreaks.com, not " + resource);
        }
        String billingUrl = httpUrl(env, "BILLING_URL");
        String billingServerToken = required(env, "BILLING_SERVER_TOKEN");
        String jwksUri = env.apply("OAUTH_JWKS_URI");
        if (jwksUri == null || jwksUri.isBlank()) {
            jwksUri = stripTrailingSlash(issuer) + "/oauth2/jwks";
        }
        return new OAuthSettings(issuer, resource, URI.create(jwksUri.trim()), stripTrailingSlash(billingUrl),
                billingServerToken);
    }

    /**
     * The resource identifiers apps may ask a token for: the server's address, the address plus /mcp,
     * and the address plus /oauth (the OAuth-only endpoint). An app's metadata lookup finds whichever
     * matches the URL it was given, and a token minted for any of them is accepted.
     */
    public List<String> resources() {
        return List.of(this.resource, this.resource + MCP_PATH, this.resource + OAUTH_PATH);
    }

    /** Where each resource's metadata is (RFC 9728: the well-known path, then the resource's own path). */
    public String metadataPath(String resource) {
        return METADATA_PATH + URI.create(resource).getRawPath();
    }

    /** The full URL of a resource's metadata (RFC 9728), which a 401 challenge points apps to. */
    public String metadataUrl(String resource) {
        return this.resource + metadataPath(resource);
    }

    /** The full URL of the server address's metadata. */
    public String metadataUrl() {
        return metadataUrl(this.resource);
    }

    /** The OAuth-only endpoint's metadata URL, which its 401 challenge points apps to. */
    public String oauthMetadataUrl() {
        return metadataUrl(this.resource + OAUTH_PATH);
    }

    private static String httpUrl(Function<String, String> env, String name) {
        String value = required(env, name);
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(name + " is not a URL: " + value, e);
        }
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getRawAuthority() == null) {
            throw new IllegalStateException(name + " must be an http(s) URL, not " + value);
        }
        return value;
    }

    private static String required(Function<String, String> env, String name) {
        String value = env.apply(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set when OAUTH_ISSUER is");
        }
        return value.trim();
    }

    private static String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
