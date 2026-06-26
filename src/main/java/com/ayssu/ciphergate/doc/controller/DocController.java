package com.ayssu.ciphergate.doc.controller;

import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.doc.dto.*;
import com.ayssu.ciphergate.doc.entity.DocAttachment;
import com.ayssu.ciphergate.doc.entity.DocCategory;
import com.ayssu.ciphergate.doc.entity.DocItem;
import com.ayssu.ciphergate.doc.service.DocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/doc")
@Tag(name = "文档管理", description = "文档分类、文章及附件管理")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;

    // ==================== Category Endpoints ====================

    @GetMapping("/categories")
    @Operation(summary = "获取所有文档分类")
    public Result<List<DocCategory>> getAllCategories() {
        List<DocCategory> categories = docService.getAllCategories();
        return Result.success(categories);
    }

    @GetMapping("/categories/{id}")
    @Operation(summary = "根据ID获取文档分类")
    public Result<DocCategory> getCategoryById(@PathVariable Long id) {
        DocCategory category = docService.getCategoryById(id);
        if (category == null) {
            return Result.notFound("分类不存在");
        }
        return Result.success(category);
    }

    @PostMapping("/categories")
    @Operation(summary = "创建文档分类")
    public Result<DocCategory> createCategory(@RequestBody DocCategoryCreateRequest request) {
        DocCategory category = docService.createCategory(request);
        return Result.success(category);
    }

    @PutMapping("/categories/{id}")
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
    @Operation(summary = "获取文档菜单结构")
    public Result<List<DocMenuResponse>> getMenu() {
        List<DocMenuResponse> menu = docService.getDocMenu();
        return Result.success(menu);
    }

    @GetMapping("/items/{id}")
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
    @Operation(summary = "根据分类获取文档列表")
    public Result<List<DocItem>> getItemsByCategory(@PathVariable Long categoryId) {
        List<DocItem> items = docService.getItemsByCategory(categoryId);
        return Result.success(items);
    }

    // ==================== Attachment Endpoints ====================

    @PostMapping("/items/{docId}/attachments")
    @Operation(summary = "添加文档附件")
    public Result<DocAttachment> addAttachment(
            @PathVariable Long docId,
            @RequestParam String fileName,
            @RequestParam String fileUrl,
            @RequestParam Long fileSize,
            @RequestParam String fileType) {
        DocAttachment attachment = docService.addAttachment(docId, fileName, fileUrl, fileSize, fileType);
        return Result.success(attachment);
    }

    @DeleteMapping("/attachments/{id}")
    @Operation(summary = "删除附件")
    public Result<Void> deleteAttachment(@PathVariable Long id) {
        docService.deleteAttachment(id);
        return Result.success();
    }

    @PostMapping("/attachments/{id}/download")
    @Operation(summary = "记录附件下载")
    public Result<Void> recordDownload(@PathVariable Long id) {
        docService.incrementDownloadCount(id);
        return Result.success();
    }

    // ==================== Helper Methods ====================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Map) {
            return (Map<String, Object>) auth.getPrincipal();
        }
        return null;
    }

    private static Long getCurrentUserId() {
        Map<String, Object> p = getPrincipal();
        return p != null ? ((Number) p.get("id")).longValue() : null;
    }
}
