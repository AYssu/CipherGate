package com.ayssu.ciphergate.doc.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.doc.dto.AnnouncementCreateRequest;
import com.ayssu.ciphergate.doc.entity.SystemAnnouncement;
import com.ayssu.ciphergate.doc.service.AnnouncementService;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.UserService;
import com.ayssu.ciphergate.util.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
@Tag(name = "系统公告", description = "系统公告管理")
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final UserService userService;

    @GetMapping
    @RequirePermission("ANNOUNCEMENT_LIST")
    @Operation(summary = "获取所有公告")
    public Result<List<SystemAnnouncement>> getAll() {
        List<SystemAnnouncement> announcements = announcementService.getAllAnnouncements();
        return Result.success(announcements);
    }

    @GetMapping("/active")
    @RequirePermission("ANNOUNCEMENT_VIEW")
    @Operation(summary = "获取激活的公告")
    public Result<List<SystemAnnouncement>> getActive() {
        List<SystemAnnouncement> announcements = announcementService.getActiveAnnouncements();
        return Result.success(announcements);
    }

    @GetMapping("/{id}")
    @RequirePermission("ANNOUNCEMENT_DETAIL")
    @Operation(summary = "根据ID获取公告")
    public Result<SystemAnnouncement> getById(@PathVariable Long id) {
        SystemAnnouncement announcement = announcementService.getAnnouncementById(id);
        if (announcement == null) {
            return Result.notFound("公告不存在");
        }
        return Result.success(announcement);
    }

    @PostMapping
    @RequirePermission("ANNOUNCEMENT_CREATE")
    @Operation(summary = "创建公告")
    public Result<SystemAnnouncement> create(@RequestBody AnnouncementCreateRequest request) {
        User user = getCurrentUser();
        if (user == null) {
            return Result.unauthorized("未登录");
        }
        SystemAnnouncement announcement = announcementService.createAnnouncement(request, user.getId());
        return Result.success(announcement);
    }

    @PutMapping("/{id}")
    @RequirePermission("ANNOUNCEMENT_UPDATE")
    @Operation(summary = "更新公告")
    public Result<SystemAnnouncement> update(@PathVariable Long id, @RequestBody AnnouncementCreateRequest request) {
        try {
            SystemAnnouncement announcement = announcementService.updateAnnouncement(id, request);
            return Result.success(announcement);
        } catch (RuntimeException e) {
            return Result.notFound(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @RequirePermission("ANNOUNCEMENT_DELETE")
    @Operation(summary = "删除公告")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.deleteAnnouncement(id);
        return Result.success(null);
    }

    private User getCurrentUser() {
        User user = AuthUtils.getCurrentUser();
        if (user != null) return user;

        var auth = AuthUtils.getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OAuth2User oauth2User) {
            String githubId = oauth2User.getAttribute("id").toString();
            user = userService.getUserByGithubId(githubId);
            if (user != null) return user;
        }

        return null;
    }
}
