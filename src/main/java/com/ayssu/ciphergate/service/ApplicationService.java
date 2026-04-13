package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.ApplicationDTO;
import com.ayssu.ciphergate.dto.ApplicationQueryDTO;
import com.ayssu.ciphergate.entity.Application;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.multipart.MultipartFile;

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
    Application getApplicationById(Long id, Long userId);
    
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
     * 更新应用状态
     */
    void updateStatus(Long id, Integer status, Long userId);
    
    /**
     * 获取应用统计信息（需为应用所有者或管理员）
     */
    Map<String, Object> getApplicationStats(Long id, Long userId);

    Map<String, Object> getEncryptionConfig(Long id, Long userId);

    void updateEncryptionConfig(Long id, Map<String, Object> encryptionConfig, Long userId);

    /**
     * 上传应用更新包到 MinIO，并写入 {@code update_file_storage_key}（覆盖旧对象）。
     */
    Application uploadUpdatePackage(Long id, MultipartFile file, Long userId);
}
