package com.ems.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class AnnouncementResponse {

    private Long id;

    private String title;

    private String message;

    private LocalDate publishDate;

    private LocalDate expiryDate;

}
