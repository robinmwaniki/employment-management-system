package com.ems.gateway.tracing;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that exported spans retain useful route metadata but not PII. */
class GatewayTracingConfigurationTests {

    @Test
    void removesRawUrlsCredentialsAndInternalBackendAddressesFromObservations() {
        Observation.Context context = new Observation.Context();
        context.setName("http.client.requests");
        context.addHighCardinalityKeyValue(
                KeyValue.of("http.uri", "/api/employees/42?email=private@example.test"));
        context.addHighCardinalityKeyValue(
                KeyValue.of("http.request.header.authorization", "Bearer sensitive-token"));
        context.addHighCardinalityKeyValue(
                KeyValue.of("http.request.header.cookie", "SESSION=private-session"));
        context.addHighCardinalityKeyValue(
                KeyValue.of("http.request.header.x-correlation-id", "private-correlation-id"));
        context.addLowCardinalityKeyValue(
                KeyValue.of("spring.cloud.gateway.route.uri", "http://internal-ems:8080"));
        context.addLowCardinalityKeyValue(
                KeyValue.of("spring.cloud.gateway.route.id", "ems-employees-api"));

        ObservationFilter filter = new GatewayTracingConfiguration().gatewayTracePrivacyFilter();
        filter.map(context);

        assertThat(context.getHighCardinalityKeyValue("http.uri")).isNull();
        assertThat(context.getHighCardinalityKeyValue("http.request.header.authorization")).isNull();
        assertThat(context.getHighCardinalityKeyValue("http.request.header.cookie")).isNull();
        assertThat(context.getHighCardinalityKeyValue("http.request.header.x-correlation-id")).isNull();
        assertThat(context.getLowCardinalityKeyValue("spring.cloud.gateway.route.uri")).isNull();
        assertThat(context.getLowCardinalityKeyValue("spring.cloud.gateway.route.id").getValue())
                .isEqualTo("ems-employees-api");
    }
}
