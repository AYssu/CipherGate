package com.ayssu.ciphergate.doc.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DocDetailResponse {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String title;

    private String content;

    private String authorName;

    private String authorGithub;

    private String authorQq;

    private String authorBilibili;

    private Long viewCount;

    private LocalDateTime createdAt;

    private List<AttachmentInfo> attachments;

    @Data
    public static class AttachmentInfo {

        private Long id;

        private String fileName;

        private String fileUrl;

        private Long fileSize;

        private String fileType;

        private Long downloadCount;
    }
}
