package com.ems.mapper;

import com.ems.dto.request.AnnouncementRequest;
import com.ems.dto.response.AnnouncementResponse;
import com.ems.entity.Announcement;

public class AnnouncementMapper {

    public static Announcement toEntity(AnnouncementRequest request) {

        return Announcement.builder()
                .title(request.getTitle())
                .message(request.getMessage())
                .publishDate(request.getPublishDate())
                .expiryDate(request.getExpiryDate())
                .build();
    }

    public static AnnouncementResponse toResponse(Announcement announcement) {

        return AnnouncementResponse.builder()
                .id(announcement.getId())
                .title(announcement.getTitle())
                .message(announcement.getMessage())
                .publishDate(announcement.getPublishDate())
                .expiryDate(announcement.getExpiryDate())
                .build();
    }

}