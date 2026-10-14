package com.ems.service.impl;

import com.ems.entity.Employee;
import com.ems.entity.EmployeePhoto;
import com.ems.exception.ResourceNotFoundException;
import com.ems.repository.EmployeePhotoRepository;
import com.ems.repository.EmployeeRepository;
import com.ems.service.interfaces.PhotoService;
import com.ems.service.interfaces.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Stores employee photos in the database. The image type is detected from the
 * file's own bytes; the client-supplied name and content type are never trusted.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PhotoServiceImpl implements PhotoService {

    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private final EmployeePhotoRepository photoRepository;
    private final EmployeeRepository employeeRepository;
    private final SystemLogService systemLogService;

    @Override
    public void storePhoto(Long employeeId, MultipartFile file) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Employee not found with id: " + employeeId));

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a photo to upload.");
        }

        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("The photo must be 2 MB or smaller.");
        }

        byte[] data;

        try {
            data = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read the uploaded photo.", ex);
        }

        String contentType = detectContentType(data);

        if (contentType == null) {
            throw new IllegalArgumentException(
                    "Only JPEG, PNG or WebP images are allowed.");
        }

        photoRepository.save(EmployeePhoto.builder()
                .employeeId(employeeId)
                .contentType(contentType)
                .data(data)
                .updatedAt(LocalDateTime.now())
                .build());

        // The employee row only keeps a version marker. Pages use it to know that
        // a photo exists and to bypass the browser cache after a change.
        employee.setProfilePhoto(String.valueOf(System.currentTimeMillis()));

        systemLogService.saveLog(
                "System",
                "ADMIN",
                "UPDATE PHOTO",
                "Updated profile photo of employee "
                        + employee.getFirstName() + " " + employee.getLastName());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PhotoContent> findPhoto(Long employeeId) {

        return photoRepository.findById(employeeId)
                .map(photo -> new PhotoContent(photo.getContentType(), photo.getData()));
    }

    /**
     * Recognises JPEG, PNG and WebP by their leading bytes.
     *
     * @return the content type, or null when the data is none of them
     */
    private String detectContentType(byte[] d) {

        if (d.length >= 3
                && (d[0] & 0xFF) == 0xFF
                && (d[1] & 0xFF) == 0xD8
                && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        if (d.length >= 8
                && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }

        if (d.length >= 12
                && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }

        return null;
    }
}
