package com.ayssu.ciphergate.doc.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.config.MinioProperties;
import com.ayssu.ciphergate.doc.dto.*;
import com.ayssu.ciphergate.doc.entity.DocAttachment;
import com.ayssu.ciphergate.doc.entity.DocCategory;
import com.ayssu.ciphergate.doc.entity.DocItem;
import com.ayssu.ciphergate.doc.service.DocService;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.MinioObjectService;
import com.ayssu.ciphergate.service.UserService;
import com.ayssu.ciphergate.util.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

@Slf4j
@RestController
@RequestMapping("/api/doc")
@Tag(name = "文档管理", description = "文档分类、文章及附件管理")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;
    private final UserService userService;
    private final MinioObjectService minioObjectService;
    private final MinioProperties minioProperties;

    private final Map<String, Long> downloadTimestamps = new ConcurrentHashMap<>();

    // ==================== Category Endpoints ====================

    @GetMapping("/categories")
    @RequirePermission("DOC_CATEGORY_LIST")
    @Operation(summary = "获取所有文档分类")
    public Result<List<DocCategory>> getAllCategories() {
        List<DocCategory> categories = docService.getAllCategories();
        return Result.success(categories);
    }

    @GetMapping("/categories/{id}")
    @RequirePermission("DOC_CATEGORY_DETAIL")
    @Operation(summary = "根据ID获取文档分类")
    public Result<DocCategory> getCategoryById(@PathVariable Long id) {
        DocCategory category = docService.getCategoryById(id);
        if (category == null) {
            return Result.notFound("分类不存在");
        }
        return Result.success(category);
    }

    @PostMapping("/categories")
    @RequirePermission("DOC_CATEGORY_CREATE")
    @Operation(summary = "创建文档分类")
    public Result<DocCategory> createCategory(@RequestBody DocCategoryCreateRequest request) {
        DocCategory category = docService.createCategory(request);
        return Result.success(category);
    }

    @PutMapping("/categories/{id}")
    @RequirePermission("DOC_CATEGORY_UPDATE")
    @Operation(summary = "更新文档分类")
    public Result<DocCategory> updateCategory(@PathVariable Long id, @RequestBody DocCategoryCreateRequest request) {
        try {
            DocCategory category = docService.updateCategory(id, request);
            return Result.success(category);
        } catch (RuntimeException e) {
            return Result.notFound(e.getMessage());
        }
    }

    @DeleteMapping("/categories/{id}")
    @RequirePermission("DOC_CATEGORY_DELETE")
    @Operation(summary = "删除文档分类")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        try {
            docService.deleteCategory(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.badRequest(e.getMessage());
        }
    }

    // ==================== Item Endpoints ====================

    @GetMapping("/menu")
    @RequirePermission("DOC_ITEM_LIST")
    @Operation(summary = "获取文档菜单结构")
    public Result<List<DocMenuResponse>> getMenu() {
        List<DocMenuResponse> menu = docService.getDocMenu();
        return Result.success(menu);
    }

    @GetMapping("/items/{id}")
    @RequirePermission("DOC_ITEM_DETAIL")
    @Operation(summary = "获取文档详情")
    public Result<DocDetailResponse> getDocDetail(@PathVariable Long id) {
        try {
            DocDetailResponse detail = docService.getDocDetail(id);
            return Result.success(detail);
        } catch (RuntimeException e) {
            return Result.notFound(e.getMessage());
        }
    }

    @PostMapping("/items")
    @RequirePermission("DOC_ITEM_CREATE")
    @Operation(summary = "创建文档")
    public Result<DocItem> createDoc(@RequestBody DocItemCreateRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return Result.unauthorized("未登录");
        }
        DocItem item = docService.createItem(request, userId);
        return Result.success(item);
    }

    @PutMapping("/items/{id}")
    @RequirePermission("DOC_ITEM_UPDATE")
    @Operation(summary = "更新文档")
    public Result<DocItem> updateDoc(@PathVariable Long id, @RequestBody DocItemUpdateRequest request) {
        try {
            DocItem item = docService.updateItem(id, request);
            return Result.success(item);
        } catch (RuntimeException e) {
            return Result.notFound(e.getMessage());
        }
    }

    @DeleteMapping("/items/{id}")
    @RequirePermission("DOC_ITEM_DELETE")
    @Operation(summary = "删除文档")
    public Result<Void> deleteDoc(@PathVariable Long id) {
        try {
            docService.deleteItem(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.notFound(e.getMessage());
        }
    }

    @GetMapping("/items/category/{categoryId}")
    @RequirePermission("DOC_ITEM_LIST")
    @Operation(summary = "根据分类获取文档列表")
    public Result<List<DocItem>> getItemsByCategory(@PathVariable Long categoryId) {
        List<DocItem> items = docService.getItemsByCategory(categoryId);
        return Result.success(items);
    }

    // ==================== Attachment Endpoints ====================

    @PostMapping("/items/{docId}/attachments")
    @RequirePermission("DOC_ITEM_CREATE")
    @Operation(summary = "添加文档附件")
    public Result<DocAttachment> addAttachment(
            @PathVariable Long docId,
            @RequestBody DocAttachmentRequest request) {
        DocAttachment attachment = docService.addAttachment(docId, request.getFileName(), request.getFileUrl(), request.getFileSize(), request.getFileType());
        return Result.success(attachment);
    }

    @DeleteMapping("/attachments/{id}")
    @RequirePermission("DOC_ITEM_DELETE")
    @Operation(summary = "删除附件")
    public Result<Void> deleteAttachment(@PathVariable Long id) {
        docService.deleteAttachment(id);
        return Result.success();
    }

    @PostMapping("/attachments/{id}/download")
    @RequirePermission("DOC_ITEM_LIST")
    @Operation(summary = "记录附件下载")
    public Result<Void> recordDownload(@PathVariable Long id) {
        docService.incrementDownloadCount(id);
        return Result.success();
    }

    @GetMapping("/attachments/{id}/file")
    @RequirePermission("DOC_ITEM_LIST")
    @Operation(summary = "下载附件文件")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        String rateLimitKey = userId + ":" + id;
        Long lastDownload = downloadTimestamps.get(rateLimitKey);
        if (lastDownload != null && System.currentTimeMillis() - lastDownload < 60000) {
            return ResponseEntity.status(429).build();
        }

        try {
            DocAttachment attachment = docService.getAttachmentById(id);
            if (attachment == null) {
                return ResponseEntity.notFound().build();
            }

            String objectKey = attachment.getFileUrl();
            String bucket = minioProperties.getBucket();

            long contentLength = minioObjectService.contentLengthDefaultBucket(objectKey);
            if (contentLength < 0) {
                log.warn("附件文件不存在于存储中: id={}, objectKey={}", id, objectKey);
                return ResponseEntity.notFound().build();
            }

            InputStream inputStream = minioObjectService.download(bucket, objectKey);
            Resource resource = new InputStreamResource(inputStream);

            downloadTimestamps.put(rateLimitKey, System.currentTimeMillis());
            docService.incrementDownloadCount(id);

            String encodedFileName = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            attachment.getFileType() != null ? attachment.getFileType() : "application/octet-stream"))
                    .contentLength(contentLength)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
                    .body(resource);
        } catch (Exception e) {
            log.error("下载附件失败: id={}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    // ==================== Helper Methods ====================

    private Long getCurrentUserId() {
        User user = AuthUtils.getCurrentUser();
        if (user != null) return user.getId();

        var auth = AuthUtils.getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OAuth2User oauth2User) {
            String githubId = oauth2User.getAttribute("id").toString();
            user = userService.getUserByGithubId(githubId);
            if (user != null) return user.getId();
        }

        return null;
    }
}
