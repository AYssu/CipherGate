package com.ayssu.ciphergate.doc.service;

import com.ayssu.ciphergate.doc.dto.AnnouncementCreateRequest;
import com.ayssu.ciphergate.doc.entity.SystemAnnouncement;
import com.ayssu.ciphergate.doc.mapper.SystemAnnouncementMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final SystemAnnouncementMapper announcementMapper;

    public List<SystemAnnouncement> getAllAnnouncements() {
        return announcementMapper.selectList(
            new LambdaQueryWrapper<SystemAnnouncement>()
                .orderByDesc(SystemAnnouncement::getCreatedAt)
        );
    }

    public List<SystemAnnouncement> getActiveAnnouncements() {
        return announcementMapper.selectList(
            new LambdaQueryWrapper<SystemAnnouncement>()
                .eq(SystemAnnouncement::getStatus, 1)
                .orderByDesc(SystemAnnouncement::getCreatedAt)
        );
    }

    public SystemAnnouncement getAnnouncementById(Long id) {
        return announcementMapper.selectById(id);
    }

    @Transactional
    public SystemAnnouncement createAnnouncement(AnnouncementCreateRequest request, Long userId) {
        SystemAnnouncement announcement = new SystemAnnouncement();
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcement.setStatus(1);
        announcement.setCreatedBy(userId);
        announcementMapper.insert(announcement);
        return announcement;
    }

    @Transactional
    public SystemAnnouncement updateAnnouncement(Long id, AnnouncementCreateRequest request) {
        SystemAnnouncement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new RuntimeException("Announcement not found");
        }
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcementMapper.updateById(announcement);
        return announcement;
    }

    @Transactional
    public void deleteAnnouncement(Long id) {
        announcementMapper.deleteById(id);
    }
}
