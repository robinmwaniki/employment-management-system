package com.ems.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the gateway accepts the existing EMS JJWT format and rejects
 * malformed, expired, incorrectly signed or incorrectly issued tokens.
 */
class JwtTokenValidatorTests {

    private static final String TEST_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String WRONG_SECRET =
            "YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=";
    private static final String ISSUER = "employee-management-system";

    private final JwtTokenValidator validator = new JwtTokenValidator(TEST_SECRET);
    private final SecretKey signingKey =
            Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));

    @Test
    void acceptsValidEmsTokenAndReturnsItsSubject() {
        Claims claims = validator.validate(token(ISSUER, "admin@example.test",
                Instant.now().plusSeconds(300)));

        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getSubject()).isEqualTo("admin@example.test");
        assertThat(claims.getExpiration()).isNotNull();
    }

    @Test
    void rejectsExpiredToken() {
        String expired = token(ISSUER, "admin@example.test",
                Instant.now().minusSeconds(30));

        assertThatThrownBy(() -> validator.validate(expired))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        SecretKey wrongKey =
                Keys.hmacShaKeyFor(Decoders.BASE64.decode(WRONG_SECRET));
        String incorrectlySigned = Jwts.builder()
                .issuer(ISSUER)
                .subject("admin@example.test")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(wrongKey)
                .compact();

        assertThatThrownBy(() -> validator.validate(incorrectlySigned))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        String wrongIssuer = token("another-application", "admin@example.test",
                Instant.now().plusSeconds(300));

        assertThatThrownBy(() -> validator.validate(wrongIssuer))
                .isInstanceOf(io.jsonwebtoken.IncorrectClaimException.class);
    }

    @Test
    void rejectsTokenWithoutSubject() {
        String missingSubject = Jwts.builder()
                .issuer(ISSUER)
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(signingKey)
                .compact();

        assertThatThrownBy(() -> validator.validate(missingSubject))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void rejectsTokenWithoutExpiration() {
        String missingExpiration = Jwts.builder()
                .issuer(ISSUER)
                .subject("admin@example.test")
                .issuedAt(new Date())
                .signWith(signingKey)
                .compact();

        assertThatThrownBy(() -> validator.validate(missingExpiration))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    void rejectsMalformedAndBlankTokens() {
        assertThatThrownBy(() -> validator.validate("not-a-jwt"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
        assertThatThrownBy(() -> validator.validate(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidOrWeakSigningConfigurationAtStartup() {
        assertThatThrownBy(() -> new JwtTokenValidator("not-base64"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtTokenValidator("c2hvcnQ="))
                .isInstanceOf(IllegalStateException.class);
    }

    private String token(String issuer, String subject, Instant expiresAt) {
        return Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }
}
