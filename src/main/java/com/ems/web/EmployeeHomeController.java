package com.ems.web;

import com.ems.repository.AnnouncementRepository;
import com.ems.repository.AttendanceRepository;
import com.ems.repository.LeaveRepository;
import com.ems.repository.PayrollRepository;
import com.ems.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class EmployeeHomeController {

    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRepository leaveRepository;
    private final PayrollRepository payrollRepository;
    private final AnnouncementRepository announcementRepository;

    @GetMapping("/employee/dashboard")
    public String dashboard(Authentication authentication,
                            Model model) {

        String username = authentication.getName();

        model.addAttribute("username", username);

        model.addAttribute("announcements",
                announcementRepository.findAll());

        return "employee-dashboard";
    }

}