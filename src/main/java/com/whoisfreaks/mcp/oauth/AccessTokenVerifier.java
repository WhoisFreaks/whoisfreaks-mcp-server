package com.whoisfreaks.mcp.oauth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import java.net.MalformedURLException;
import java.text.ParseException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

/**
 * Checks the access tokens apps send: an RS256 JWT signed with one of the authorization
 * server's keys (fetched from its JWKS and cached), issued by OAUTH_ISSUER for this server
 * (one of {@link OAuthSettings#resources()} as audience), unexpired with 60 s clock skew, for a
 * subject, with the lookups scope.
 */
public class AccessTokenVerifier {

    public record VerifiedToken(String subject, Instant expiresAt) {
    }

    /** The token can't be used; {@code insufficientScope} when it's valid but lacks the lookups scope. */
    public static class InvalidTokenException extends Exception {
        private final boolean insufficientScope;

        InvalidTokenException(String message, boolean insufficientScope) {
            super(message);
            this.insufficientScope = insufficientScope;
        }

        public boolean insufficientScope() {
            return this.insufficientScope;
        }
    }

    /** The authorization server's keys couldn't be fetched, so no token can be checked right now. */
    public static class KeysUnavailableException extends Exception {
        KeysUnavailableException(Throwable cause) {
            super(cause);
        }
    }

    private final ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();

    public AccessTokenVerifier(OAuthSettings settings) {
        this(settings, remoteKeys(settings));
    }

    AccessTokenVerifier(OAuthSettings settings, JWKSource<SecurityContext> keys) {
        this.processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
        // Mutable sets: Nimbus asks them contains(null), which Set.of() answers with an exception.
        this.processor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                new HashSet<>(settings.resources()),
                new JWTClaimsSet.Builder().issuer(settings.issuer()).build(),
                new HashSet<>(List.of("sub", "exp")),
                null));
    }

    public VerifiedToken verify(String token) throws InvalidTokenException, KeysUnavailableException {
        JWTClaimsSet claims;
        try {
            claims = this.processor.process(token, null);
        } catch (KeySourceException e) {
            throw new KeysUnavailableException(e);
        } catch (ParseException | BadJOSEException | JOSEException e) {
            throw new InvalidTokenException("The access token is invalid or has expired.", false);
        }
        if (!scopes(claims).contains(OAuthSettings.LOOKUPS_SCOPE)) {
            throw new InvalidTokenException("The access token doesn't allow lookups.", true);
        }
        return new VerifiedToken(claims.getSubject(), claims.getExpirationTime().toInstant());
    }

    /** The scope claim, which the authorization server writes as a JSON array; a space-separated string works too. */
    private static List<String> scopes(JWTClaimsSet claims) {
        Object scope = claims.getClaim("scope");
        if (scope instanceof String value) {
            return List.of(value.trim().split("\\s+"));
        }
        if (scope instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static JWKSource<SecurityContext> remoteKeys(OAuthSettings settings) {
        try {
            return JWKSourceBuilder.<SecurityContext>create(settings.jwksUri().toURL()).build();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("OAUTH_JWKS_URI is not a URL: " + settings.jwksUri(), e);
        }
    }
}
