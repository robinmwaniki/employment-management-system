package com.ems.controller;

import com.ems.dto.response.AttendanceResponse;
import com.ems.entity.Employee;
import com.ems.entity.User;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies that employee attendance self-service always uses the authenticated
 * employee profile rather than accepting an employee identifier from a client.
 */
@ExtendWith(MockitoExtension.class)
class MeControllerAttendanceTest {

    @Mock private UserRepository userRepository;
    @Mock private AnnouncementService announcementService;
    @Mock private PerformanceService performanceService;
    @Mock private EmployeeService employeeService;
    @Mock private PhotoService photoService;
    @Mock private LeaveService leaveService;
    @Mock private AttendanceService attendanceService;
    @Mock private PayrollService payrollService;
    @Mock private PdfService pdfService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private MeController meController;

    @Test
    void attendanceHistoryUsesAuthenticatedEmployeeAndRequestedPagination() {
        Long employeeId = 10L;
        Authentication authentication = authenticationFor("employee.user");
        Page<AttendanceResponse> expected = Page.empty();
        when(userRepository.findByUsername("employee.user"))
                .thenReturn(Optional.of(user("employee.user", employeeId)));
        when(attendanceService.getEmployeeAttendance(employeeId, 2, 5))
                .thenReturn(expected);

        Page<AttendanceResponse> actual =
                meController.myAttendance(authentication, 2, 5);

        assertSame(expected, actual);
        verify(attendanceService).getEmployeeAttendance(employeeId, 2, 5);
    }

    @Test
    void checkInBuildsRequestForAuthenticatedEmployee() {
        Long employeeId = 10L;
        Authentication authentication = authenticationFor("employee.user");
        AttendanceResponse expected = mock(AttendanceResponse.class);
        when(userRepository.findByUsername("employee.user"))
                .thenReturn(Optional.of(user("employee.user", employeeId)));
        when(attendanceService.checkIn(org.mockito.ArgumentMatchers.any()))
                .thenReturn(expected);

        AttendanceResponse actual = meController.checkIn(authentication);

        assertSame(expected, actual);
        ArgumentCaptor<com.ems.dto.request.AttendanceRequest> requestCaptor =
                ArgumentCaptor.forClass(com.ems.dto.request.AttendanceRequest.class);
        verify(attendanceService).checkIn(requestCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                employeeId, requestCaptor.getValue().getEmployeeId());
    }

    @Test
    void checkOutUsesAuthenticatedEmployee() {
        Long employeeId = 10L;
        Authentication authentication = authenticationFor("employee.user");
        AttendanceResponse expected = mock(AttendanceResponse.class);
        when(userRepository.findByUsername("employee.user"))
                .thenReturn(Optional.of(user("employee.user", employeeId)));
        when(attendanceService.checkOut(employeeId)).thenReturn(expected);

        AttendanceResponse actual = meController.checkOut(authentication);

        assertSame(expected, actual);
        verify(attendanceService).checkOut(employeeId);
    }

    @Test
    void attendanceActionsDoNotProceedWithoutLinkedEmployeeProfile() {
        Authentication authentication = authenticationFor("orphan.user");
        when(userRepository.findByUsername("orphan.user"))
                .thenReturn(Optional.of(User.builder()
                        .username("orphan.user")
                        .employee(null)
                        .build()));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.ems.exception.ResourceNotFoundException.class,
                () -> meController.checkIn(authentication));

        verifyNoInteractions(attendanceService);
    }

    private User user(String username, Long employeeId) {
        return User.builder()
                .username(username)
                .employee(Employee.builder().id(employeeId).build())
                .build();
    }

    private Authentication authenticationFor(String username) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(username);
        return authentication;
    }
}
