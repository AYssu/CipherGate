package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 函数插件对应用的显式访问授权。
 * 默认不存在授权记录，因此新应用/新插件默认拒绝调用。
 */
@Data
@TableName("function_plugin_app_access")
public class FunctionPluginAppAccess implements Serializable {

    private static final long serialVersionUID = 1L;

    private String pluginId;

    private Long appId;

    private LocalDateTime createdAt;
}
