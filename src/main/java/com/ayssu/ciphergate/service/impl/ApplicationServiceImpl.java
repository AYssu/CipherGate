package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.dto.ApplicationDTO;
import com.ayssu.ciphergate.dto.ApplicationQueryDTO;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.ApplicationLog;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.ApplicationLogMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.ApplicationService;
import com.ayssu.ciphergate.service.SystemMessageService;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 应用服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationServiceImpl implements ApplicationService {
    
    private final ApplicationMapper applicationMapper;
    private final ApplicationLogMapper applicationLogMapper;
    private final UserMapper userMapper;
    private final SecurityUtils securityUtils;
    private final SystemMessageService systemMessageService;
    
    @Override
    public Page<Application> getApplicationPage(ApplicationQueryDTO queryDTO) {
        log.info("=== 开始查询应用列表 ===");
        log.info("查询参数: {}", queryDTO);
        
        // 测试：先直接查询所有数据
        List<Application> allApps = applicationMapper.selectList(null);
        log.info("数据库中总共有 {} 条application记录", allApps.size());
        
        Page<Application> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        log.info("分页参数: current={}, size={}", page.getCurrent(), page.getSize());
        
        LambdaQueryWrapper<Application> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(queryDTO.getAppName()), 
                    Application::getAppName, queryDTO.getAppName())
               .eq(StringUtils.hasText(queryDTO.getCategory()), 
                   Application::getCategory, queryDTO.getCategory())
               .eq(queryDTO.getBusinessModel() != null, 
                   Application::getBusinessModel, queryDTO.getBusinessModel())
               .eq(queryDTO.getStatus() != null, 
                   Application::getStatus, queryDTO.getStatus())
               .eq(queryDTO.getOwnerId() != null, 
                   Application::getOwnerId, queryDTO.getOwnerId())
               .orderByDesc(Application::getCreatedAt);
        
        Page<Application> result = applicationMapper.selectPage(page, wrapper);
        
        log.info("查询结果: total={}, records={}, pages={}", 
                result.getTotal(), result.getRecords().size(), result.getPages());
        
        // 填充所属用户名称
        result.getRecords().forEach(app -> {
            User user = userMapper.selectById(app.getOwnerId());
            if (user != null) {
                app.setOwnerName(user.getName() != null ? user.getName() : user.getLogin());
            }
        });
        
        log.info("=== 查询应用列表完成 ===");
        return result;
    }
    
    /**
     * 检查用户是否有权限操作应用
     * @param appId 应用ID
     * @param userId 用户ID
     * @param requireAdmin 是否需要管理员权限
     * @return 是否有权限
     */
    private boolean hasPermission(Long appId, Long userId, boolean requireAdmin) {
        Application application = applicationMapper.selectById(appId);
        if (application == null) {
            return false;
        }
        
        // 检查是否是应用所有者
        if (application.getOwnerId().equals(userId)) {
            return true;
        }
        
        // 检查是否是管理员
        if (securityUtils.isAdmin(userId)) {
            return true;
        }
        
        return false;
    }
    
    @Override
    public Application getApplicationById(Long id, Long userId) {
        Application application = applicationMapper.selectById(id);
        if (application == null) {
            throw new RuntimeException("应用不存在");
        }
        if (!hasPermission(id, userId, false)) {
            throw new RuntimeException("无权限查看此应用");
        }
        
        // 填充所属用户名称
        User user = userMapper.selectById(application.getOwnerId());
        if (user != null) {
            application.setOwnerName(user.getName() != null ? user.getName() : user.getLogin());
        }
        
        return application;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Application createApplication(ApplicationDTO dto, Long userId) {
        Application application = new Application();
        BeanUtils.copyProperties(dto, application);
        
        // 设置所属用户
        application.setOwnerId(userId);
        
        // 生成应用密钥
        Map<String, String> keys = generateAppKeys();
        application.setAppKey(keys.get("appKey"));
        application.setAppSecret(keys.get("appSecret"));
        
        // 设置默认值
        if (application.getIconUrl() == null) {
            application.setIconUrl("/default-app-icon.png");
        }
        if (application.getStatus() == null) {
            application.setStatus(1);
        }
        if (application.getEncryptionPlugin() == null) {
            application.setEncryptionPlugin("rsa-default");
        }
        if (application.getTrafficLimit() == null) {
            application.setTrafficLimit(0L);
        }
        if (application.getTrafficUsed() == null) {
            application.setTrafficUsed(0L);
        }
        
        // 设置时间
        LocalDateTime now = LocalDateTime.now();
        application.setCreatedAt(now);
        application.setUpdatedAt(now);
        
        applicationMapper.insert(application);
        
        // 记录日志
        logOperation(application.getId(), userId, "CREATE", "创建应用: " + application.getAppName(), 
                    "SUCCESS", null, dto);
        
        return application;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Application updateApplication(Long id, ApplicationDTO dto, Long userId) {
        Application application = applicationMapper.selectById(id);
        if (application == null) {
            throw new RuntimeException("应用不存在");
        }
        
        // 检查权限：应用所有者或管理员可以操作
        if (!hasPermission(id, userId, false)) {
            throw new RuntimeException("无权限操作此应用");
        }
        
        // 更新字段
        if (StringUtils.hasText(dto.getAppName())) {
            application.setAppName(dto.getAppName());
        }
        if (dto.getDescription() != null) {
            application.setDescription(dto.getDescription());
        }
        if (dto.getNotice() != null) {
            application.setNotice(dto.getNotice());
        }
        if (dto.getUpdateNotice() != null) {
            application.setUpdateNotice(dto.getUpdateNotice());
        }
        if (dto.getUpdateFileStorageKey() != null) {
            application.setUpdateFileStorageKey(dto.getUpdateFileStorageKey());
        }
        if (dto.getCategory() != null) {
            application.setCategory(dto.getCategory());
        }
        if (dto.getTags() != null) {
            application.setTags(dto.getTags());
        }
        if (dto.getIconUrl() != null) {
            application.setIconUrl(dto.getIconUrl());
        }
        if (dto.getBusinessModel() != null) {
            application.setBusinessModel(dto.getBusinessModel());
        }
        if (dto.getStatus() != null) {
            application.setStatus(dto.getStatus());
        }
        if (dto.getEncryptionPlugin() != null) {
            application.setEncryptionPlugin(dto.getEncryptionPlugin());
        }
        if (dto.getEncryptionConfig() != null) {
            application.setEncryptionConfig(dto.getEncryptionConfig());
        }
        if (dto.getFeatures() != null) {
            application.setFeatures(dto.getFeatures());
        }
        if (dto.getTrafficLimit() != null) {
            application.setTrafficLimit(dto.getTrafficLimit());
        }
        if (dto.getCurrentVersion() != null) {
            application.setCurrentVersion(dto.getCurrentVersion());
        }
        if (dto.getMinVersion() != null) {
            application.setMinVersion(dto.getMinVersion());
        }
        
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(application);
        
        // 记录日志
        logOperation(id, userId, "UPDATE", "更新应用: " + application.getAppName(), 
                    "SUCCESS", null, dto);
        
        return application;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApplication(Long id, Long userId) {
        Application application = applicationMapper.selectById(id);
        if (application == null) {
            throw new RuntimeException("应用不存在");
        }
        
        // 检查权限：应用所有者或管理员可以操作
        if (!hasPermission(id, userId, false)) {
            throw new RuntimeException("无权限操作此应用");
        }
        
        // 软删除
        applicationMapper.deleteById(id);
        
        // 记录日志
        logOperation(id, userId, "DELETE", "删除应用: " + application.getAppName(), 
                    "SUCCESS", null, null);

        // 给操作用户发送站内通知（用于前端角标）
        systemMessageService.createMessage(
                "APP_DELETE",
                "应用删除成功",
                "你已删除应用「" + application.getAppName() + "」(ID: " + id + ")。",
                "MEDIUM",
                "USER",
                userId
        );
    }
    
    @Override
    public Map<String, String> generateAppKeys() {
        Map<String, String> keys = new HashMap<>();
        
        // 生成 appKey (32位随机字符串)
        String appKey = generateRandomString(32);
        
        // 生成 appSecret (64位随机字符串)
        String appSecret = generateRandomString(64);
        
        keys.put("appKey", appKey);
        keys.put("appSecret", appSecret);
        
        return keys;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> resetAppKeys(Long id, Long userId) {
        Application application = applicationMapper.selectById(id);
        if (application == null) {
            throw new RuntimeException("应用不存在");
        }
        
        // 检查权限：应用所有者或管理员可以操作
        if (!hasPermission(id, userId, false)) {
            throw new RuntimeException("无权限操作此应用");
        }
        
        // 生成新密钥
        Map<String, String> keys = generateAppKeys();
        application.setAppKey(keys.get("appKey"));
        application.setAppSecret(keys.get("appSecret"));
        application.setUpdatedAt(LocalDateTime.now());
        
        applicationMapper.updateById(application);
        
        // 记录日志
        logOperation(id, userId, "RESET_KEYS", "重置应用密钥: " + application.getAppName(), 
                    "SUCCESS", null, null);
        
        return keys;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status, Long userId) {
        Application application = applicationMapper.selectById(id);
        if (application == null) {
            throw new RuntimeException("应用不存在");
        }
        
        // 检查权限：应用所有者或管理员可以操作
        if (!hasPermission(id, userId, false)) {
            throw new RuntimeException("无权限操作此应用");
        }
        
        application.setStatus(status);
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(application);
        
        // 记录日志
        String statusDesc = status == 1 ? "正常" : (status == 2 ? "维护" : "停用");
        logOperation(id, userId, "UPDATE_STATUS", 
                    "更新应用状态: " + application.getAppName() + " -> " + statusDesc, 
                    "SUCCESS", null, null);
    }
    
    @Override
    public Map<String, Object> getApplicationStats(Long id) {
        Application application = applicationMapper.selectById(id);
        if (application == null) {
            throw new RuntimeException("应用不存在");
        }
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("appId", application.getId());
        stats.put("appName", application.getAppName());
        stats.put("trafficLimit", application.getTrafficLimit());
        stats.put("trafficUsed", application.getTrafficUsed());
        stats.put("trafficPercent", application.getTrafficLimit() > 0 
            ? (application.getTrafficUsed() * 100.0 / application.getTrafficLimit()) : 0);
        stats.put("status", application.getStatus());
        stats.put("createdAt", application.getCreatedAt());
        
        // TODO: 添加更多统计信息（卡密数量、用户数量、登录次数等）
        
        return stats;
    }

    @Override
    public Map<String, Object> getEncryptionConfig(Long id, Long userId) {
        Application application = getApplicationById(id, userId);
        Map<String, Object> cfg = application.getEncryptionConfig();
        return cfg == null ? new LinkedHashMap<>() : new LinkedHashMap<>(cfg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEncryptionConfig(Long id, Map<String, Object> encryptionConfig, Long userId) {
        Application application = applicationMapper.selectById(id);
        if (application == null) {
            throw new RuntimeException("应用不存在");
        }
        if (!hasPermission(id, userId, false)) {
            throw new RuntimeException("无权限操作此应用");
        }
        application.setEncryptionConfig(encryptionConfig);
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(application);
    }
    
    /**
     * 记录操作日志
     */
    private void logOperation(Long appId, Long userId, String operation, String operationDesc,
                             String result, String errorMessage, Object requestParams) {
        try {
            ApplicationLog log = new ApplicationLog();
            log.setAppId(appId);
            log.setOperatorId(userId);
            
            // 获取操作人名称
            User user = userMapper.selectById(userId);
            if (user != null) {
                log.setOperatorName(user.getName() != null ? user.getName() : user.getLogin());
            }
            
            log.setOperation(operation);
            log.setOperationDesc(operationDesc);
            log.setResponseResult(result);
            log.setErrorMessage(errorMessage);
            
            // 获取请求信息
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                log.setIpAddress(getClientIp(request));
                log.setUserAgent(request.getHeader("User-Agent"));
            }
            
            // 设置请求参数
            if (requestParams != null) {
                log.setRequestParams(convertToMap(requestParams));
            }
            
            log.setCreatedAt(LocalDateTime.now());
            applicationLogMapper.insert(log);
        } catch (Exception e) {
            log.error("记录应用操作日志失败", e);
        }
    }
    
    /**
     * 生成随机字符串
     */
    private String generateRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
    
    /**
     * 转换对象为Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(Object obj) {
        if (obj instanceof Map) {
            return (Map<String, Object>) obj;
        }
        // 简单实现，实际可以使用 Jackson 或其他工具
        return new HashMap<>();
    }
}
