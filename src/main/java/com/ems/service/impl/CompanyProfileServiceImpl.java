package com.ems.service.impl;

import com.ems.dto.request.CompanyProfileRequest;
import com.ems.dto.response.CompanyProfileResponse;
import com.ems.entity.CompanyProfile;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.CompanyProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CompanyProfileServiceImpl {
    private final CompanyProfileRepository repository;

    @Transactional(readOnly = true)
    public CompanyProfileResponse getProfile() {
        return response(findProfile());
    }

    public CompanyProfileResponse updateProfile(CompanyProfileRequest request) {
        CompanyProfile profile = findProfile();
        profile.setCompanyName(request.getCompanyName().trim());
        if (request.getKraPin() != null) profile.setKraPin(normalizePin(request.getKraPin()));
        if (request.getNssfEmployerNumber() != null) profile.setNssfEmployerNumber(normalize(request.getNssfEmployerNumber()));
        if (request.getShaEmployerNumber() != null) profile.setShaEmployerNumber(normalize(request.getShaEmployerNumber()));
        if (request.getAddress() != null) profile.setAddress(normalize(request.getAddress()));
        if (request.getPhoneNumber() != null) profile.setPhoneNumber(normalize(request.getPhoneNumber()));
        if (request.getEmail() != null) profile.setEmail(normalize(request.getEmail()));
        return response(repository.saveAndFlush(profile));
    }

    private CompanyProfile findProfile() {
        return repository.findById(1).orElseThrow(() ->
                new ResourceNotFoundException("Company profile is not initialized. Verify database migrations."));
    }

    private static CompanyProfileResponse response(CompanyProfile p) {
        return new CompanyProfileResponse(p.getCompanyName(), p.getKraPin(),
                p.getNssfEmployerNumber(), p.getShaEmployerNumber(),
                p.getAddress(), p.getPhoneNumber(), p.getEmail());
    }

    private static String normalizePin(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    private static String normalize(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
