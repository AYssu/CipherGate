package com.ayssu.ciphergate.doc.dto;

import lombok.Data;

@Data
public class DocCategoryCreateRequest {

    private String name;

    private String description;

    private Integer sortOrder;
}
