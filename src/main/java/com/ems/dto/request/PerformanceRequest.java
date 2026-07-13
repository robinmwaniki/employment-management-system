package com.ems.dto.request;

import lombok.Data;

import java.time.LocalDate;

@Data
public class PerformanceRequest {

    private Long employeeId;

    private LocalDate reviewDate;

    private String reviewer;

    private Integer rating;

    private String comments;

    private String goals;

}