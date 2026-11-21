package com.ems.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmployeeRequest {
    @NotBlank(message = "First name is required") private String firstName;
    @NotBlank(message = "Last name is required") private String lastName;
    @Email(message = "Invalid email address")
    @NotBlank(message = "Email is required") private String email;
    @NotBlank(message = "Phone number is required") private String phoneNumber;
    @NotNull(message = "Salary is required")
    @Positive(message = "Salary must be greater than zero") private BigDecimal salary;
    @NotNull(message = "Hire date is required") private LocalDate hireDate;
    @NotNull(message = "Department ID is required") private Long departmentId;

    // Optional so existing React clients and employee records remain compatible.
    @Size(max = 11, message = "KRA PIN cannot exceed 11 characters") private String kraPin;
    @Size(max = 50, message = "NSSF number cannot exceed 50 characters") private String nssfNumber;
    @Size(max = 50, message = "SHA number cannot exceed 50 characters") private String shaNumber;
}
