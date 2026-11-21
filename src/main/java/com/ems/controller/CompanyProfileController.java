package com.ems.controller;

import com.ems.dto.request.CompanyProfileRequest;
import com.ems.dto.response.CompanyProfileResponse;
import com.ems.service.impl.CompanyProfileServiceImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/company-profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CompanyProfileController {
    private final CompanyProfileServiceImpl service;

    @GetMapping
    public CompanyProfileResponse getProfile() {
        return service.getProfile();
    }

    @PutMapping
    public CompanyProfileResponse updateProfile(@Valid @RequestBody CompanyProfileRequest request) {
        return service.updateProfile(request);
    }
}
