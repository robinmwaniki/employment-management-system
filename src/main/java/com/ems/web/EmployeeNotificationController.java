package com.ems.web;

import com.ems.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class EmployeeNotificationController {

    private final NotificationService notificationService;

    @GetMapping("/employee/notifications")
    public String employeeNotifications(Model model) {

        model.addAttribute(
                "notifications",
                notificationService.getLatestNotifications());

        model.addAttribute(
                "unreadCount",
                notificationService.getUnreadCount());

        return "employee-notifications";
    }

    @GetMapping("/employee/notifications/read/{id}")
    public String markAsRead(@PathVariable Long id) {

        notificationService.markAsRead(id);

        return "redirect:/employee/notifications";
    }

    @GetMapping("/employee/notifications/read-all")
    public String markAllAsRead() {

        notificationService.markAllAsRead();

        return "redirect:/employee/notifications";
    }

}