package com.ems.web;

import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.UserRepository;
import com.ems.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Notifications for the signed-in employee. Only their own are ever shown or changed.
 */
@Controller
@RequiredArgsConstructor
public class EmployeeNotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @GetMapping("/employee/notifications")
    public String employeeNotifications(
            Authentication authentication,
            Model model) {

        Long employeeId = currentEmployeeId(authentication);

        model.addAttribute(
                "notifications",
                notificationService.getLatestNotificationsForEmployee(employeeId));

        model.addAttribute(
                "unreadCount",
                notificationService.getUnreadCountForEmployee(employeeId));

        return "employee-notifications";
    }

    @PostMapping("/employee/notifications/read/{id}")
    public String markAsRead(
            Authentication authentication,
            @PathVariable Long id) {

        notificationService.markAsReadForEmployee(
                id,
                currentEmployeeId(authentication));

        return "redirect:/employee/notifications";
    }

    @PostMapping("/employee/notifications/read-all")
    public String markAllAsRead(Authentication authentication) {

        notificationService.markAllAsReadForEmployee(
                currentEmployeeId(authentication));

        return "redirect:/employee/notifications";
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
