package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.config.PluginProperties;
import com.ayssu.ciphergate.entity.PluginModule;
import com.ayssu.ciphergate.mapper.PluginModuleMapper;
import com.ayssu.ciphergate.service.MinioObjectService;
import com.ayssu.ciphergate.service.PluginModuleService;
import com.ayssu.ciphergate.util.HashUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PluginModuleServiceImpl implements PluginModuleService {

    private static final ObjectMapper CONFIG_OBJECT_MAPPER = new ObjectMapper();

    private final PluginModuleMapper pluginModuleMapper;
    private final MinioObjectService minioObjectService;
    private final PluginManager pluginManager;
    private final PluginProperties pluginProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PluginModule uploadPlugin(MultipartFile file, String pluginId, String pluginName, String pluginVersion, String remark) {
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

        String objectKey = "plugins/" + resolvedPluginId + "/" + resolvedPluginVersion + "/" + file.getOriginalFilename();
        String bucketName = minioObjectService.uploadJar(objectKey, file);
        String sha256;
        try (InputStream inputStream = file.getInputStream()) {
            sha256 = HashUtils.sha256Hex(inputStream);
        } catch (Exception e) {
            throw new RuntimeException("读取插件文件失败", e);
        }

        PluginModule pluginModule = getByPluginIdAndVersion(resolvedPluginId, resolvedPluginVersion);
        if (pluginModule == null) {
            pluginModule = new PluginModule();
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
        pluginModule.setConfigSchema(metadata.configSchema());
        pluginModule.setConfigDefaults(metadata.configDefaults());
        // initialize config values with defaults on upload if not set yet
        if (!StringUtils.hasText(pluginModule.getConfigValues()) && StringUtils.hasText(metadata.configDefaults())) {
            pluginModule.setConfigValues(metadata.configDefaults());
        }
        pluginModule.setUpdatedAt(LocalDateTime.now());

        if (pluginModule.getId() == null) {
            pluginModuleMapper.insert(pluginModule);
        } else {
            pluginModuleMapper.updateById(pluginModule);
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
            while ((entry = jarInputStream.getNextJarEntry()) != null) {
                if ("plugin.properties".equals(entry.getName())) {
                    Properties properties = new Properties();
                    properties.load(jarInputStream);
                    pluginId = properties.getProperty("plugin.id");
                    pluginVersion = properties.getProperty("plugin.version");
                } else if ("plugin-config.schema.json".equals(entry.getName())) {
                    configSchema = readJarEntryText(jarInputStream);
                } else if ("plugin-config.defaults.json".equals(entry.getName())) {
                    configDefaults = readJarEntryText(jarInputStream);
                }
            }
            if (!StringUtils.hasText(pluginId) || !StringUtils.hasText(pluginVersion)) {
                throw new RuntimeException("jar 中未找到 plugin.properties");
            }
            return new PluginJarMetadata(pluginId, pluginVersion, configSchema, configDefaults);
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

    private record PluginJarMetadata(String pluginId, String pluginVersion, String configSchema, String configDefaults) {
    }

    @Override
    public List<PluginModule> listPlugins() {
        LambdaQueryWrapper<PluginModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(PluginModule::getUpdatedAt);
        return pluginModuleMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enablePlugin(Long id) {
        PluginModule pluginModule = pluginModuleMapper.selectById(id);
        if (pluginModule == null) {
            throw new RuntimeException("插件不存在");
        }
        String loadedPluginId = loadPluginFromMinio(pluginModule);
        pluginModule.setStatus(1);
        pluginModule.setLoadedPluginId(loadedPluginId);
        pluginModule.setUpdatedAt(LocalDateTime.now());
        pluginModuleMapper.updateById(pluginModule);
        log.info("手动启用插件成功: id={}, pluginId={}, version={}, loadedPluginId={}",
                pluginModule.getId(), pluginModule.getPluginId(), pluginModule.getPluginVersion(), loadedPluginId);
        logLoadedPluginSummary("enablePlugin");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disablePlugin(Long id) {
        PluginModule pluginModule = pluginModuleMapper.selectById(id);
        if (pluginModule == null) {
            throw new RuntimeException("插件不存在");
        }
        if (StringUtils.hasText(pluginModule.getLoadedPluginId()) && pluginManager.getPlugin(pluginModule.getLoadedPluginId()) != null) {
            pluginManager.stopPlugin(pluginModule.getLoadedPluginId());
            pluginManager.unloadPlugin(pluginModule.getLoadedPluginId());
        }
        pluginModule.setStatus(2);
        pluginModule.setUpdatedAt(LocalDateTime.now());
        pluginModuleMapper.updateById(pluginModule);
        log.info("手动停用插件成功: id={}, pluginId={}, loadedPluginId={}",
                pluginModule.getId(), pluginModule.getPluginId(), pluginModule.getLoadedPluginId());
        logLoadedPluginSummary("disablePlugin");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePlugin(Long id) {
        PluginModule pluginModule = pluginModuleMapper.selectById(id);
        if (pluginModule == null) {
            throw new RuntimeException("插件不存在");
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
        int updated = pluginModuleMapper.softDeleteWithTimestamp(id);
        if (updated <= 0) {
            throw new RuntimeException("删除插件失败：记录不存在或已删除");
        }
        log.info("删除插件成功: id={}, pluginId={}, loadedPluginId={}",
                id, pluginModule.getPluginId(), pluginModule.getLoadedPluginId());
        logLoadedPluginSummary("deletePlugin");
    }

    @Override
    public void loadEnabledPluginsOnStartup() {
        LambdaQueryWrapper<PluginModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PluginModule::getStatus, 1);
        List<PluginModule> enabled = pluginModuleMapper.selectList(wrapper);
        log.info("启动插件加载流程，待加载插件数量: {}", enabled.size());
        for (PluginModule pluginModule : enabled) {
            try {
                String loadedPluginId = loadPluginFromMinio(pluginModule);
                pluginModule.setLoadedPluginId(loadedPluginId);
                pluginModule.setUpdatedAt(LocalDateTime.now());
                pluginModuleMapper.updateById(pluginModule);
                log.info("启动加载插件成功: pluginId={}, version={}, loadedPluginId={}",
                        pluginModule.getPluginId(), pluginModule.getPluginVersion(), loadedPluginId);
            } catch (Exception e) {
                log.error("启动加载插件失败: {}", pluginModule.getPluginId(), e);
                pluginModule.setStatus(3);
                pluginModule.setUpdatedAt(LocalDateTime.now());
                pluginModuleMapper.updateById(pluginModule);
            }
        }
        logLoadedPluginSummary("startup");
    }

    @Override
    public Map<String, Object> getPluginConfigSchema(Long id) {
        PluginModule pluginModule = pluginModuleMapper.selectById(id);
        if (pluginModule == null) {
            throw new RuntimeException("插件不存在");
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("pluginId", pluginModule.getPluginId());
        out.put("pluginVersion", pluginModule.getPluginVersion());
        out.put("configSchema", pluginModule.getConfigSchema());
        out.put("configDefaults", pluginModule.getConfigDefaults());
        return out;
    }

    @Override
    public Map<String, Object> getPluginConfig(Long id) {
        PluginModule pluginModule = pluginModuleMapper.selectById(id);
        if (pluginModule == null) {
            throw new RuntimeException("插件不存在");
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("pluginId", pluginModule.getPluginId());
        out.put("pluginVersion", pluginModule.getPluginVersion());
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
        LambdaQueryWrapper<PluginModule> enabled = new LambdaQueryWrapper<>();
        enabled.eq(PluginModule::getPluginId, pluginId.trim())
                .eq(PluginModule::getStatus, 1)
                .orderByDesc(PluginModule::getUpdatedAt)
                .last("limit 1");
        PluginModule row = pluginModuleMapper.selectOne(enabled);
        if (row == null) {
            LambdaQueryWrapper<PluginModule> any = new LambdaQueryWrapper<>();
            any.eq(PluginModule::getPluginId, pluginId.trim())
                    .orderByDesc(PluginModule::getUpdatedAt)
                    .last("limit 1");
            row = pluginModuleMapper.selectOne(any);
        }
        if (row == null || !StringUtils.hasText(row.getConfigValues())) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = CONFIG_OBJECT_MAPPER.readValue(
                    row.getConfigValues(), new TypeReference<>() {});
            return parsed == null || parsed.isEmpty() ? Collections.emptyMap() : new java.util.LinkedHashMap<>(parsed);
        } catch (Exception e) {
            log.warn("解析插件 config_values 失败: pluginId={}", pluginId, e);
            return Collections.emptyMap();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePluginConfig(Long id, Map<String, Object> configValues) {
        PluginModule pluginModule = pluginModuleMapper.selectById(id);
        if (pluginModule == null) {
            throw new RuntimeException("插件不存在");
        }
        try {
            // store raw JSON string to keep schema-driven flexibility
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                    configValues == null ? Map.of() : configValues
            );
            pluginModule.setConfigValues(json);
            pluginModule.setUpdatedAt(LocalDateTime.now());
            pluginModuleMapper.updateById(pluginModule);
        } catch (Exception e) {
            throw new RuntimeException("保存插件配置失败: " + e.getMessage(), e);
        }
    }

    private PluginModule getByPluginIdAndVersion(String pluginId, String pluginVersion) {
        LambdaQueryWrapper<PluginModule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PluginModule::getPluginId, pluginId)
                .eq(PluginModule::getPluginVersion, pluginVersion)
                .last("limit 1");
        return pluginModuleMapper.selectOne(wrapper);
    }

    private String loadPluginFromMinio(PluginModule pluginModule) {
        try {
            Path root = Paths.get(pluginProperties.getTempDir()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            // Windows 下已加载 jar 可能被锁，使用唯一文件名避免覆盖冲突
            Path localJar = root.resolve(
                    pluginModule.getPluginId() + "-" + pluginModule.getPluginVersion() + "-" + UUID.randomUUID() + ".jar");
            log.info("开始从 MinIO 下载插件: pluginId={}, bucket={}, objectKey={}, localJar={}",
                    pluginModule.getPluginId(), pluginModule.getBucketName(), pluginModule.getObjectKey(), localJar);
            try (InputStream in = minioObjectService.download(pluginModule.getBucketName(), pluginModule.getObjectKey())) {
                Files.copy(in, localJar);
            }

            // 已加载同一插件时先停用再加载，避免重复实例和文件占用
            if (StringUtils.hasText(pluginModule.getLoadedPluginId())
                    && pluginManager.getPlugin(pluginModule.getLoadedPluginId()) != null) {
                pluginManager.stopPlugin(pluginModule.getLoadedPluginId());
                pluginManager.unloadPlugin(pluginModule.getLoadedPluginId());
                log.info("检测到旧插件实例，已卸载: loadedPluginId={}", pluginModule.getLoadedPluginId());
            }

            String loadedPluginId = pluginManager.loadPlugin(localJar);
            pluginManager.startPlugin(loadedPluginId);
            log.info("插件加载并启动完成: loadedPluginId={}", loadedPluginId);
            return loadedPluginId;
        } catch (Exception e) {
            throw new RuntimeException("加载插件失败", e);
        }
    }

    private void logLoadedPluginSummary(String source) {
        String pluginIds = String.join(", ", pluginManager.getPlugins().stream()
                .map(pluginWrapper -> pluginWrapper.getPluginId())
                .toList());
        log.info("插件摘要(source={}): totalLoaded={}, pluginIds=[{}]",
                source, pluginManager.getPlugins().size(), pluginIds);
    }
}
