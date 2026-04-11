package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.LicenseBatchAddTimeDTO;
import com.ayssu.ciphergate.dto.LicenseBatchAddTimeResultDTO;
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
     * 导出卡密为 Excel（.xlsx）
     */
    byte[] exportLicenseKeysExcel(LicenseKeyQueryDTO queryDTO, Long operatorId);

    /**
     * 批量延长到期时间：仅已激活（已首次使用）的卡密处理；未激活的返回失败原因「该卡密未激活」。
     */
    LicenseBatchAddTimeResultDTO batchAddExpiryTime(LicenseBatchAddTimeDTO dto, Long operatorId);

    /**
     * 若已设置到期时间且当前时间已超过到期时间，且卡密非「已禁用」，则将状态落库为已过期（3）。
     * 用于列表/详情展示与三方登录前与数据库保持一致。
     */
    void syncExpiredStatusIfNeeded(LicenseKey licenseKey);

    /**
     * 解绑设备（清空 bindDeviceId），受 unbindLimit 约束（0 表示不限制）。
     * 解绑后用户下次卡密登录可绑定新设备（需开启设备校验时生效）。
     */
    LicenseKey unbindDevice(Long id, Long operatorId);

    /**
     * 解绑 IP（清空 bindIp），受 unbindLimit 约束（0 表示不限制）。
     */
    LicenseKey unbindIp(Long id, Long operatorId);
}
