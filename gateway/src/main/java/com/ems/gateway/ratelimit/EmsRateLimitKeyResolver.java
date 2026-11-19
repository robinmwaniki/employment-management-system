package com.ems.gateway.ratelimit;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolves privacy-safe Redis rate-limit keys for authenticated and anonymous
 * gateway requests. Raw subjects and IP addresses are never used as Redis keys.
 */
@Component
public class EmsRateLimitKeyResolver implements KeyResolver {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]+");

    private final JwtTokenValidator tokenValidator;
    private final List<Cidr> trustedProxies;

    /**
     * Creates the key resolver with an explicit trusted-proxy allowlist.
     *
     * @param tokenValidator validator for the existing EMS JWT format
     * @param trustedProxyCidrs comma-separated trusted proxy CIDR ranges
     */
    public EmsRateLimitKeyResolver(
            JwtTokenValidator tokenValidator,
            @Value("${ems.gateway.rate-limit.trusted-proxy-cidrs:}") List<String> trustedProxyCidrs) {
        this.tokenValidator = tokenValidator;
        this.trustedProxies = parseTrustedProxies(trustedProxyCidrs);
    }

    /**
     * Resolves authenticated users to a hash of their signed JWT subject.
     * Anonymous requests are keyed by the immediate peer address unless that
     * peer is explicitly trusted, in which case the forwarded chain is walked
     * from right to left to find the first untrusted client address.
     *
     * @param exchange current gateway request
     * @return non-PII, stable rate-limit key
     */
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> resolveKey(exchange));
    }

    private String resolveKey(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            try {
                Claims claims = tokenValidator.validate(token);
                if (claims.getSubject() != null && !claims.getSubject().isBlank()) {
                    return "user:" + sha256(claims.getSubject());
                }
            } catch (RuntimeException ignored) {
                // Authentication rejects invalid tokens; do not use token data as a key.
            }
        }

        return "ip:" + sha256(resolveClientAddress(exchange));
    }

    private String resolveClientAddress(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }

        InetAddress peer = remote.getAddress();
        if (!isTrusted(peer)) {
            // Never trust X-Forwarded-For supplied by an untrusted direct peer.
            return peer.getHostAddress();
        }

        List<InetAddress> forwarded = parseForwardedChain(
                exchange.getRequest().getHeaders().getFirst("X-Forwarded-For"));
        if (forwarded.isEmpty()) {
            return peer.getHostAddress();
        }

        // XFF is ordered client, proxy-1, proxy-2. Walk backwards from the
        // trusted connection peer and stop at the first address not on our allowlist.
        for (int index = forwarded.size() - 1; index >= 0; index--) {
            InetAddress candidate = forwarded.get(index);
            if (!isTrusted(candidate)) {
                return candidate.getHostAddress();
            }
        }
        return forwarded.get(0).getHostAddress();
    }

    private List<InetAddress> parseForwardedChain(String header) {
        if (header == null || header.isBlank()) {
            return List.of();
        }

        List<InetAddress> addresses = new ArrayList<>();
        for (String part : header.split(",")) {
            InetAddress address = parseIpLiteral(part.trim());
            if (address == null) {
                // Ignore the entire header rather than trusting a partial chain.
                return List.of();
            }
            addresses.add(address);
        }
        return addresses;
    }

    private InetAddress parseIpLiteral(String value) {
        if (value == null || value.isBlank() || !IP_LITERAL.matcher(value).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private boolean isTrusted(InetAddress address) {
        return trustedProxies.stream().anyMatch(cidr -> cidr.contains(address));
    }

    private List<Cidr> parseTrustedProxies(List<String> configured) {
        if (configured == null) {
            return List.of();
        }
        return configured.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(Cidr::parse)
                .toList();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private record Cidr(byte[] network, int prefixLength) {

        private static Cidr parse(String value) {
            String[] parts = value.trim().split("/", -1);
            if (parts.length != 2) {
                throw new IllegalStateException("Trusted proxy entries must use CIDR notation.");
            }

            InetAddress address;
            try {
                if (!IP_LITERAL.matcher(parts[0]).matches()) {
                    throw new IllegalStateException("Trusted proxy CIDR must contain an IP literal.");
                }
                address = InetAddress.getByName(parts[0]);
                int prefix = Integer.parseInt(parts[1]);
                int maxPrefix = address.getAddress().length * 8;
                if (prefix < 0 || prefix > maxPrefix) {
                    throw new IllegalStateException("Trusted proxy CIDR prefix is out of range.");
                }
                return new Cidr(address.getAddress(), prefix);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalStateException("Invalid trusted proxy CIDR.", exception);
            }
        }

        private boolean contains(InetAddress candidate) {
            byte[] candidateBytes = candidate.getAddress();
            if (candidateBytes.length != network.length) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int index = 0; index < fullBytes; index++) {
                if (network[index] != candidateBytes[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }

            int mask = 0xFF << (8 - remainingBits);
            return (network[fullBytes] & mask) == (candidateBytes[fullBytes] & mask);
        }
    }
}
