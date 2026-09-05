package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.FunctionPluginModule;
import com.ayssu.ciphergate.thirdparty.ws.model.FunctionResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 函数插件模块服务接口。
 * <p>
 * 管理 WebSocket 函数执行插件的生命周期：上传、启用、禁用、删除、配置。
 */
public interface FunctionPluginModuleService {

    /**
     * 上传函数插件 JAR 文件。
     *
     * @param file          JAR 文件
     * @param pluginId      插件ID（可选，从 JAR 解析）
     * @param pluginName    插件名称（可选）
     * @param pluginVersion 版本号（可选，从 JAR 解析）
     * @param remark        备注
     * @return 上传后的插件模块信息
     */
    FunctionPluginModule uploadPlugin(MultipartFile file, String pluginId, String pluginName, String pluginVersion, String remark);

    /**
     * 查询所有函数插件列表。
     */
    List<FunctionPluginModule> listPlugins();

    /**
     * 启用函数插件。
     * 从 MinIO 下载 JAR，通过 PF4J 加载并启动。
     */
    void enablePlugin(Long id);

    /**
     * 停用函数插件。
     * 通过 PF4J 停止并卸载插件。
     */
    void disablePlugin(Long id);

    /**
     * 删除函数插件。
     * 停用插件、删除 MinIO 对象、软删除数据库记录。
     */
    void deletePlugin(Long id);

    /**
     * 启动时加载所有已启用的函数插件。
     */
    void loadEnabledPluginsOnStartup();

    /**
     * 获取插件配置 Schema。
     */
    Map<String, Object> getPluginConfigSchema(Long id);

    /**
     * 获取插件配置（包含 Schema、默认值、当前值）。
     */
    Map<String, Object> getPluginConfig(Long id);

    /**
     * 更新插件运行时配置。
     */
    void updatePluginConfig(Long id, Map<String, Object> configValues);

    /**
     * 解析插件的运行时配置值（用于函数执行时注入）。
     */
    Map<String, Object> resolveRuntimeConfigValues(String pluginId);

    /**
     * 测试执行函数（管理后台测试用）。
     */
    FunctionResult testFunction(String pluginId, String funcName, Map<String, Object> params);

    /**
     * 获取插件提供的函数列表详情。
     */
    List<Map<String, Object>> getPluginFunctions(Long id);
}
