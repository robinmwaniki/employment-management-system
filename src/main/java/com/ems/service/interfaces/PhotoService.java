package com.ems.service.interfaces;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

public interface PhotoService {

    /**
     * Validates and stores the photo, replacing any previous one.
     *
     * @throws IllegalArgumentException when the file is empty, too large or not
     *                                  a JPEG, PNG or WebP image
     */
    void storePhoto(Long employeeId, MultipartFile file);

    Optional<PhotoContent> findPhoto(Long employeeId);

    /** A stored photo: its validated content type and the image bytes. */
    record PhotoContent(String contentType, byte[] data) {
    }

}
