package com.ems;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * Starts the whole application against an empty PostgreSQL: Flyway builds the
 * schema from the migrations, then Hibernate checks that it matches the entities.
 * If an entity and a migration ever drift apart, this test fails.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EmployeeManagementSystemApplicationTests {

    @Test
    void contextLoads() {
    }
}
