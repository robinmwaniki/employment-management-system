package com.ems.web;

import com.ems.service.interfaces.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class EmployeeAnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping("/employee/announcements")
    public String announcements(Model model) {

        model.addAttribute(
                "announcements",
                announcementService.getLatestAnnouncements());

        return "employee-announcements";
    }

}