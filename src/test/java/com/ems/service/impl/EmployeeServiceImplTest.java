package com.ems.service.impl;

import com.ems.dto.request.EmployeeRequest;
import com.ems.dto.response.EmployeeResponse;
import com.ems.entity.Department;
import com.ems.entity.Employee;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.DepartmentRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.service.interfaces.SystemLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for employee creation, updates, and deletion business rules.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private SystemLogService systemLogService;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    private Department department;

    @BeforeEach
    void setUp() {
        department = Department.builder()
                .id(3L)
                .name("Engineering")
                .code("ENG")
                .build();
    }

    @Test
    void createEmployee_withValidRequest_savesEmployeeAndReturnsResponse() {
        EmployeeRequest request = employeeRequest();

        when(employeeRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(employeeRepository.existsByPhoneNumber(request.getPhoneNumber())).thenReturn(false);
        when(departmentRepository.findById(3L)).thenReturn(Optional.of(department));
        when(employeeRepository.saveAndFlush(any(Employee.class))).thenAnswer(invocation -> {
            Employee employee = invocation.getArgument(0);
            employee.setId(10L);
            return employee;
        });

        EmployeeResponse response = employeeService.createEmployee(request);

        assertEquals(10L, response.getId());
        assertEquals("Jane", response.getFirstName());
        assertEquals("jane@example.com", response.getEmail());
        assertEquals(3L, response.getDepartmentId());
        assertEquals("Engineering", response.getDepartmentName());
        verify(systemLogService).saveLog(
                "HR Admin", "ADMIN", "CREATE EMPLOYEE", "Created employee Jane Doe");
    }

    @Test
    void createEmployee_withExistingEmail_throwsIllegalArgumentException() {
        EmployeeRequest request = employeeRequest();
        when(employeeRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> employeeService.createEmployee(request));

        verify(employeeRepository, never()).save(any(Employee.class));
        verify(departmentRepository, never()).findById(any());
    }

    @Test
    void createEmployee_withExistingPhoneNumber_throwsIllegalArgumentException() {
        EmployeeRequest request = employeeRequest();
        when(employeeRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(employeeRepository.existsByPhoneNumber(request.getPhoneNumber())).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> employeeService.createEmployee(request));

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void createEmployee_withUnknownDepartment_throwsResourceNotFoundException() {
        EmployeeRequest request = employeeRequest();
        when(employeeRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(employeeRepository.existsByPhoneNumber(request.getPhoneNumber())).thenReturn(false);
        when(departmentRepository.findById(3L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> employeeService.createEmployee(request));

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void updateEmployee_withValidRequest_updatesEmployeeAndReturnsResponse() {
        Employee employee = existingEmployee();
        EmployeeRequest request = employeeRequest();
        request.setFirstName("Janet");
        request.setEmail("janet@example.com");

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(departmentRepository.findById(3L)).thenReturn(Optional.of(department));
        when(employeeRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(employeeRepository.findByPhoneNumber(request.getPhoneNumber())).thenReturn(Optional.empty());
        when(employeeRepository.saveAndFlush(employee)).thenReturn(employee);

        EmployeeResponse response = employeeService.updateEmployee(10L, request);

        assertEquals("Janet", response.getFirstName());
        assertEquals("janet@example.com", response.getEmail());
        verify(systemLogService).saveLog(
                "HR Admin", "ADMIN", "UPDATE EMPLOYEE", "Updated employee Janet Doe");
    }

    @Test
    void updateEmployee_whenEmployeeDoesNotExist_throwsResourceNotFoundException() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> employeeService.updateEmployee(404L, employeeRequest()));

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void updateEmployee_withEmailOwnedByAnotherEmployee_throwsIllegalArgumentException() {
        Employee employee = existingEmployee();
        EmployeeRequest request = employeeRequest();
        Employee otherEmployee = Employee.builder().id(99L).build();

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));
        when(departmentRepository.findById(3L)).thenReturn(Optional.of(department));
        when(employeeRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(otherEmployee));

        assertThrows(IllegalArgumentException.class,
                () -> employeeService.updateEmployee(10L, request));

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void deleteEmployee_whenEmployeeExists_deletesEmployeeAndWritesAuditLog() {
        Employee employee = existingEmployee();
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(employee));

        employeeService.deleteEmployee(10L);

        verify(systemLogService).saveLog(
                "HR Admin", "ADMIN", "DELETE EMPLOYEE", "Deleted employee Jane Doe");
        verify(employeeRepository).delete(employee);
    }

    @Test
    void deleteEmployee_whenEmployeeDoesNotExist_throwsResourceNotFoundException() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> employeeService.deleteEmployee(404L));

        verify(employeeRepository, never()).delete(any(Employee.class));
    }

    private EmployeeRequest employeeRequest() {
        EmployeeRequest request = new EmployeeRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("jane@example.com");
        request.setPhoneNumber("0700000000");
        request.setSalary(new BigDecimal("75000.00"));
        request.setHireDate(LocalDate.of(2024, 1, 15));
        request.setDepartmentId(3L);
        return request;
    }

    private Employee existingEmployee() {
        return Employee.builder()
                .id(10L)
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .phoneNumber("0700000000")
                .salary(new BigDecimal("75000.00"))
                .hireDate(LocalDate.of(2024, 1, 15))
                .department(department)
                .build();
    }
}
