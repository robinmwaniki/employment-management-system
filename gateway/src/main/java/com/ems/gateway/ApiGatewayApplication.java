package com.ems.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the independently deployable EMS API Gateway.
 *
 * <p>The gateway uses the reactive WebFlux runtime. It is intentionally kept
 * separate from the existing servlet-based EMS backend so the two runtimes
 * do not compete for application auto-configuration.</p>
 */
@SpringBootApplication
public class ApiGatewayApplication {

    /**
     * Starts the EMS API Gateway.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
