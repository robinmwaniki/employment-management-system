package com.ems.dto.response;

public record CompanyProfileResponse(
        String companyName, String kraPin, String nssfEmployerNumber,
        String shaEmployerNumber, String address, String phoneNumber, String email) {
}
