package com.ems.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemLogResponse {

    private Long id;

    private String username;

    private String role;

    private String action;

    private String description;

    private LocalDateTime createdAt;

}