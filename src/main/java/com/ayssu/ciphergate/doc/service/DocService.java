package com.ayssu.ciphergate.doc.service;

import com.ayssu.ciphergate.doc.dto.*;
import com.ayssu.ciphergate.doc.entity.DocAttachment;
import com.ayssu.ciphergate.doc.entity.DocCategory;
import com.ayssu.ciphergate.doc.entity.DocItem;
import com.ayssu.ciphergate.doc.mapper.DocAttachmentMapper;
import com.ayssu.ciphergate.doc.mapper.DocCategoryMapper;
import com.ayssu.ciphergate.doc.mapper.DocItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocService {

    private final DocCategoryMapper categoryMapper;
    private final DocItemMapper itemMapper;
    private final DocAttachmentMapper attachmentMapper;

    // ==================== Category Operations ====================

    public List<DocCategory> getAllCategories() {
        return categoryMapper.selectList(
            new LambdaQueryWrapper<DocCategory>()
                .eq(DocCategory::getStatus, 1)
                .orderByAsc(DocCategory::getSortOrder)
        );
    }

    public DocCategory getCategoryById(Long id) {
        return categoryMapper.selectById(id);
    }

    public DocCategory createCategory(DocCategoryCreateRequest request) {
        DocCategory category = new DocCategory();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        category.setStatus(1);
        categoryMapper.insert(category);
        return category;
    }

    @Transactional
    public DocCategory updateCategory(Long id, DocCategoryCreateRequest request) {
        DocCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new RuntimeException("Category not found");
        }
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        categoryMapper.updateById(category);
        return category;
    }

    @Transactional
    public void deleteCategory(Long id) {
        Long itemCount = itemMapper.selectCount(
            new LambdaQueryWrapper<DocItem>()
                .eq(DocItem::getCategoryId, id)
        );
        if (itemCount > 0) {
            throw new RuntimeException("Cannot delete category with existing items");
        }
        categoryMapper.deleteById(id);
    }

    // ==================== Item Operations ====================

    public List<DocMenuResponse> getDocMenu() {
        List<DocCategory> categories = categoryMapper.selectList(
            new LambdaQueryWrapper<DocCategory>()
                .eq(DocCategory::getStatus, 1)
                .orderByAsc(DocCategory::getSortOrder)
        );

        List<DocMenuResponse> menu = new ArrayList<>();
        for (DocCategory category : categories) {
            DocMenuResponse menuResponse = new DocMenuResponse();
            menuResponse.setId(category.getId());
            menuResponse.setName(category.getName());
            menuResponse.setDescription(category.getDescription());

            List<DocItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<DocItem>()
                    .eq(DocItem::getCategoryId, category.getId())
                    .eq(DocItem::getStatus, 1)
            );

            List<DocMenuResponse.DocMenuItem> menuItems = items.stream().map(item -> {
                DocMenuResponse.DocMenuItem menuItem = new DocMenuResponse.DocMenuItem();
                menuItem.setId(item.getId());
                menuItem.setTitle(item.getTitle());
                menuItem.setAuthorName(item.getAuthorName());
                return menuItem;
            }).collect(Collectors.toList());

            menuResponse.setItems(menuItems);
            menu.add(menuResponse);
        }
        return menu;
    }

    @Transactional
    public DocDetailResponse getDocDetail(Long id) {
        DocItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new RuntimeException("Doc item not found");
        }

        // increment view count
        item.setViewCount(item.getViewCount() + 1);
        itemMapper.updateById(item);

        // get category name
        DocCategory category = categoryMapper.selectById(item.getCategoryId());
        String categoryName = category != null ? category.getName() : null;

        // get attachments
        List<DocAttachment> attachments = attachmentMapper.selectList(
            new LambdaQueryWrapper<DocAttachment>()
                .eq(DocAttachment::getDocId, id)
        );

        List<DocDetailResponse.AttachmentInfo> attachmentInfos = attachments.stream().map(att -> {
            DocDetailResponse.AttachmentInfo info = new DocDetailResponse.AttachmentInfo();
            info.setId(att.getId());
            info.setFileName(att.getFileName());
            info.setFileUrl(att.getFileUrl());
            info.setFileSize(att.getFileSize());
            info.setFileType(att.getFileType());
            info.setDownloadCount(att.getDownloadCount() != null ? (long) att.getDownloadCount() : 0L);
            return info;
        }).collect(Collectors.toList());

        DocDetailResponse response = new DocDetailResponse();
        response.setId(item.getId());
        response.setCategoryId(item.getCategoryId());
        response.setCategoryName(categoryName);
        response.setTitle(item.getTitle());
        response.setContent(item.getContent());
        response.setAuthorName(item.getAuthorName());
        response.setAuthorGithub(item.getAuthorGithub());
        response.setAuthorQq(item.getAuthorQq());
        response.setAuthorBilibili(item.getAuthorBilibili());
        response.setViewCount(item.getViewCount() != null ? (long) item.getViewCount() : 0L);
        response.setCreatedAt(item.getCreatedAt());
        response.setAttachments(attachmentInfos);

        return response;
    }

    @Transactional
    public DocItem createItem(DocItemCreateRequest request, Long userId) {
        DocItem item = new DocItem();
        item.setCategoryId(request.getCategoryId());
        item.setTitle(request.getTitle());
        item.setContent(request.getContent());
        item.setAuthorName(request.getAuthorName());
        item.setAuthorGithub(request.getAuthorGithub());
        item.setAuthorQq(request.getAuthorQq());
        item.setAuthorBilibili(request.getAuthorBilibili());
        item.setStatus(1);
        item.setViewCount(0);
        item.setCreatedBy(userId);
        itemMapper.insert(item);
        return item;
    }

    @Transactional
    public DocItem updateItem(Long id, DocItemUpdateRequest request) {
        DocItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new RuntimeException("Doc item not found");
        }
        item.setCategoryId(request.getCategoryId());
        item.setTitle(request.getTitle());
        item.setContent(request.getContent());
        item.setAuthorName(request.getAuthorName());
        item.setAuthorGithub(request.getAuthorGithub());
        item.setAuthorQq(request.getAuthorQq());
        item.setAuthorBilibili(request.getAuthorBilibili());
        if (request.getStatus() != null) {
            item.setStatus(request.getStatus());
        }
        itemMapper.updateById(item);
        return item;
    }

    @Transactional
    public void deleteItem(Long id) {
        // delete attachments first
        attachmentMapper.delete(
            new LambdaQueryWrapper<DocAttachment>()
                .eq(DocAttachment::getDocId, id)
        );
        itemMapper.deleteById(id);
    }

    public List<DocItem> getItemsByCategory(Long categoryId) {
        return itemMapper.selectList(
            new LambdaQueryWrapper<DocItem>()
                .eq(DocItem::getCategoryId, categoryId)
                .eq(DocItem::getStatus, 1)
        );
    }

    // ==================== Attachment Operations ====================

    public DocAttachment addAttachment(Long docId, String fileName, String fileUrl, Long fileSize, String fileType) {
        DocAttachment attachment = new DocAttachment();
        attachment.setDocId(docId);
        attachment.setFileName(fileName);
        attachment.setFileUrl(fileUrl);
        attachment.setFileSize(fileSize);
        attachment.setFileType(fileType);
        attachment.setDownloadCount(0);
        attachmentMapper.insert(attachment);
        return attachment;
    }

    public void deleteAttachment(Long id) {
        attachmentMapper.deleteById(id);
    }

    public DocAttachment getAttachmentById(Long id) {
        return attachmentMapper.selectById(id);
    }

    @Transactional
    public void incrementDownloadCount(Long attachmentId) {
        DocAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment != null) {
            attachment.setDownloadCount(attachment.getDownloadCount() + 1);
            attachmentMapper.updateById(attachment);
        }
    }
}
