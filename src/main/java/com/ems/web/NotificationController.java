package com.ems.web;

import com.ems.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public String notifications(Model model) {

        model.addAttribute(
                "notifications",
                notificationService.getLatestNotifications());

        model.addAttribute(
                "unreadCount",
                notificationService.getUnreadCount());

        return "notifications";
    }

    @GetMapping("/read/{id}")
    public String markAsRead(
            @PathVariable Long id) {

        notificationService.markAsRead(id);

        return "redirect:/notifications";
    }

    @GetMapping("/read-all")
    public String readAll() {

        notificationService.markAllAsRead();

        return "redirect:/notifications";
    }

}