package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.ApplicationDTO;
import com.ayssu.ciphergate.dto.ApplicationQueryDTO;
import com.ayssu.ciphergate.entity.Application;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 应用服务接口
 */
public interface ApplicationService {
    
    /**
     * 分页查询应用列表
     */
    Page<Application> getApplicationPage(ApplicationQueryDTO queryDTO);
    
    /**
     * 根据ID获取应用详情
     */
    Application getApplicationById(Long id);
    
    /**
     * 创建应用
     */
    Application createApplication(ApplicationDTO dto, Long userId);
    
    /**
     * 更新应用
     */
    Application updateApplication(Long id, ApplicationDTO dto, Long userId);
    
    /**
     * 删除应用
     */
    void deleteApplication(Long id, Long userId);
    
    /**
     * 生成应用密钥
     */
    Map<String, String> generateAppKeys();
    
    /**
     * 重置应用密钥
     */
    Map<String, String> resetAppKeys(Long id, Long userId);
    
    /**
     * 生成加密密钥对
     */
    Map<String, String> generateEncryptionKeys(String pluginId);
    
    /**
     * 更新应用状态
     */
    void updateStatus(Long id, Integer status, Long userId);
    
    /**
     * 获取应用统计信息
     */
    Map<String, Object> getApplicationStats(Long id);
}
