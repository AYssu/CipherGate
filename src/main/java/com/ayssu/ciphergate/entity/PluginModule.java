package com.ayssu.ciphergate.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("plugin_module")
public class PluginModule implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String pluginId;
    private String pluginName;
    private String pluginVersion;
    private String bucketName;
    private String objectKey;
    private String sha256;
    private Integer status;
    private String loadedPluginId;
    private String remark;
    private String configSchema;
    private String configDefaults;
    private String configValues;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    private LocalDateTime deletedAt;
}
