package com.ems;

import com.ems.security.jwt.JwtService;
import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.entity.PerformanceReview;
import com.ems.entity.Role;
import com.ems.entity.User;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.PerformanceRepository;
import com.ems.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.test.context.support.WithMockUser;

import javax.crypto.SecretKey;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the access rules: who may open which page or endpoint.
 */
@SpringBootTest(properties = "SWAGGER_ENABLED=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityRulesTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;


    @Test
    void generatedJwtContainsSignedCurrentAuthorities() {
        UserDetails user = org.springframework.security.core.userdetails.User.withUsername("admin@example.test")
                .password("not-used-in-this-test")
                .authorities("ROLE_ADMIN", "ROLE_HR_MANAGER")
                .build();

        String token = jwtService.generateToken(user);
        SecretKey signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer("employee-management-system")
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("admin@example.test");
        assertThat(claims.get("roles", List.class))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_HR_MANAGER");
    }

    @Test
    void anonymousUserIsSentToLoginFromStaffPages() throws Exception {

        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void openApiDocumentRequiresAdministratorBearerToken() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void hrManagerCannotReadOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanReadCanonicalOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title")
                        .value("Employee Management System API"))
                .andExpect(jsonPath("$.components.securitySchemes").exists());
    }

    @Test
    void anonymousApiCallIsUnauthorized() throws Exception {

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void registrationIsClosedToAnonymousUsers() throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"intruder\",\"password\":\"Password12345\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongCredentialsAreRejectedWithoutDetails() throws Exception {

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong-password-1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Invalid credentials or account unavailable."));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotOpenStaffPagesOrTheStaffApi() throws Exception {

        mockMvc.perform(get("/employees")).andExpect(status().isForbidden());
        mockMvc.perform(get("/payroll")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/employees")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/payslips/1")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeCanReachOwnSelfServiceApiWithoutAccessingStaffApis() throws Exception {

        // The test user is not seeded in this test database, so the controller
        // correctly returns 404 after authorization instead of a security 403.
        mockMvc.perform(get("/api/me/profile"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/me/payroll/1/payslip"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isForbidden());
    }


    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeeAnnouncementsEndpointIsReachableWithoutStaffPrivileges() throws Exception {

        mockMvc.perform(get("/api/me/announcements"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void employeePerformanceEndpointRequiresALinkedEmployeeProfile() throws Exception {

        // The test user is not seeded. The endpoint resolves employee identity
        // from the authenticated principal rather than trusting employeeId input.
        mockMvc.perform(get("/api/me/performance")
                        .param("employeeId", "999"))
                .andExpect(status().isNotFound());
    }


    @Test
    @Transactional
    @WithMockUser(username = "employee-a@example.test", roles = "EMPLOYEE")
    void employeePerformanceEndpointReturnsOnlyTheAuthenticatedEmployeesReviews()
            throws Exception {

        Department department = departmentRepository.save(Department.builder()
                .name("Cutover Security Test")
                .code("CUTOVER-SEC")
                .build());

        Employee employeeA = employeeRepository.save(Employee.builder()
                .firstName("Employee")
                .lastName("A")
                .email("employee-a@example.test")
                .phoneNumber("+254700000101")
                .salary(new BigDecimal("50000.00"))
                .hireDate(LocalDate.of(2025, 1, 1))
                .department(department)
                .build());

        Employee employeeB = employeeRepository.save(Employee.builder()
                .firstName("Employee")
                .lastName("B")
                .email("employee-b@example.test")
                .phoneNumber("+254700000102")
                .salary(new BigDecimal("60000.00"))
                .hireDate(LocalDate.of(2025, 1, 1))
                .department(department)
                .build());

        userRepository.save(User.builder()
                .employee(employeeA)
                .username("employee-a@example.test")
                .password("not-used-in-mock-authentication")
                .role(Role.ROLE_EMPLOYEE)
                .enabled(true)
                .build());

        performanceRepository.save(PerformanceReview.builder()
                .employee(employeeA)
                .reviewDate(LocalDate.of(2026, 10, 1))
                .reviewer("HR")
                .rating(5)
                .comments("Employee A private review")
                .goals("Employee A goals")
                .build());

        performanceRepository.save(PerformanceReview.builder()
                .employee(employeeB)
                .reviewDate(LocalDate.of(2026, 10, 1))
                .reviewer("HR")
                .rating(1)
                .comments("Employee B private review")
                .goals("Employee B goals")
                .build());

        mockMvc.perform(get("/api/me/performance")
                        .param("employeeId", employeeB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].employeeId")
                        .value(employeeA.getId()))
                .andExpect(jsonPath("$.content[0].comments")
                        .value("Employee A private review"));
    }

    @Test
    @WithMockUser(roles = "HR_MANAGER")
    void hrManagerCannotOpenAdministratorPages() throws Exception {

        mockMvc.perform(get("/users")).andExpect(status().isForbidden());
        mockMvc.perform(get("/system-logs")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteActionsRejectGetRequests() throws Exception {

        mockMvc.perform(get("/employees/delete/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void logoutRequiresACsrfToken() throws Exception {

        mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden());
    }
}
