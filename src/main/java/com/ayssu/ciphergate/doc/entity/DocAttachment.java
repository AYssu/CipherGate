package com.ayssu.ciphergate.doc.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("doc_attachment")
public class DocAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long docId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String fileType;
    private Integer downloadCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}