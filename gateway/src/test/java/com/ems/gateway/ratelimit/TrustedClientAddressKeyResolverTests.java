package com.ems.gateway.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests trusted proxy checks and opaque client key generation. */
class TrustedClientAddressKeyResolverTests {

    @Test
    void ignoresForwardedAddressFromUntrustedPeer() throws Exception {
        var resolver = new TrustedClientAddressKeyResolver("10.0.0.0/8");
        var request = exchange("203.0.113.10", "198.51.100.25");
        String key = resolver.resolve(request).block();
        String expected = new TrustedClientAddressKeyResolver("")
                .resolve(exchange("203.0.113.10", null)).block();
        assertThat(key).isEqualTo(expected).doesNotContain("198.51.100.25");
    }

    @Test
    void trustsFirstForwardedAddressOnlyFromConfiguredProxy() throws Exception {
        var resolver = new TrustedClientAddressKeyResolver("10.0.0.0/8");
        String actual = resolver.resolve(exchange("10.1.2.3", "198.51.100.25, 10.2.0.8")).block();
        String expected = new TrustedClientAddressKeyResolver("")
                .resolve(exchange("198.51.100.25", null)).block();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void fallsBackToProxyAddressForMalformedForwardedHeader() throws Exception {
        var resolver = new TrustedClientAddressKeyResolver("10.0.0.0/8");
        String actual = resolver.resolve(exchange("10.1.2.3", "spoofed-host")).block();
        String expected = new TrustedClientAddressKeyResolver("")
                .resolve(exchange("10.1.2.3", null)).block();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void returnsEmptyWhenSocketAddressIsMissing() {
        var resolver = new TrustedClientAddressKeyResolver("");
        var request = MockServerWebExchange.from(MockServerHttpRequest.get("/api").build());
        assertThat(resolver.resolve(request).blockOptional()).isEmpty();
    }

    @Test
    void rejectsInvalidTrustedProxyCidr() {
        assertThatThrownBy(() -> new TrustedClientAddressKeyResolver("not-a-cidr"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MockServerWebExchange exchange(String remote, String forwarded) throws Exception {
        var builder = MockServerHttpRequest.get("/api")
                .remoteAddress(new InetSocketAddress(InetAddress.getByName(remote), 12345));
        if (forwarded != null) {
            builder.header("X-Forwarded-For", forwarded);
        }
        return MockServerWebExchange.from(builder.build());
    }
}
