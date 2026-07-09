package com.ems.web;

import com.ems.dto.request.AttendanceRequest;
import com.ems.entity.User;
import com.ems.repository.UserRepository;
import com.ems.service.interfaces.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class EmployeeAttendanceController {

    private final AttendanceService attendanceService;
    private final UserRepository userRepository;

    @GetMapping("/employee/attendance")
    public String attendance(
            Authentication authentication,
            Model model) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long employeeId = user.getEmployee().getId();

        Page<?> attendance =
                attendanceService.getEmployeeAttendance(
                        employeeId,
                        0,
                        20);

        model.addAttribute("attendance", attendance);

        return "employee-attendance";
    }

    @PostMapping("/employee/check-in")
    public String checkIn(Authentication authentication) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        AttendanceRequest request = new AttendanceRequest();

        request.setEmployeeId(user.getEmployee().getId());

        attendanceService.checkIn(request);

        return "redirect:/employee/attendance";
    }

    @PostMapping("/employee/check-out")
    public String checkOut(Authentication authentication) {

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        attendanceService.checkOut(user.getEmployee().getId());

        return "redirect:/employee/attendance";
    }

}