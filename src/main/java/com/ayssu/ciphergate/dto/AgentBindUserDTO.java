package com.ayssu.ciphergate.dto;

import lombok.Data;

@Data
public class AgentBindUserDTO {
    private Long id;
    private String githubId;
    private String login;
    private String name;
    private Integer status;
}

