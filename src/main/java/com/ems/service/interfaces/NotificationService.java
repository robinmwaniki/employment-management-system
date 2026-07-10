package com.ems.service.interfaces;

import com.ems.dto.response.NotificationResponse;

import java.util.List;

public interface NotificationService {

    NotificationResponse create(
            String title,
            String message);

    List<NotificationResponse> getLatestNotifications();

    long getUnreadCount();

    void markAsRead(Long id);

    void markAllAsRead();

}