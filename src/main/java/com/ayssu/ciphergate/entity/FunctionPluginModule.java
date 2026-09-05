package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 函数插件模块实体。
 * <p>
 * 用于管理 WebSocket 函数执行插件的元信息和生命周期。
 * 与 {@link PluginModule}（加密插件）分离，独立管理。
 */
@Data
@TableName("function_plugin_module")
public class FunctionPluginModule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 插件唯一标识（与 plugin.properties 中 plugin.id 一致） */
    private String pluginId;

    /** 插件名称（显示用） */
    private String pluginName;

    /** 插件版本 */
    private String pluginVersion;

    /** MinIO 存储桶名称 */
    private String bucketName;

    /** MinIO 对象键（文件路径） */
    private String objectKey;

    /** JAR 文件 SHA256 校验和 */
    private String sha256;

    /**
     * 插件状态：
     * 0 = 待启用（上传后默认）
     * 1 = 已启用
     * 2 = 已禁用
     * 3 = 加载失败
     */
    private Integer status;

    /** PF4J 运行时加载的插件ID */
    private String loadedPluginId;

    /** 备注 */
    private String remark;

    /** 插件提供的函数列表（JSON 数组，如 ["echo","add"]） */
    private String functions;

    /** 插件配置 Schema（JSON） */
    private String configSchema;

    /** 插件默认配置（JSON） */
    private String configDefaults;

    /** 插件运行时配置（JSON，用户可修改） */
    private String configValues;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private LocalDateTime deletedAt;
}
