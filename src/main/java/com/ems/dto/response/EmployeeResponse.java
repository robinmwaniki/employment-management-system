package com.ems.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class EmployeeResponse {

    private Long id;

    private String firstName;

    private String lastName;

    private String email;

    private String phoneNumber;

    private BigDecimal salary;

    private LocalDate hireDate;

    private Long departmentId;

    private String departmentName;

    private String profilePhoto;
}