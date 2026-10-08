package com.ems.web;

import com.ems.dto.request.AnnouncementRequest;
import com.ems.dto.response.AnnouncementResponse;
import com.ems.service.interfaces.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class AnnouncementPageController {

    private final AnnouncementService announcementService;

    @GetMapping("/announcements")
    public String announcements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        model.addAttribute(
                "announcements",
                announcementService.getAllAnnouncements(page, size));

        return "announcements";
    }

    @GetMapping("/announcements/new")
    public String newAnnouncement(Model model) {

        model.addAttribute(
                "announcement",
                new AnnouncementRequest());

        return "announcement-form";
    }

    @PostMapping("/announcements/save")
    public String saveAnnouncement(
            @ModelAttribute AnnouncementRequest request) {

        announcementService.createAnnouncement(request);

        return "redirect:/announcements";
    }

    @GetMapping("/announcements/edit/{id}")
    public String editAnnouncement(
            @PathVariable Long id,
            Model model) {

        AnnouncementResponse response =
                announcementService.getAnnouncement(id);

        AnnouncementRequest request =
                new AnnouncementRequest();

        request.setTitle(response.getTitle());
        request.setMessage(response.getMessage());
        request.setPublishDate(response.getPublishDate());
        request.setExpiryDate(response.getExpiryDate());

        model.addAttribute("announcement", request);
        model.addAttribute("announcementId", id);

        return "announcement-form";
    }

    @PostMapping("/announcements/update/{id}")
    public String updateAnnouncement(
            @PathVariable Long id,
            @ModelAttribute AnnouncementRequest request) {

        announcementService.updateAnnouncement(id, request);

        return "redirect:/announcements";
    }

    @PostMapping("/announcements/delete/{id}")
    public String deleteAnnouncement(
            @PathVariable Long id) {

        announcementService.deleteAnnouncement(id);

        return "redirect:/announcements";
    }

}