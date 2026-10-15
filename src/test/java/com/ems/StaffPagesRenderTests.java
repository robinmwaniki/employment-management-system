package com.ems;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test: every main staff page renders (HTTP 200) against an empty database,
 * so a broken template or a failing query is caught before deployment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@WithMockUser(roles = "ADMIN")
class StaffPagesRenderTests {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {
            "/departments",
            "/employees",
            "/payroll",
            "/leave",
            "/announcements",
            "/notifications",
            "/attendance",
            "/users"
    })
    void pageRenders(String path) throws Exception {

        mockMvc.perform(get(path)).andExpect(status().isOk());
    }
}
