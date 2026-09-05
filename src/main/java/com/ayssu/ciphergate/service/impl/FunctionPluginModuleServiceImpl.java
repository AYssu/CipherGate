package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.config.PluginProperties;
import com.ayssu.ciphergate.entity.FunctionPluginModule;
import com.ayssu.ciphergate.mapper.FunctionPluginModuleMapper;
import com.ayssu.ciphergate.service.FunctionPluginModuleService;
import com.ayssu.ciphergate.service.MinioObjectService;
import com.ayssu.ciphergate.thirdparty.ws.model.FunctionResult;
import com.ayssu.ciphergate.thirdparty.ws.service.FunctionRuntimeService;
import com.ayssu.ciphergate.util.HashUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * 函数插件模块服务实现。
 * <p>
 * 管理 WebSocket 函数执行插件的完整生命周期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionPluginModuleServiceImpl implements FunctionPluginModuleService {

    private static final ObjectMapper CONFIG_OBJECT_MAPPER = new ObjectMapper();

    private final FunctionPluginModuleMapper functionPluginModuleMapper;
    private final MinioObjectService minioObjectService;
    private final PluginManager pluginManager;
    private final PluginProperties pluginProperties;
    private final FunctionRuntimeService functionRuntimeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FunctionPluginModule uploadPlugin(MultipartFile file, String pluginId, String pluginName, String pluginVersion, String remark) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("插件文件不能为空");
        }
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".jar")) {
            throw new RuntimeException("只支持上传 jar 文件");
        }

        PluginJarMetadata metadata = readPluginMetadata(file);
        String resolvedPluginId = StringUtils.hasText(pluginId) ? pluginId : metadata.pluginId();
        String resolvedPluginVersion = StringUtils.hasText(pluginVersion) ? pluginVersion : metadata.pluginVersion();
        String resolvedPluginName = StringUtils.hasText(pluginName) ? pluginName : resolvedPluginId;
        if (!StringUtils.hasText(resolvedPluginId) || !StringUtils.hasText(resolvedPluginVersion)) {
            throw new RuntimeException("无法从 jar 解析 plugin.id 或 plugin.version，请检查 plugin.properties");
        }

        String objectKey = "function-plugins/" + resolvedPluginId + "/" + resolvedPluginVersion + "/" + file.getOriginalFilename();
        String bucketName = minioObjectService.uploadJar(objectKey, file);
        String sha256;
        try (InputStream inputStream = file.getInputStream()) {
            sha256 = HashUtils.sha256Hex(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("读取插件文件失败", e);
        }

        FunctionPluginModule pluginModule = getByPluginIdAndVersion(resolvedPluginId, resolvedPluginVersion);
        if (pluginModule == null) {
            pluginModule = new FunctionPluginModule();
            pluginModule.setPluginId(resolvedPluginId);
            pluginModule.setPluginVersion(resolvedPluginVersion);
            pluginModule.setCreatedAt(LocalDateTime.now());
        }
        pluginModule.setPluginName(resolvedPluginName);
        pluginModule.setBucketName(bucketName);
        pluginModule.setObjectKey(objectKey);
        pluginModule.setSha256(sha256);
        pluginModule.setStatus(0);
        pluginModule.setRemark(remark);
        pluginModule.setFunctions(metadata.functions());
        pluginModule.setConfigSchema(metadata.configSchema());
        pluginModule.setConfigDefaults(metadata.configDefaults());
        if (!StringUtils.hasText(pluginModule.getConfigValues()) && StringUtils.hasText(metadata.configDefaults())) {
            pluginModule.setConfigValues(metadata.configDefaults());
        }
        pluginModule.setUpdatedAt(LocalDateTime.now());

        if (pluginModule.getId() == null) {
            functionPluginModuleMapper.insert(pluginModule);
        } else {
            functionPluginModuleMapper.updateById(pluginModule);
        }
        return pluginModule;
    }

    private PluginJarMetadata readPluginMetadata(MultipartFile file) {
        try (JarInputStream jarInputStream = new JarInputStream(file.getInputStream())) {
            JarEntry entry;
            String pluginId = null;
            String pluginVersion = null;
            String configSchema = null;
            String configDefaults = null;
            String functionsStr = null;
            String functionsDetail = null;

            while ((entry = jarInputStream.getNextJarEntry()) != null) {
                if ("plugin.properties".equals(entry.getName())) {
                    Properties properties = new Properties();
                    properties.load(jarInputStream);
                    pluginId = properties.getProperty("plugin.id");
                    pluginVersion = properties.getProperty("plugin.version");
                    functionsStr = properties.getProperty("plugin.functions");
                } else if ("plugin-config.schema.json".equals(entry.getName())) {
                    configSchema = readJarEntryText(jarInputStream);
                } else if ("plugin-config.defaults.json".equals(entry.getName())) {
                    configDefaults = readJarEntryText(jarInputStream);
                } else if ("plugin-functions.json".equals(entry.getName())) {
                    functionsDetail = readJarEntryText(jarInputStream);
                }
            }

            if (!StringUtils.hasText(pluginId) || !StringUtils.hasText(pluginVersion)) {
                throw new RuntimeException("jar 中未找到 plugin.properties");
            }

            // 优先使用 plugin-functions.json，否则从 plugin.properties 解析
            String functionsJson;
            if (StringUtils.hasText(functionsDetail)) {
                functionsJson = functionsDetail;
            } else if (StringUtils.hasText(functionsStr)) {
                // 将逗号分隔的函数名转换为简化的 JSON 数组
                List<String> functionNames = Arrays.stream(functionsStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                // 转换为标准格式
                List<Map<String, Object>> functionList = new ArrayList<>();
                for (String name : functionNames) {
                    Map<String, Object> func = new LinkedHashMap<>();
                    func.put("name", name);
                    func.put("description", "");
                    func.put("inputExample", Map.of());
                    func.put("outputExample", Map.of());
                    functionList.add(func);
                }
                functionsJson = CONFIG_OBJECT_MAPPER.writeValueAsString(functionList);
            } else {
                functionsJson = null;
            }

            return new PluginJarMetadata(pluginId, pluginVersion, functionsJson, configSchema, configDefaults);
        } catch (Exception e) {
            throw new RuntimeException("解析插件元数据失败: " + e.getMessage(), e);
        }
    }

    private String readJarEntryText(JarInputStream jarInputStream) {
        try {
            return new String(jarInputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("读取插件配置文件失败", e);
        }
    }

    private record PluginJarMetadata(String pluginId, String pluginVersion, String functions, String configSchema, String configDefaults) {
    }

    @Override
    public List<FunctionPluginModule> listPlugins() {
        LambdaQueryWrapper<FunctionPluginModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(FunctionPluginModule::getUpdatedAt);
        return functionPluginModuleMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enablePlugin(Long id) {
        FunctionPluginModule pluginModule = functionPluginModuleMapper.selectById(id);
        if (pluginModule == null) {
            throw new RuntimeException("函数插件不存在");
        }
        String loadedPluginId = loadPluginFromMinio(pluginModule);
        pluginModule.setStatus(1);
        pluginModule.setLoadedPluginId(loadedPluginId);
        pluginModule.setUpdatedAt(LocalDateTime.now());
        functionPluginModuleMapper.updateById(pluginModule);
        log.info("启用函数插件成功: id={}, pluginId={}, version={}, loadedPluginId={}",
                pluginModule.getId(), pluginModule.getPluginId(), pluginModule.getPluginVersion(), loadedPluginId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disablePlugin(Long id) {
        FunctionPluginModule pluginModule = functionPluginModuleMapper.selectById(id);
        if (pluginModule == null) {
            throw new RuntimeException("函数插件不存在");
        }
        if (StringUtils.hasText(pluginModule.getLoadedPluginId()) && pluginManager.getPlugin(pluginModule.getLoadedPluginId()) != null) {
            pluginManager.stopPlugin(pluginModule.getLoadedPluginId());
            pluginManager.unloadPlugin(pluginModule.getLoadedPluginId());
        }
        pluginModule.setStatus(2);
        pluginModule.setUpdatedAt(LocalDateTime.now());
        functionPluginModuleMapper.updateById(pluginModule);
        log.info("停用函数插件成功: id={}, pluginId={}, loadedPluginId={}",
                pluginModule.getId(), pluginModule.getPluginId(), pluginModule.getLoadedPluginId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlugin(Long id) {
        FunctionPluginModule pluginModule = functionPluginModuleMapper.selectById(id);
        if (pluginModule == null) {
            throw new RuntimeException("函数插件不存在");
        }
        if (StringUtils.hasText(pluginModule.getLoadedPluginId()) && pluginManager.getPlugin(pluginModule.getLoadedPluginId()) != null) {
            pluginManager.stopPlugin(pluginModule.getLoadedPluginId());
            pluginManager.unloadPlugin(pluginModule.getLoadedPluginId());
        }
        try {
            minioObjectService.deleteObject(pluginModule.getBucketName(), pluginModule.getObjectKey());
        } catch (Exception e) {
            log.warn("删除 MinIO 对象失败，继续删除数据库记录: bucket={}, objectKey={}",
                    pluginModule.getBucketName(), pluginModule.getObjectKey(), e);
        }
        int updated = functionPluginModuleMapper.softDeleteWithTimestamp(id);
        if (updated <= 0) {
            throw new RuntimeException("删除函数插件失败：记录不存在或已删除");
        }
        log.info("删除函数插件成功: id={}, pluginId={}, loadedPluginId={}",
                id, pluginModule.getPluginId(), pluginModule.getLoadedPluginId());
    }

    @Override
    public void loadEnabledPluginsOnStartup() {
        LambdaQueryWrapper<FunctionPluginModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FunctionPluginModule::getStatus, 1);
        List<FunctionPluginModule> enabled = functionPluginModuleMapper.selectList(wrapper);
        log.info("启动函数插件加载流程，待加载插件数量: {}", enabled.size());
        for (FunctionPluginModule pluginModule : enabled) {
            try {
                String loadedPluginId = loadPluginFromMinio(pluginModule);
                pluginModule.setLoadedPluginId(loadedPluginId);
                pluginModule.setUpdatedAt(LocalDateTime.now());
                functionPluginModuleMapper.updateById(pluginModule);
                log.info("启动加载函数插件成功: pluginId={}, version={}, loadedPluginId={}",
                        pluginModule.getPluginId(), pluginModule.getPluginVersion(), loadedPluginId);
            } catch (Exception e) {
                log.error("启动加载函数插件失败: {}", pluginModule.getPluginId(), e);
                pluginModule.setStatus(3);
                pluginModule.setUpdatedAt(LocalDateTime.now());
                functionPluginModuleMapper.updateById(pluginModule);
            }
        }
    }

    @Override
    public Map<String, Object> getPluginConfigSchema(Long id) {
        FunctionPluginModule pluginModule = functionPluginModuleMapper.selectById(id);
        if (pluginModule == null) {
            throw new RuntimeException("函数插件不存在");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pluginId", pluginModule.getPluginId());
        out.put("pluginVersion", pluginModule.getPluginVersion());
        out.put("functions", pluginModule.getFunctions());
        out.put("configSchema", pluginModule.getConfigSchema());
        out.put("configDefaults", pluginModule.getConfigDefaults());
        return out;
    }

    @Override
    public Map<String, Object> getPluginConfig(Long id) {
        FunctionPluginModule pluginModule = functionPluginModuleMapper.selectById(id);
        if (pluginModule == null) {
            throw new RuntimeException("函数插件不存在");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pluginId", pluginModule.getPluginId());
        out.put("pluginVersion", pluginModule.getPluginVersion());
        out.put("functions", pluginModule.getFunctions());
        out.put("configSchema", pluginModule.getConfigSchema());
        out.put("configDefaults", pluginModule.getConfigDefaults());
        out.put("configValues", pluginModule.getConfigValues());
        return out;
    }

    @Override
    public Map<String, Object> resolveRuntimeConfigValues(String pluginId) {
        if (!StringUtils.hasText(pluginId)) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<FunctionPluginModule> enabled = new LambdaQueryWrapper<>();
        enabled.eq(FunctionPluginModule::getPluginId, pluginId.trim())
                .eq(FunctionPluginModule::getStatus, 1)
                .orderByDesc(FunctionPluginModule::getUpdatedAt)
                .last("limit 1");
        FunctionPluginModule row = functionPluginModuleMapper.selectOne(enabled);
        if (row == null) {
            LambdaQueryWrapper<FunctionPluginModule> any = new LambdaQueryWrapper<>();
            any.eq(FunctionPluginModule::getPluginId, pluginId.trim())
                    .orderByDesc(FunctionPluginModule::getUpdatedAt)
                    .last("limit 1");
            row = functionPluginModuleMapper.selectOne(any);
        }
        if (row == null || !StringUtils.hasText(row.getConfigValues())) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = CONFIG_OBJECT_MAPPER.readValue(
                    row.getConfigValues(), new TypeReference<>() {});
            return parsed == null || parsed.isEmpty() ? Collections.emptyMap() : new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            log.warn("解析函数插件 config_values 失败: pluginId={}", pluginId, e);
            return Collections.emptyMap();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePluginConfig(Long id, Map<String, Object> configValues) {
        FunctionPluginModule pluginModule = functionPluginModuleMapper.selectById(id);
        if (pluginModule == null) {
            throw new RuntimeException("函数插件不存在");
        }
        try {
            String json = new ObjectMapper().writeValueAsString(
                    configValues == null ? Map.of() : configValues
            );
            pluginModule.setConfigValues(json);
            pluginModule.setUpdatedAt(LocalDateTime.now());
            functionPluginModuleMapper.updateById(pluginModule);
        } catch (Exception e) {
            throw new RuntimeException("保存函数插件配置失败: " + e.getMessage(), e);
        }
    }

    private FunctionPluginModule getByPluginIdAndVersion(String pluginId, String pluginVersion) {
        LambdaQueryWrapper<FunctionPluginModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FunctionPluginModule::getPluginId, pluginId)
                .eq(FunctionPluginModule::getPluginVersion, pluginVersion)
                .last("limit 1");
        return functionPluginModuleMapper.selectOne(wrapper);
    }

    private String loadPluginFromMinio(FunctionPluginModule pluginModule) {
        try {
            Path root = Paths.get(pluginProperties.getTempDir()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path localJar = root.resolve(
                    "func-" + pluginModule.getPluginId() + "-" + pluginModule.getPluginVersion() + "-" + UUID.randomUUID() + ".jar");
            log.info("开始从 MinIO 下载函数插件: pluginId={}, bucket={}, objectKey={}, localJar={}",
                    pluginModule.getPluginId(), pluginModule.getBucketName(), pluginModule.getObjectKey(), localJar);
            try (InputStream in = minioObjectService.download(pluginModule.getBucketName(), pluginModule.getObjectKey())) {
                Files.copy(in, localJar);
            }

            if (StringUtils.hasText(pluginModule.getLoadedPluginId())
                    && pluginManager.getPlugin(pluginModule.getLoadedPluginId()) != null) {
                pluginManager.stopPlugin(pluginModule.getLoadedPluginId());
                pluginManager.unloadPlugin(pluginModule.getLoadedPluginId());
                log.info("检测到旧函数插件实例，已卸载: loadedPluginId={}", pluginModule.getLoadedPluginId());
            }

            String loadedPluginId = pluginManager.loadPlugin(localJar);
            pluginManager.startPlugin(loadedPluginId);
            log.info("函数插件加载并启动完成: loadedPluginId={}", loadedPluginId);
            return loadedPluginId;
        } catch (Exception e) {
            throw new RuntimeException("加载函数插件失败", e);
        }
    }

    @Override
    public FunctionResult testFunction(String pluginId, String funcName, Map<String, Object> params) {
        // 刷新函数注册表，确保最新
        functionRuntimeService.refresh();
        return functionRuntimeService.executeFunction(pluginId, funcName, params);
    }

    @Override
    public List<Map<String, Object>> getPluginFunctions(Long id) {
        FunctionPluginModule pluginModule = functionPluginModuleMapper.selectById(id);
        if (pluginModule == null) {
            throw new RuntimeException("函数插件不存在");
        }

        // 直接返回存储的函数详情（从 plugin-functions.json 解析）
        if (StringUtils.hasText(pluginModule.getFunctions())) {
            try {
                // 兼容两种格式：
                // 1. 数组格式: [{"name": "echo"}, ...]
                // 2. 对象格式: {"functions": [{"name": "echo"}, ...]}
                String json = pluginModule.getFunctions().trim();
                if (json.startsWith("[")) {
                    return CONFIG_OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
                } else if (json.startsWith("{")) {
                    Map<String, Object> wrapper = CONFIG_OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
                    Object functions = wrapper.get("functions");
                    if (functions instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> funcList = (List<Map<String, Object>>) functions;
                        return funcList;
                    }
                }
            } catch (Exception e) {
                log.warn("解析函数详情失败: pluginId={}", pluginModule.getPluginId(), e);
            }
        }

        return Collections.emptyList();
    }
}
