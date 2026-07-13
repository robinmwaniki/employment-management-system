package com.ems.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class PerformanceResponse {

    private Long id;

    private Long employeeId;

    private String employeeName;

    private LocalDate reviewDate;

    private String reviewer;

    private Integer rating;

    private String comments;

    private String goals;

}