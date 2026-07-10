package com.ems.mapper;

import com.ems.dto.response.NotificationResponse;
import com.ems.entity.Notification;

public class NotificationMapper {

    public static NotificationResponse toResponse(
            Notification notification){

        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .read(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();

    }

}