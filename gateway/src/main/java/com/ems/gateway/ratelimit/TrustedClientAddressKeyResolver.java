package com.ems.gateway.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolves a privacy-preserving client key and trusts forwarded addresses only
 * when the direct socket peer belongs to an explicitly configured proxy CIDR.
 */
@Component
public class TrustedClientAddressKeyResolver implements KeyResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]+");
    private final List<CidrRange> trustedProxies;

    /**
     * Creates a resolver using explicitly trusted IPv4/IPv6 proxy CIDRs.
     *
     * @param trustedProxyCidrs comma-separated proxy CIDR ranges
     */
    public TrustedClientAddressKeyResolver(
            @Value("${ems.gateway.rate-limit.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
        this.trustedProxies = parseTrustedProxies(trustedProxyCidrs);
    }

    /**
     * Uses the socket peer unless a trusted proxy supplied a valid client address.
     *
     * @param exchange current gateway exchange
     * @return SHA-256 key, or empty when no socket address is available
     */
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return Mono.empty();
        }
        InetAddress client = remote.getAddress();
        if (trustedProxies.stream().anyMatch(range -> range.contains(client))) {
            InetAddress forwarded = firstForwardedAddress(
                    exchange.getRequest().getHeaders().getFirst(FORWARDED_FOR));
            if (forwarded != null) {
                client = forwarded;
            }
        }
        return Mono.just(hash(client));
    }

    private List<CidrRange> parseTrustedProxies(String configured) {
        if (configured == null || configured.isBlank()) {
            return List.of();
        }
        List<CidrRange> ranges = new ArrayList<>();
        for (String value : configured.split(",")) {
            ranges.add(CidrRange.parse(value.trim()));
        }
        return List.copyOf(ranges);
    }

    private InetAddress firstForwardedAddress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String candidate = value.split(",", 2)[0].trim();
        if (!IP_LITERAL.matcher(candidate).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private String hash(InetAddress address) {
        try {
            return "client:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(address.getAddress()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
        }
    }

    private record CidrRange(byte[] network, int prefixLength) {
        private static CidrRange parse(String value) {
            String[] parts = value.split("/", -1);
            if (parts.length > 2 || !IP_LITERAL.matcher(parts[0]).matches()) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + value);
            }
            try {
                byte[] address = InetAddress.getByName(parts[0]).getAddress();
                int max = address.length * 8;
                int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : max;
                if (prefix < 0 || prefix > max) {
                    throw new IllegalArgumentException("Invalid trusted proxy CIDR prefix: " + value);
                }
                byte[] network = address.clone();
                for (int bit = prefix; bit < max; bit++) {
                    network[bit / 8] &= (byte) ~(1 << (7 - bit % 8));
                }
                return new CidrRange(network, prefix);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR: " + value, exception);
            }
        }

        private boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int index = 0; index < fullBytes; index++) {
                if (candidate[index] != network[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
