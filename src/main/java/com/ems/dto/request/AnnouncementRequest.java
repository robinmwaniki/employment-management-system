package com.ems.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AnnouncementRequest {

    private String title;

    private String message;

    private LocalDate publishDate;

    private LocalDate expiryDate;

}
