package com.whoisfreaks.mcp.oauth;

import java.net.URI;
import java.util.function.Function;

/**
 * Settings for OAuth mode ({@code --oauth} or {@code MCP_AUTH=oauth}), read from the environment:
 *
 *   OAUTH_ISSUER          the authorization server, e.g. https://auth.whoisfreaks.com
 *   MCP_RESOURCE          this server's resource identifier, e.g. https://mcp-oauth.whoisfreaks.com;
 *                         access tokens must name it as their audience
 *   BILLING_URL           where the API key lookup lives, e.g. https://billing-backend.whoisfreaks.com
 *   BILLING_SERVER_TOKEN  the shared secret for that lookup (billing's oauth.mcp-server-token)
 *   OAUTH_JWKS_URI        optional: the issuer's signing keys, by default OAUTH_ISSUER/oauth2/jwks
 */
public record OAuthSettings(String issuer, String resource, URI jwksUri, String billingUrl, String billingServerToken) {

    /** The scope an access token needs to run lookups. */
    public static final String LOOKUPS_SCOPE = "lookups";
    private static final String METADATA_PATH = "/.well-known/oauth-protected-resource";

    public static OAuthSettings fromEnvironment() {
        return from(System::getenv);
    }

    static OAuthSettings from(Function<String, String> env) {
        String issuer = httpUrl(env, "OAUTH_ISSUER");
        String resource = httpUrl(env, "MCP_RESOURCE");
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
     * Path of this server's protected resource metadata: the well-known path followed by the
     * resource's own path (RFC 9728), so a resource of https://host/mcp has its metadata at
     * /.well-known/oauth-protected-resource/mcp.
     */
    public String metadataPath() {
        return METADATA_PATH + stripTrailingSlash(URI.create(this.resource).getRawPath());
    }

    /** The metadata's full URL, which the 401 challenge points apps to. */
    public String metadataUrl() {
        URI resource = URI.create(this.resource);
        return resource.getScheme() + "://" + resource.getRawAuthority() + metadataPath();
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
            throw new IllegalStateException(name + " must be set in OAuth mode");
        }
        return value.trim();
    }

    private static String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
