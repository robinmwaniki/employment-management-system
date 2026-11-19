package com.ems.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the standalone gateway starts with its reactive application
 * configuration and expected service identity.
 */
@SpringBootTest
class ApiGatewayApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    /**
     * The gateway application context must start successfully.
     */
    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
        assertThat(environment.getProperty("spring.application.name"))
                .isEqualTo("ems-api-gateway");
        assertThat(environment.getProperty("spring.main.web-application-type"))
                .isEqualTo("reactive");
    }
}
