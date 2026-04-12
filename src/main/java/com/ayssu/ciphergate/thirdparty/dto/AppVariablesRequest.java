package com.ayssu.ciphergate.thirdparty.dto;

import lombok.Data;

/**
 * 三方查询应用变量（解密后的业务体）。解密须非空，推荐 {@code ping=1} 占位。
 */
@Data
public class AppVariablesRequest {

    private String ping;
}
