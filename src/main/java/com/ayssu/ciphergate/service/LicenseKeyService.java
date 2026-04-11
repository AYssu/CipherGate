package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.LicenseBatchCreateDTO;
import com.ayssu.ciphergate.dto.LicenseKeyDTO;
import com.ayssu.ciphergate.dto.LicenseKeyQueryDTO;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 卡密服务接口
 */
public interface LicenseKeyService {
    
    /**
     * 分页查询卡密列表
     */
    Page<LicenseKey> getLicenseKeyPage(LicenseKeyQueryDTO queryDTO, Long operatorId);
    
    /**
     * 根据ID获取卡密详情
     */
    LicenseKey getLicenseKeyById(Long id, Long operatorId);
    
    /**
     * 根据卡密码获取卡密
     */
    LicenseKey getByKeyCode(String keyCode);
    
    /**
     * 创建单个卡密
     */
    LicenseKey createLicenseKey(LicenseKeyDTO dto, Long userId);
    
    /**
     * 批量生成卡密
     */
    List<LicenseKey> batchCreateLicenseKeys(LicenseBatchCreateDTO dto, Long userId);
    
    /**
     * 更新卡密
     */
    LicenseKey updateLicenseKey(Long id, LicenseKeyDTO dto, Long userId);
    
    /**
     * 删除卡密
     */
    void deleteLicenseKey(Long id, Long userId);
    
    /**
     * 禁用/启用卡密
     */
    void updateStatus(Long id, Integer status, Long userId);
    
    /**
     * 生成卡密码
     */
    String generateKeyCode();
    
    /**
     * 导出卡密
     */
    List<LicenseKey> exportLicenseKeys(LicenseKeyQueryDTO queryDTO, Long operatorId);
}
