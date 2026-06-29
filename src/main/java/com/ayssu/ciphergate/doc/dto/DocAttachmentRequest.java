package com.ayssu.ciphergate.doc.dto;

import lombok.Data;

@Data
public class DocAttachmentRequest {

    private String fileName;

    private String fileUrl;

    private Long fileSize;

    private String fileType;
}
