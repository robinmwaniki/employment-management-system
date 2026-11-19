package com.ems.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that Redis keys are hashed and client IP resolution trusts only
 * explicitly configured proxy networks.
 */
class EmsRateLimitKeyResolverTests {

    private static final String TEST_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void hashesTheSignedJwtSubjectInsteadOfUsingPersonalData() {
        EmsRateLimitKeyResolver resolver = resolver(List.of());
        String token = token("employee@example.test");
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/employees")
                        .header("Authorization", "Bearer " + token)
                        .remoteAddress(new InetSocketAddress("192.0.2.10", 8080))
                        .build());

        String key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("user:" + sha256("employee@example.test"));
        assertThat(key).doesNotContain("employee@example.test", "192.0.2.10");
    }

    @Test
    void ignoresForwardedIpHeadersFromAnUntrustedPeer() {
        EmsRateLimitKeyResolver resolver = resolver(List.of("10.0.0.0/8"));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login")
                        .header("X-Forwarded-For", "203.0.113.77")
                        .remoteAddress(new InetSocketAddress("192.0.2.10", 8080))
                        .build());

        assertThat(resolver.resolve(exchange).block())
                .isEqualTo("ip:" + sha256("192.0.2.10"));
    }

    @Test
    void walksForwardedChainFromTrustedProxyToFindOriginalClient() {
        EmsRateLimitKeyResolver resolver = resolver(List.of("10.0.0.0/8"));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login")
                        .header("X-Forwarded-For", "198.51.100.8, 10.0.0.5")
                        .remoteAddress(new InetSocketAddress("10.0.0.10", 8080))
                        .build());

        assertThat(resolver.resolve(exchange).block())
                .isEqualTo("ip:" + sha256("198.51.100.8"));
    }

    @Test
    void fallsBackToPeerAddressWhenForwardedChainIsMalformed() {
        EmsRateLimitKeyResolver resolver = resolver(List.of("10.0.0.0/8"));
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login")
                        .header("X-Forwarded-For", "attacker.example, 10.0.0.5")
                        .remoteAddress(new InetSocketAddress("10.0.0.10", 8080))
                        .build());

        assertThat(resolver.resolve(exchange).block())
                .isEqualTo("ip:" + sha256("10.0.0.10"));
    }

    @Test
    void rejectsInvalidTrustedProxyConfiguration() {
        assertThatThrownBy(() -> resolver(List.of("10.0.0.0/not-a-prefix")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid trusted proxy CIDR");
    }

    private EmsRateLimitKeyResolver resolver(List<String> trustedProxyCidrs) {
        return new EmsRateLimitKeyResolver(
                new JwtTokenValidator(TEST_SECRET), trustedProxyCidrs);
    }

    private String token(String subject) {
        return io.jsonwebtoken.Jwts.builder()
                .issuer("employee-management-system")
                .subject(subject)
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        io.jsonwebtoken.io.Decoders.BASE64.decode(TEST_SECRET)))
                .compact();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
