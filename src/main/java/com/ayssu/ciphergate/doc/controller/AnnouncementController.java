package com.ayssu.ciphergate.doc.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.doc.dto.AnnouncementCreateRequest;
import com.ayssu.ciphergate.doc.entity.SystemAnnouncement;
import com.ayssu.ciphergate.doc.service.AnnouncementService;
import com.ayssu.ciphergate.util.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public Result<List<SystemAnnouncement>> getAll() {
        List<SystemAnnouncement> announcements = announcementService.getAllAnnouncements();
        return Result.success(announcements);
    }

    @GetMapping("/active")
    public Result<List<SystemAnnouncement>> getActive() {
        List<SystemAnnouncement> announcements = announcementService.getActiveAnnouncements();
        return Result.success(announcements);
    }

    @GetMapping("/{id}")
    public Result<SystemAnnouncement> getById(@PathVariable Long id) {
        SystemAnnouncement announcement = announcementService.getAnnouncementById(id);
        if (announcement == null) {
            return Result.notFound("Announcement not found");
        }
        return Result.success(announcement);
    }

    @PostMapping
    public Result<SystemAnnouncement> create(@RequestBody AnnouncementCreateRequest request) {
        var user = AuthUtils.getCurrentUser();
        if (user == null) {
            return Result.unauthorized("Unauthorized");
        }
        SystemAnnouncement announcement = announcementService.createAnnouncement(request, user.getId());
        return Result.success(announcement);
    }

    @PutMapping("/{id}")
    public Result<SystemAnnouncement> update(@PathVariable Long id, @RequestBody AnnouncementCreateRequest request) {
        try {
            SystemAnnouncement announcement = announcementService.updateAnnouncement(id, request);
            return Result.success(announcement);
        } catch (RuntimeException e) {
            return Result.notFound(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.deleteAnnouncement(id);
        return Result.success(null);
    }
}
