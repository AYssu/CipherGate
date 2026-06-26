package com.ayssu.ciphergate.doc.dto;

import lombok.Data;

import java.util.List;

@Data
public class DocMenuResponse {

    private Long id;

    private String name;

    private String description;

    private List<DocMenuItem> items;

    @Data
    public static class DocMenuItem {

        private Long id;

        private String title;

        private String authorName;
    }
}
