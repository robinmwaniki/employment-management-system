package com.ems.service.impl;

import com.ems.dto.request.AnnouncementRequest;
import com.ems.dto.response.AnnouncementResponse;
import com.ems.entity.Announcement;
import com.ems.exception.ResourceNotFoundException;
import com.ems.mapper.AnnouncementMapper;
import com.ems.repository.AnnouncementRepository;
import com.ems.service.interfaces.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    private final AnnouncementRepository repository;

    @Override
    public AnnouncementResponse createAnnouncement(AnnouncementRequest request) {

        Announcement announcement =
                AnnouncementMapper.toEntity(request);

        return AnnouncementMapper.toResponse(
                repository.save(announcement));
    }

    @Override
    public AnnouncementResponse updateAnnouncement(Long id,
                                                   AnnouncementRequest request) {

        Announcement announcement = repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Announcement not found"));

        announcement.setTitle(request.getTitle());
        announcement.setMessage(request.getMessage());
        announcement.setPublishDate(request.getPublishDate());
        announcement.setExpiryDate(request.getExpiryDate());

        return AnnouncementMapper.toResponse(
                repository.save(announcement));
    }

    @Override
    public AnnouncementResponse getAnnouncement(Long id) {

        Announcement announcement = repository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Announcement not found"));

        return AnnouncementMapper.toResponse(announcement);
    }

    @Override
    public Page<AnnouncementResponse> getAllAnnouncements(int page,
                                                          int size) {

        return repository.findAll(PageRequest.of(page, size))
                .map(AnnouncementMapper::toResponse);
    }

    @Override
    public void deleteAnnouncement(Long id) {

        repository.deleteById(id);
    }

}