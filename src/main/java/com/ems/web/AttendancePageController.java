package com.ems.web;

import com.ems.service.interfaces.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AttendancePageController {

    private final AttendanceService attendanceService;

    @GetMapping("/attendance")
    public String attendance(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        model.addAttribute(
                "attendance",
                attendanceService.getAllAttendance(page, size));

        return "attendance";
    }

}