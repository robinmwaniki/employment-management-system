package com.ems.service.impl;

import com.ems.dto.response.NotificationResponse;
import com.ems.entity.Notification;
import com.ems.mapper.NotificationMapper;
import com.ems.repository.NotificationRepository;
import com.ems.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    public NotificationResponse create(String title, String message) {

        Notification notification = Notification.builder()
                .title(title)
                .message(message)
                .isRead(false)
                .build();

        return NotificationMapper.toResponse(
                notificationRepository.save(notification));
    }

    @Override
    public List<NotificationResponse> getLatestNotifications() {

        return notificationRepository
                .findTop10ByOrderByCreatedAtDesc()
                .stream()
                .map(NotificationMapper::toResponse)
                .toList();
    }

    @Override
    public long getUnreadCount() {

        return notificationRepository.countByIsReadFalse();
    }

    @Override
    public void markAsRead(Long id) {

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Notification not found"));

        notification.setRead(true);

        notificationRepository.save(notification);
    }

    @Override
    public void markAllAsRead() {

        notificationRepository.findAll().forEach(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
    }
}