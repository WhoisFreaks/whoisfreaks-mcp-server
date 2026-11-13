package com.whoisfreaks.mcp.oauth;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OAuth is on only with OAUTH_ISSUER, and then refuses to start half set up. */
class OAuthSettingsTest {

    private static final Map<String, String> COMPLETE = Map.of(
            "OAUTH_ISSUER", "https://auth.example.com",
            "MCP_RESOURCE", "https://mcp.example.com/",
            "BILLING_URL", "https://billing-backend.example.com/",
            "BILLING_SERVER_TOKEN", "secret");

    @Test
    void offWithoutAnIssuer() {
        assertNull(OAuthSettings.from(name -> null));
        assertNull(OAuthSettings.from(Map.of("OAUTH_ISSUER", " ", "MCP_RESOURCE", "https://mcp.example.com")::get));
    }

    @Test
    void readsTheSettingsAndDerivesTheRest() {
        OAuthSettings settings = OAuthSettings.from(COMPLETE::get);

        assertEquals(List.of("https://mcp.example.com", "https://mcp.example.com/mcp"), settings.resources());
        assertEquals("https://mcp.example.com/.well-known/oauth-protected-resource", settings.metadataUrl());
        assertEquals("/.well-known/oauth-protected-resource/mcp", settings.metadataPath("https://mcp.example.com/mcp"));
        assertEquals("https://auth.example.com/oauth2/jwks", settings.jwksUri().toString());
        assertEquals("https://billing-backend.example.com", settings.billingUrl());
    }

    @Test
    void anIssuerWithoutTheRestStopsTheServer() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> OAuthSettings.from(Map.of("OAUTH_ISSUER", "https://auth.example.com")::get));

        assertTrue(error.getMessage().contains("MCP_RESOURCE"), error.getMessage());
    }

    @Test
    void theResourceIsTheServerAddressWithoutAPath() {
        Map<String, String> withPath = new HashMap<>(COMPLETE);
        withPath.put("MCP_RESOURCE", "https://mcp.example.com/mcp");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> OAuthSettings.from(withPath::get));
        assertTrue(error.getMessage().contains("without a path"), error.getMessage());
    }
}
