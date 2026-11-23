package com.ems;

import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.entity.Role;
import com.ems.entity.User;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.UserRepository;
import com.ems.security.jwt.JwtService;
import com.ems.security.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the staff React employee-photo API using real JWT bearer tokens.
 * HR/admin staff may upload and read employee photos; employees cannot use
 * another employee's staff-management photo endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StaffEmployeePhotoBearerIntegrationTest {

    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtService jwtService;

    @Test
    @Transactional
    void staffCanUploadAndReadEmployeePhotoWhileEmployeeCannotUseStaffEndpoint()
            throws Exception {

        Department department = departmentRepository.saveAndFlush(
                Department.builder()
                        .name("Staff Photo Integration Department")
                        .code("SPID")
                        .build());

        Employee employee = employeeRepository.saveAndFlush(Employee.builder()
                .firstName("Staff")
                .lastName("Photo")
                .email("staff-photo-employee@example.test")
                .phoneNumber("+254700000901")
                .salary(new BigDecimal("50000.00"))
                .hireDate(LocalDate.of(2025, 1, 1))
                .department(department)
                .build());

        createUser("staff-photo-admin@example.test", Role.ROLE_ADMIN, null);
        createUser("staff-photo-hr@example.test", Role.ROLE_HR_MANAGER, null);
        createUser("staff-photo-employee@example.test", Role.ROLE_EMPLOYEE, employee);

        String adminToken = tokenFor("staff-photo-admin@example.test");
        String hrToken = tokenFor("staff-photo-hr@example.test");
        String employeeToken = tokenFor("staff-photo-employee@example.test");

        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "employee.png",
                MediaType.IMAGE_PNG_VALUE,
                PNG_SIGNATURE);

        mockMvc.perform(multipart("/api/employees/{id}/photo", employee.getId())
                        .file(upload)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        MvcResult hrPhoto = mockMvc.perform(get(
                                "/api/employees/{id}/photo", employee.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hrToken)))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(MediaType.IMAGE_PNG_VALUE,
                hrPhoto.getResponse().getContentType());
        assertArrayEquals(PNG_SIGNATURE, hrPhoto.getResponse().getContentAsByteArray());

        mockMvc.perform(get("/api/employees/{id}/photo", employee.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/employees/{id}/photo", employee.getId())
                        .file(upload)
                        .header(HttpHeaders.AUTHORIZATION, bearer(employeeToken)))
                .andExpect(status().isForbidden());
    }

    private void createUser(String username, Role role, Employee employee) {
        userRepository.saveAndFlush(User.builder()
                .employee(employee)
                .username(username)
                .password("test-only-not-used-for-bearer-authentication")
                .role(role)
                .enabled(true)
                .build());
    }

    private String tokenFor(String username) {
        return "Bearer " + jwtService.generateToken(
                userDetailsService.loadUserByUsername(username));
    }

    private String bearer(String token) {
        return token;
    }
}
