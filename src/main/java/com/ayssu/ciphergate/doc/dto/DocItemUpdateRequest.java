package com.ayssu.ciphergate.doc.dto;

import lombok.Data;

@Data
public class DocItemUpdateRequest {

    private Long categoryId;

    private String title;

    private String content;

    private String authorName;

    private String authorGithub;

    private String authorQq;

    private String authorBilibili;

    private Integer status;
}
