package com.ems.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PerformanceRequest {

    @NotNull
    private Long employeeId;

    @NotNull
    @Min(1)
    @Max(5)
    private Integer score;

    @NotBlank
    private String managerComments;

    private String employeeComments;

}