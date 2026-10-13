package com.ems.service.interfaces;

import com.ems.dto.response.NotificationResponse;

import java.util.List;

public interface NotificationService {

    /**
     * Creates a notification.
     *
     * @param employeeId the employee it is addressed to, or null for staff only
     */
    NotificationResponse create(
            Long employeeId,
            String title,
            String message);

    /** Staff view: every notification. */
    List<NotificationResponse> getLatestNotifications();

    long getUnreadCount();

    void markAsRead(Long id);

    void markAllAsRead();

    /** Employee view: only the employee's own notifications. */
    List<NotificationResponse> getLatestNotificationsForEmployee(Long employeeId);

    long getUnreadCountForEmployee(Long employeeId);

    void markAsReadForEmployee(Long id, Long employeeId);

    void markAllAsReadForEmployee(Long employeeId);

}
