package com.ems.service.impl;

import com.ems.dto.response.NotificationResponse;
import com.ems.entity.Employee;
import com.ems.entity.Notification;
import com.ems.exception.ResourceNotFoundException;
import com.ems.mapper.NotificationMapper;
import com.ems.repository.EmployeeRepository;
import com.ems.repository.NotificationRepository;
import com.ems.service.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public NotificationResponse create(
            Long employeeId,
            String title,
            String message) {

        Employee employee = employeeId == null
                ? null
                : employeeRepository.getReferenceById(employeeId);

        Notification notification = Notification.builder()
                .employee(employee)
                .title(title)
                .message(message)
                .isRead(false)
                .build();

        return NotificationMapper.toResponse(
                notificationRepository.save(notification));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getLatestNotifications() {

        return notificationRepository
                .findTop10ByOrderByCreatedAtDesc()
                .stream()
                .map(NotificationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount() {

        return notificationRepository.countByIsReadFalse();
    }

    @Override
    public void markAsRead(Long id) {

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Notification not found"));

        notification.setRead(true);
    }

    @Override
    public void markAllAsRead() {

        notificationRepository.markAllAsRead();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getLatestNotificationsForEmployee(
            Long employeeId) {

        return notificationRepository
                .findTop10ByEmployeeIdOrderByCreatedAtDesc(employeeId)
                .stream()
                .map(NotificationMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCountForEmployee(Long employeeId) {

        return notificationRepository
                .countByEmployeeIdAndIsReadFalse(employeeId);
    }

    /**
     * An employee can only mark their own notifications; anyone else's is
     * reported as not found.
     */
    @Override
    public void markAsReadForEmployee(Long id, Long employeeId) {

        Notification notification = notificationRepository
                .findByIdAndEmployeeId(id, employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Notification not found"));

        notification.setRead(true);
    }

    @Override
    public void markAllAsReadForEmployee(Long employeeId) {

        notificationRepository.markAllAsReadForEmployee(employeeId);
    }
}
