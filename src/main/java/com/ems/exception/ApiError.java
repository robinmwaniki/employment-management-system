package com.ems.exception;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ApiError {

    private boolean success;

    private String message;

    private LocalDateTime timestamp;

}