package com.ems.dto.response;

import com.ems.entity.AttendanceStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AttendanceResponse {

    private Long id;

    private Long employeeId;

    private String employeeName;

    private LocalDate attendanceDate;

    private LocalDateTime checkInTime;

    private LocalDateTime checkOutTime;

    private Duration workingHours;

    private boolean late;

    private boolean overtime;

    private AttendanceStatus status;

}