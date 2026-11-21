package com.ems.controller;

import com.ems.entity.Employee;
import com.ems.entity.User;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.UserRepository;
import com.ems.service.interfaces.AnnouncementService;
import com.ems.service.interfaces.AttendanceService;
import com.ems.service.interfaces.EmployeeService;
import com.ems.service.interfaces.LeaveService;
import com.ems.service.interfaces.NotificationService;
import com.ems.service.interfaces.PayrollService;
import com.ems.service.interfaces.PdfService;
import com.ems.service.interfaces.PerformanceService;
import com.ems.service.interfaces.PhotoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression tests for employee-scoped payslip downloads.
 */
@ExtendWith(MockitoExtension.class)
class MeControllerPayslipOwnershipTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AnnouncementService announcementService;

    @Mock
    private PerformanceService performanceService;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private PhotoService photoService;

    @Mock
    private LeaveService leaveService;

    @Mock
    private AttendanceService attendanceService;

    @Mock
    private PayrollService payrollService;

    @Mock
    private PdfService pdfService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private MeController meController;

    @Test
    void employeeCanDownloadOwnPayslip() {
        Long employeeId = 10L;
        Long payrollId = 501L;
        byte[] pdfBytes = new byte[] {'%', 'P', 'D', 'F'};
        Authentication authentication = authenticationFor("employee.user");
        Employee employee = Employee.builder().id(employeeId).build();
        User user = User.builder()
                .username("employee.user")
                .employee(employee)
                .build();

        when(userRepository.findByUsername("employee.user"))
                .thenReturn(Optional.of(user));
        when(pdfService.generatePayslip(payrollId)).thenReturn(pdfBytes);

        var response = meController.downloadMyPayslip(authentication, payrollId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.APPLICATION_PDF, response.getHeaders().getContentType());
        assertEquals("attachment; filename=\"Payslip-501.pdf\"",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals(pdfBytes, response.getBody());

        verify(payrollService).getEmployeePayroll(employeeId, payrollId);
        verify(pdfService).generatePayslip(payrollId);
    }

    @Test
    void employeeCannotDownloadAnotherEmployeesPayslip() {
        Long employeeId = 10L;
        Long payrollId = 501L;
        Authentication authentication = authenticationFor("employee.user");
        Employee employee = Employee.builder().id(employeeId).build();
        User user = User.builder()
                .username("employee.user")
                .employee(employee)
                .build();

        when(userRepository.findByUsername("employee.user"))
                .thenReturn(Optional.of(user));
        when(payrollService.getEmployeePayroll(employeeId, payrollId))
                .thenThrow(new ResourceNotFoundException(
                        "Payroll does not belong to this employee."));

        assertThrows(ResourceNotFoundException.class,
                () -> meController.downloadMyPayslip(authentication, payrollId));

        verify(payrollService).getEmployeePayroll(employeeId, payrollId);
        verifyNoInteractions(pdfService);
    }

    @Test
    void downloadDoesNotProceedWhenAuthenticatedUserHasNoEmployeeProfile() {
        Authentication authentication = authenticationFor("orphan.user");
        User user = User.builder()
                .username("orphan.user")
                .employee(null)
                .build();

        when(userRepository.findByUsername("orphan.user"))
                .thenReturn(Optional.of(user));

        assertThrows(ResourceNotFoundException.class,
                () -> meController.downloadMyPayslip(authentication, 501L));

        verifyNoInteractions(payrollService, pdfService);
    }

    private Authentication authenticationFor(String username) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(username);
        return authentication;
    }
}
