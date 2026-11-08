package com.ems.service.interfaces;

import com.ems.dto.request.AnnouncementRequest;
import com.ems.dto.response.AnnouncementResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface AnnouncementService {

    AnnouncementResponse createAnnouncement(AnnouncementRequest request);

    AnnouncementResponse updateAnnouncement(Long id, AnnouncementRequest request);

    AnnouncementResponse getAnnouncement(Long id);

    AnnouncementResponse getAnnouncementById(Long id);
AnnouncementResponse updateAnnouncement(Long id, AnnouncementRequest request);

    Page<AnnouncementResponse> getAllAnnouncements(int page, int size);

    void deleteAnnouncement(Long id);
    List<AnnouncementResponse> getLatestAnnouncements();

}
