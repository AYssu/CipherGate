package com.ayssu.ciphergate.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "插件元数据")
public class PluginModuleDTO {
    @Schema(description = "插件ID")
    private String pluginId;

    @Schema(description = "插件名称")
    private String pluginName;

    @Schema(description = "插件版本")
    private String pluginVersion;

    @Schema(description = "备注")
    private String remark;
}
