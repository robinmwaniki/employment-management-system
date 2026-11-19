package com.ems.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

/**
 * Validates the existing EMS backend's HMAC-signed JWT format.
 *
 * <p>The backend uses JJWT with a Base64-encoded HMAC key, issuer
 * {@code employee-management-system}, a username subject and an expiration
 * timestamp. The gateway deliberately uses the same parser contract so that
 * it does not invent a second token format or weaken backend validation.</p>
 */
@Component
public class JwtTokenValidator {

    private static final String EXPECTED_ISSUER = "employee-management-system";
    private static final int MINIMUM_KEY_BYTES = 32;

    private final SecretKey signingKey;

    /**
     * Builds the verification key from the same required environment secret
     * supplied to the EMS backend. There is intentionally no development fallback.
     *
     * @param base64Secret Base64-encoded EMS JWT signing key
     */
    public JwtTokenValidator(@Value("${ems.jwt.secret}") String base64Secret) {
        final byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(base64Secret);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET must be a valid Base64-encoded EMS signing key.");
        }

        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must decode to at least 32 bytes (256 bits).");
        }

        try {
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT_SECRET is not a valid HMAC signing key.");
        }
    }

    /**
     * Verifies the token signature, issuer, expiration and required subject.
     * JJWT selects the compatible HMAC algorithm from the key length, matching
     * the backend's {@code Jwts.builder().signWith(signingKey)} behavior.
     *
     * @param token compact signed JWT from the Authorization header
     * @return verified token claims
     * @throws io.jsonwebtoken.JwtException if the token is malformed, expired,
     *                                      incorrectly signed or has the wrong issuer
     */
    public Claims validate(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("A bearer token is required.");
        }

        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(EXPECTED_ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new MalformedJwtException("JWT subject is required.");
        }
        if (claims.getExpiration() == null) {
            throw new MalformedJwtException("JWT expiration is required.");
        }

        return claims;
    }
}
