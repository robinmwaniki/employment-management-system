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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies employee photo upload and download through the real bearer-token
 * security filter, self-service controller and disposable PostgreSQL database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MePhotoBearerIntegrationTest {

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
    void employeeCanUploadAndDownloadOnlyItsOwnPhoto() throws Exception {

        Department department = departmentRepository.saveAndFlush(
                Department.builder()
                        .name("Photo Integration Department")
                        .code("PID")
                        .build());

        Employee photoOwner = createEmployee(
                department,
                "Photo",
                "Owner",
                "photo-owner@example.test",
                "+254700000201");
        Employee otherEmployee = createEmployee(
                department,
                "Photo",
                "Other",
                "photo-other@example.test",
                "+254700000202");

        createEmployeeUser(photoOwner, "photo-owner@example.test");
        createEmployeeUser(otherEmployee, "photo-other@example.test");

        String ownerToken = tokenFor("photo-owner@example.test");
        String otherToken = tokenFor("photo-other@example.test");

        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "profile.png",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                PNG_SIGNATURE);

        mockMvc.perform(multipart("/api/me/photo")
                        .file(upload)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        MvcResult ownPhoto = mockMvc.perform(get("/api/me/photo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        org.junit.jupiter.api.Assertions.assertEquals(
                MediaType.IMAGE_PNG_VALUE,
                ownPhoto.getResponse().getContentType());
        assertArrayEquals(PNG_SIGNATURE, ownPhoto.getResponse().getContentAsByteArray());

        mockMvc.perform(get("/api/me/photo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    private Employee createEmployee(
            Department department,
            String firstName,
            String lastName,
            String email,
            String phoneNumber) {

        return employeeRepository.saveAndFlush(Employee.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phoneNumber(phoneNumber)
                .salary(new BigDecimal("50000.00"))
                .hireDate(LocalDate.of(2025, 1, 1))
                .department(department)
                .build());
    }

    private void createEmployeeUser(Employee employee, String username) {

        userRepository.saveAndFlush(User.builder()
                .employee(employee)
                .username(username)
                .password("test-only-not-used-for-bearer-authentication")
                .role(Role.ROLE_EMPLOYEE)
                .enabled(true)
                .build());
    }

    private String tokenFor(String username) {

        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return jwtService.generateToken(userDetails);
    }
}
