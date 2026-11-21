package com.ems.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyProfileRequest {
    @NotBlank @Size(max = 150) private String companyName;
    @Size(max = 11) private String kraPin;
    @Size(max = 50) private String nssfEmployerNumber;
    @Size(max = 50) private String shaEmployerNumber;
    @Size(max = 300) private String address;
    @Size(max = 30) private String phoneNumber;
    @Email @Size(max = 255) private String email;
}
