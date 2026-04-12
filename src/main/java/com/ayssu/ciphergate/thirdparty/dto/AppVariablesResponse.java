package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

import java.util.Map;

/**
 * 应用变量查询结果（出站前由统一 Advice 加密为 data HEX）。
 * <p>
 * {@link #variables} 与卡密登录响应中同名字段一致。
 */
@Data
public class AppVariablesResponse {

    private Map<String, Object> variables;
}
