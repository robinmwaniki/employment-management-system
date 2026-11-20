package com.ems.gateway.ratelimit;

import com.ems.gateway.security.JwtAuthenticationGlobalFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
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
 * Resolves a privacy-preserving rate-limit key from an authenticated subject
 * or a safely determined client IP address.
 *
 * <p>Forwarded addresses are ignored unless the direct socket peer belongs to
 * an explicitly configured trusted proxy CIDR. When a trusted proxy chain is
 * present, hops are inspected from right to left so client-supplied values
 * prepended to X-Forwarded-For cannot override the address appended by the
 * trusted proxy.</p>
 */
@Component
public class TrustedClientAddressKeyResolver implements KeyResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9a-fA-F:.]+");

    private final List<CidrRange> trustedProxies;

    /**
     * Creates a resolver using explicitly trusted IPv4/IPv6 proxy CIDRs.
     *
     * @param trustedProxyCidrs comma-separated trusted proxy CIDR ranges
     */
    public TrustedClientAddressKeyResolver(
            @Value("${ems.gateway.rate-limit.trusted-proxy-cidrs:}") String trustedProxyCidrs) {
        this.trustedProxies = parseTrustedProxies(trustedProxyCidrs);
    }

    /**
     * Returns a hashed subject key for authenticated requests or a hashed IP
     * key for public requests such as login.
     *
     * @param exchange current gateway exchange
     * @return opaque key, or empty if a public request has no socket address
     */
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String subject = exchange.getAttribute(
                JwtAuthenticationGlobalFilter.AUTHENTICATED_SUBJECT_ATTRIBUTE);
        if (subject != null && !subject.isBlank()) {
            return Mono.just(hash("user:" + subject));
        }

        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return Mono.empty();
        }

        InetAddress client = resolveClientAddress(
                remote.getAddress(),
                exchange.getRequest().getHeaders().getFirst(FORWARDED_FOR));
        return Mono.just(hash("ip:" + client.getHostAddress()));
    }

    private InetAddress resolveClientAddress(InetAddress remotePeer, String forwardedFor) {
        if (!isTrustedProxy(remotePeer) || forwardedFor == null || forwardedFor.isBlank()) {
            return remotePeer;
        }

        String[] hops = forwardedFor.split(",");
        for (int index = hops.length - 1; index >= 0; index--) {
            InetAddress hop = parseAddress(hops[index].trim());
            if (hop == null) {
                // An invalid chain is not trusted; fall back to the socket peer.
                return remotePeer;
            }
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }

        // Every forwarded hop is itself a trusted proxy. No client address was
        // supplied, so use the left-most trusted hop rather than guessing.
        InetAddress leftMost = parseAddress(hops[0].trim());
        return leftMost == null ? remotePeer : leftMost;
    }

    private InetAddress parseAddress(String value) {
        if (value == null || !IP_LITERAL.matcher(value).matches()) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private boolean isTrustedProxy(InetAddress address) {
        return trustedProxies.stream().anyMatch(range -> range.contains(address));
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

    private String hash(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return "client:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
        }
    }

    private record CidrRange(byte[] network, int prefixLength) {

        private static CidrRange parse(String value) {
            String[] parts = value.split("/", -1);
            if (parts.length > 2 || !IP_LITERAL.matcher(parts[0]).matches()) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR.");
            }
            try {
                byte[] address = InetAddress.getByName(parts[0]).getAddress();
                int maximumPrefix = address.length * 8;
                int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : maximumPrefix;
                if (prefix < 0 || prefix > maximumPrefix) {
                    throw new IllegalArgumentException("Invalid trusted proxy CIDR prefix.");
                }

                byte[] network = address.clone();
                for (int bit = prefix; bit < maximumPrefix; bit++) {
                    network[bit / 8] &= (byte) ~(1 << (7 - bit % 8));
                }
                return new CidrRange(network, prefix);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid trusted proxy CIDR.", exception);
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
