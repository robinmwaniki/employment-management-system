package com.ems.web;

import com.ems.dto.request.AttendanceRequest;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.UserRepository;
import com.ems.service.interfaces.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Attendance self-service for the signed-in employee. The employee is always
 * taken from the authenticated account, never from the request.
 */
@Controller
@RequiredArgsConstructor
public class EmployeeAttendanceController {

    private final AttendanceService attendanceService;
    private final UserRepository userRepository;

    @GetMapping("/employee/attendance")
    public String attendance(
            Authentication authentication,
            Model model) {

        Page<?> attendance =
                attendanceService.getEmployeeAttendance(
                        currentEmployeeId(authentication),
                        0,
                        20);

        model.addAttribute("attendance", attendance);

        return "employee-attendance";
    }

    @PostMapping("/employee/check-in")
    public String checkIn(Authentication authentication) {

        AttendanceRequest request = new AttendanceRequest();

        request.setEmployeeId(currentEmployeeId(authentication));

        attendanceService.checkIn(request);

        return "redirect:/employee/attendance";
    }

    @PostMapping("/employee/check-out")
    public String checkOut(Authentication authentication) {

        attendanceService.checkOut(currentEmployeeId(authentication));

        return "redirect:/employee/attendance";
    }

    /**
     * Resolves the employee profile linked to the signed-in account.
     */
    private Long currentEmployeeId(Authentication authentication) {

        var user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));

        if (user.getEmployee() == null) {
            throw new ResourceNotFoundException(
                    "No employee profile is linked to this account.");
        }

        return user.getEmployee().getId();
    }

}
