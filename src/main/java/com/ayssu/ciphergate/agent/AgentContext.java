package com.ayssu.ciphergate.agent;

import lombok.Value;

@Value
public class AgentContext {
    Long appId;
    Long agentId;
    Long userId;
}

