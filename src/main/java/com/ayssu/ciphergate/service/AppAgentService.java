package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.AppAgentDTO;
import com.ayssu.ciphergate.dto.AgentBindUserDTO;

import java.util.List;
import java.util.Map;

public interface AppAgentService {
    List<AppAgentDTO> listByAppId(Long appId, Long operatorId);

    AppAgentDTO create(Long appId, AppAgentDTO dto, Long operatorId);

    AppAgentDTO update(Long appId, Long agentId, AppAgentDTO dto, Long operatorId);

    void updatePermissions(Long appId, Long agentId, List<String> permissions, Long operatorId);

    void updateQuotas(Long appId, Long agentId, Map<String, Long> quotas, Long operatorId);

    AgentBindUserDTO findBindUserByGithubId(Long appId, String githubId, Long operatorId);
}

