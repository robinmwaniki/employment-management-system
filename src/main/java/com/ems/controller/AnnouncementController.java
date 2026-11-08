package com.ems.controller;

import com.ems.dto.request.AnnouncementRequest;
import com.ems.dto.response.AnnouncementResponse;
import com.ems.service.interfaces.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    /** All signed-in users may read announcements. */
    @GetMapping
    public Page<AnnouncementResponse> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return announcementService.getAllAnnouncements(page, size);
    }

    @GetMapping("/{id}")
    public AnnouncementResponse getById(@PathVariable Long id) {

        return announcementService.getAnnouncementById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    public AnnouncementResponse create(
            @Valid @RequestBody AnnouncementRequest request) {

        return announcementService.createAnnouncement(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    public AnnouncementResponse update(
            @PathVariable Long id,
            @Valid @RequestBody AnnouncementRequest request) {

        return announcementService.updateAnnouncement(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','HR_MANAGER')")
    public void delete(@PathVariable Long id) {

        announcementService.deleteAnnouncement(id);
    }
}
