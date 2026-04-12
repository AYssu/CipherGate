package com.ayssu.ciphergate.thirdparty.service;

import com.ayssu.ciphergate.entity.AppVariable;
import com.ayssu.ciphergate.mapper.AppVariableMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 三方可读的应用变量（与卡密登录响应中 {@code variables} 字段语义一致：仅启用、未删除的变量，按变量名 → 解析后的值）。
 */
@Service
@RequiredArgsConstructor
public class ThirdPartyAppVariableService {

    private final AppVariableMapper appVariableMapper;
    private final ObjectMapper objectMapper;

    public Map<String, Object> getEnabledVariablesMap(Long appId) {
        List<AppVariable> variables = appVariableMapper.selectList(new LambdaQueryWrapper<AppVariable>()
                .eq(AppVariable::getAppId, appId)
                .eq(AppVariable::getEnabled, true)
                .eq(AppVariable::getDeleted, 0));
        Map<String, Object> result = new HashMap<>();
        for (AppVariable v : variables) {
            result.put(v.getVariableName(), convertVariableValue(v.getVariableValue(), v.getVariableType()));
        }
        return result;
    }

    private Object convertVariableValue(String value, String type) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return switch (type == null ? "" : type) {
                case "NUMBER" -> Double.parseDouble(value);
                case "BOOLEAN" -> Boolean.parseBoolean(value);
                case "JSON" -> objectMapper.readTree(value);
                case "ARRAY" -> objectMapper.readValue(value, List.class);
                default -> value;
            };
        } catch (Exception e) {
            return value;
        }
    }
}
