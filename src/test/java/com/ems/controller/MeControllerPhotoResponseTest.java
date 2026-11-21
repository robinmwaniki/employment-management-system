package com.ems.controller;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the employee self-service photo response contract.
 */
@ExtendWith(MockitoExtension.class)
class MeControllerPhotoResponseTest {

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
    void employeeReceivesOwnPhotoBytesAndDetectedContentType() {
        Long employeeId = 10L;
        byte[] photoBytes = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01};
        Authentication authentication = authenticationFor("employee.user");
        when(userRepository.findByUsername("employee.user"))
                .thenReturn(Optional.of(User.builder()
                        .username("employee.user")
                        .employee(Employee.builder().id(employeeId).build())
                        .build()));
        when(photoService.findPhoto(employeeId))
                .thenReturn(Optional.of(new PhotoService.PhotoContent("image/jpeg", photoBytes)));

        var response = meController.photo(authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
        assertArrayEquals(photoBytes, response.getBody());
        verify(photoService).findPhoto(employeeId);
    }

    @Test
    void missingPhotoReturnsNotFoundForAuthenticatedEmployee() {
        Long employeeId = 10L;
        Authentication authentication = authenticationFor("employee.user");
        when(userRepository.findByUsername("employee.user"))
                .thenReturn(Optional.of(User.builder()
                        .username("employee.user")
                        .employee(Employee.builder().id(employeeId).build())
                        .build()));
        when(photoService.findPhoto(employeeId)).thenReturn(Optional.empty());

        var response = meController.photo(authentication);

        assertEquals(404, response.getStatusCode().value());
        verify(photoService).findPhoto(employeeId);
    }

    private Authentication authenticationFor(String username) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(username);
        return authentication;
    }
}
