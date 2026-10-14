package com.ems.web;

import com.ems.repository.UserRepository;
import com.ems.service.interfaces.PhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Serves employee photos to signed-in users: staff may see any photo, an employee
 * only their own. Anything else is answered with 404.
 */
@RestController
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;
    private final UserRepository userRepository;

    @GetMapping("/photos/employee/{id}")
    public ResponseEntity<byte[]> employeePhoto(
            @PathVariable Long id,
            Authentication authentication) {

        if (!canView(authentication, id)) {
            return ResponseEntity.notFound().build();
        }

        return photoService.findPhoto(id)
                .map(photo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(photo.contentType()))
                        // Page URLs carry a version marker, so a short private cache is safe.
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                        .body(photo.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean canView(Authentication authentication, Long employeeId) {

        boolean staff = authentication.getAuthorities()
                .stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_HR_MANAGER".equals(a.getAuthority()));

        if (staff) {
            return true;
        }

        return userRepository.findByUsername(authentication.getName())
                .map(user -> user.getEmployee() != null
                        && employeeId.equals(user.getEmployee().getId()))
                .orElse(false);
    }
}
