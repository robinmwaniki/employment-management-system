package com.ems.controller;

import com.ems.dto.request.LeaveRequest;
import com.ems.dto.request.ProfileUpdateRequest;
import com.ems.dto.response.AnnouncementResponse;
import com.ems.dto.response.PerformanceResponse;
import com.ems.dto.response.AttendanceResponse;
import com.ems.dto.response.EmployeeResponse;
import com.ems.dto.response.LeaveResponse;
import com.ems.dto.response.NotificationResponse;
import com.ems.dto.response.PayrollResponse;
import com.ems.entity.User;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.UserRepository;
import com.ems.service.interfaces.AnnouncementService;
import com.ems.service.interfaces.PerformanceService;
import com.ems.service.interfaces.AttendanceService;
import com.ems.service.interfaces.EmployeeService;
import com.ems.service.interfaces.LeaveService;
import com.ems.service.interfaces.NotificationService;
import com.ems.service.interfaces.PayrollService;
import com.ems.service.interfaces.PdfService;
import com.ems.service.interfaces.PhotoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * Self-service endpoints for the signed-in employee.
 * The employee identity is always taken from the JWT, never from the request.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {

    private final UserRepository userRepository;
    private final AnnouncementService announcementService;
    private final PerformanceService performanceService;
    private final EmployeeService employeeService;
    private final PhotoService photoService;
    private final LeaveService leaveService;
    private final AttendanceService attendanceService;
    private final PayrollService payrollService;
    private final PdfService pdfService;
    private final NotificationService notificationService;

    // ----- Employee announcements and performance -----

    /** Returns the announcements visible to the signed-in employee. */
    @GetMapping("/announcements")
    public List<AnnouncementResponse> myAnnouncements() {

        LocalDate today = LocalDate.now();
        return announcementService.getLatestAnnouncements().stream()
                .filter(announcement -> announcement.getPublishDate() == null
                        || !announcement.getPublishDate().isAfter(today))
                .filter(announcement -> announcement.getExpiryDate() == null
                        || !announcement.getExpiryDate().isBefore(today))
                .toList();
    }

    /**
     * Returns only the signed-in employee's performance reviews. The employee
     * identifier is resolved from the authenticated principal, never supplied
     * by the browser.
     */
    @GetMapping("/performance")
    public Page<PerformanceResponse> myPerformance(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return performanceService.getEmployeeReviews(
                currentEmployeeId(authentication),
                page,
                size);
    }

    // ----- Profile -----

    @GetMapping("/profile")
    public EmployeeResponse profile(Authentication authentication) {

        return employeeService.getEmployeeById(currentEmployeeId(authentication));
    }

    @PutMapping("/profile")
    public EmployeeResponse updateProfile(
            Authentication authentication,
            @Valid @RequestBody ProfileUpdateRequest request) {

        return employeeService.updateOwnProfile(
                currentEmployeeId(authentication),
                request);
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadPhoto(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {

        photoService.storePhoto(currentEmployeeId(authentication), file);
    }

    @GetMapping("/photo")
    public ResponseEntity<byte[]> photo(Authentication authentication) {

        Long employeeId = currentEmployeeId(authentication);

        return photoService.findPhoto(employeeId)
                .map(photo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(photo.contentType()))
                        .body(photo.data()))
                .orElse(ResponseEntity.notFound().build());
    }

    // ----- Leave -----

    @GetMapping("/leave")
    public Page<LeaveResponse> myLeave(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return leaveService.getEmployeeLeaves(
                currentEmployeeId(authentication),
                page,
                size);
    }

    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveResponse applyLeave(
            Authentication authentication,
            @Valid @RequestBody LeaveRequest request) {

        request.setEmployeeId(currentEmployeeId(authentication));

        return leaveService.applyLeave(request);
    }

    @PostMapping("/leave/{id}/cancel")
    public LeaveResponse cancelLeave(
            Authentication authentication,
            @PathVariable Long id) {

        return leaveService.cancelOwnLeave(id, currentEmployeeId(authentication));
    }

    // ----- Attendance -----

    @GetMapping("/attendance")
    public Page<AttendanceResponse> myAttendance(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return attendanceService.getEmployeeAttendance(
                currentEmployeeId(authentication),
                page,
                size);
    }

    @PostMapping("/attendance/check-in")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceResponse checkIn(Authentication authentication) {

        var request = new com.ems.dto.request.AttendanceRequest();

        request.setEmployeeId(currentEmployeeId(authentication));

        return attendanceService.checkIn(request);
    }

    @PostMapping("/attendance/check-out")
    public AttendanceResponse checkOut(Authentication authentication) {

        return attendanceService.checkOut(currentEmployeeId(authentication));
    }

    // ----- Payroll -----

    @GetMapping("/payroll")
    public Page<PayrollResponse> myPayroll(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {

        return payrollService.getEmployeePayrolls(
                currentEmployeeId(authentication),
                page,
                size);
    }

    /**
     * Generates a payslip only after verifying that the requested payroll
     * record belongs to the employee identified by the authenticated token.
     */
    @GetMapping("/payroll/{payrollId}/payslip")
    public ResponseEntity<byte[]> downloadMyPayslip(
            Authentication authentication,
            @PathVariable Long payrollId) {

        Long employeeId = currentEmployeeId(authentication);

        // This service method enforces employee ownership and returns 404
        // when the record is absent or belongs to another employee.
        payrollService.getEmployeePayroll(employeeId, payrollId);

        byte[] pdf = pdfService.generatePayslip(payrollId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename("Payslip-" + payrollId + ".pdf")
                        .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }

    // ----- Notifications -----

    @GetMapping("/notifications")
    public List<NotificationResponse> myNotifications(
            Authentication authentication) {

        return notificationService.getLatestNotificationsForEmployee(
                currentEmployeeId(authentication));
    }

    @GetMapping("/notifications/unread-count")
    public long unreadCount(Authentication authentication) {

        return notificationService.getUnreadCountForEmployee(
                currentEmployeeId(authentication));
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(
            Authentication authentication,
            @PathVariable Long id) {

        notificationService.markAsReadForEmployee(
                id,
                currentEmployeeId(authentication));
    }

    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(Authentication authentication) {

        notificationService.markAllAsReadForEmployee(
                currentEmployeeId(authentication));
    }

    // ----- Helper -----

    private Long currentEmployeeId(Authentication authentication) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        if (user.getEmployee() == null) {
            throw new ResourceNotFoundException(
                    "No employee profile is linked to this account.");
        }

        return user.getEmployee().getId();
    }
}
