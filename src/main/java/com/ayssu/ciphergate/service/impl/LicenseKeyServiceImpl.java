package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.dto.LicenseBatchCreateDTO;
import com.ayssu.ciphergate.dto.LicenseKeyDTO;
import com.ayssu.ciphergate.dto.LicenseKeyQueryDTO;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.LicenseBatch;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.LicenseBatchMapper;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.LicenseKeyService;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 卡密服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseKeyServiceImpl implements LicenseKeyService {
    
    private final LicenseKeyMapper licenseKeyMapper;
    private final LicenseBatchMapper licenseBatchMapper;
    private final ApplicationMapper applicationMapper;
    private final UserMapper userMapper;
    private final SecurityUtils securityUtils;
    
    @Override
    public Page<LicenseKey> getLicenseKeyPage(LicenseKeyQueryDTO queryDTO) {
        Page<LicenseKey> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        
        LambdaQueryWrapper<LicenseKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(queryDTO.getAppId() != null, LicenseKey::getAppId, queryDTO.getAppId())
               .like(StringUtils.hasText(queryDTO.getKeyCode()), LicenseKey::getKeyCode, queryDTO.getKeyCode())
               .eq(StringUtils.hasText(queryDTO.getKeyType()), LicenseKey::getKeyType, queryDTO.getKeyType())
               .eq(queryDTO.getBatchId() != null, LicenseKey::getBatchId, queryDTO.getBatchId())
               .eq(queryDTO.getStatus() != null, LicenseKey::getStatus, queryDTO.getStatus())
               .eq(queryDTO.getOwnerId() != null, LicenseKey::getOwnerId, queryDTO.getOwnerId())
               .eq(queryDTO.getIsOnline() != null, LicenseKey::getIsOnline, queryDTO.getIsOnline())
               .orderByDesc(LicenseKey::getCreatedAt);
        
        Page<LicenseKey> result = licenseKeyMapper.selectPage(page, wrapper);
        
        // 填充关联信息
        result.getRecords().forEach(this::fillRelatedInfo);
        
        return result;
    }
    
    @Override
    public LicenseKey getLicenseKeyById(Long id) {
        LicenseKey licenseKey = licenseKeyMapper.selectById(id);
        if (licenseKey == null) {
            throw new RuntimeException("卡密不存在");
        }
        
        fillRelatedInfo(licenseKey);
        return licenseKey;
    }
    
    @Override
    public LicenseKey getByKeyCode(String keyCode) {
        LambdaQueryWrapper<LicenseKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LicenseKey::getKeyCode, keyCode);
        return licenseKeyMapper.selectOne(wrapper);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LicenseKey createLicenseKey(LicenseKeyDTO dto, Long userId) {
        // 验证应用是否存在
        Application application = applicationMapper.selectById(dto.getAppId());
        if (application == null) {
            throw new RuntimeException("应用不存在");
        }
        
        // 检查权限
        if (!hasPermission(dto.getAppId(), userId)) {
            throw new RuntimeException("无权限操作此应用的卡密");
        }
        
        LicenseKey licenseKey = new LicenseKey();
        BeanUtils.copyProperties(dto, licenseKey);
        
        // 生成或验证卡密码
        String keyCode;
        if (StringUtils.hasText(dto.getKeyCode())) {
            String input = dto.getKeyCode().trim().toUpperCase();
            
            // 验证格式（只允许字母和数字）
            if (!input.matches("^[A-Z0-9]+$")) {
                throw new RuntimeException("卡密格式不正确，只能包含字母和数字");
            }
            
            if (input.length() >= 6) {
                // 长度>=6，直接使用
                if (input.length() > 64) {
                    throw new RuntimeException("卡密长度不能超过64位");
                }
                keyCode = input;
                
                // 检查在当前应用下是否重复
                if (isKeyCodeExistsInApp(keyCode, dto.getAppId())) {
                    throw new RuntimeException("该卡密在当前应用下已存在");
                }
            } else {
                // 长度<6，作为前缀，后面自动生成
                String prefix = input;
                int remainingLength = 16 - prefix.length(); // 总长度16位
                
                // 生成唯一卡密（带前缀）
                do {
                    keyCode = prefix + generateRandomSuffix(remainingLength);
                } while (isKeyCodeExistsInApp(keyCode, dto.getAppId()));
            }
        } else {
            // 自动生成卡密，并确保在当前应用下唯一
            do {
                keyCode = generateKeyCode();
            } while (isKeyCodeExistsInApp(keyCode, dto.getAppId()));
        }
        
        licenseKey.setKeyCode(keyCode);
        licenseKey.setOwnerId(userId);
        licenseKey.setSource("MANUAL");
        licenseKey.setStatus(1); // 未使用
        licenseKey.setUseCount(0);
        licenseKey.setUnbindCount(0);
        licenseKey.setIsOnline(false);
        licenseKey.setHeartbeatInterval(60);
        
        // 设置时长信息
        if (dto.getDurationValue() != null) {
            licenseKey.setDurationValue(dto.getDurationValue());
        }
        if (StringUtils.hasText(dto.getDurationUnit())) {
            licenseKey.setDurationUnit(dto.getDurationUnit());
        }
        
        // 设置默认值
        if (licenseKey.getUseLimit() == null) {
            licenseKey.setUseLimit(0);
        }
        if (licenseKey.getUnbindLimit() == null) {
            licenseKey.setUnbindLimit(0);
        }
        if (licenseKey.getDeviceCheckEnabled() == null) {
            licenseKey.setDeviceCheckEnabled(true);
        }
        if (licenseKey.getIpCheckEnabled() == null) {
            licenseKey.setIpCheckEnabled(false);
        }
        
        LocalDateTime now = LocalDateTime.now();
        licenseKey.setCreatedAt(now);
        licenseKey.setUpdatedAt(now);
        
        licenseKeyMapper.insert(licenseKey);
        
        log.info("创建卡密成功: keyCode={}, appId={}, userId={}", 
                licenseKey.getKeyCode(), dto.getAppId(), userId);
        
        return licenseKey;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LicenseKey> batchCreateLicenseKeys(LicenseBatchCreateDTO dto, Long userId) {
        // 验证应用是否存在
        Application application = applicationMapper.selectById(dto.getAppId());
        if (application == null) {
            throw new RuntimeException("应用不存在");
        }
        
        // 检查权限
        if (!hasPermission(dto.getAppId(), userId)) {
            throw new RuntimeException("无权限操作此应用的卡密");
        }
        
        // 创建批次
        LicenseBatch batch = new LicenseBatch();
        batch.setAppId(dto.getAppId());
        batch.setCreatorId(userId);
        batch.setBatchName(dto.getBatchName());
        batch.setBatchCode(generateBatchCode());
        batch.setKeyType(dto.getKeyType());
        batch.setDurationValue(dto.getDurationValue());
        batch.setDurationUnit(dto.getDurationUnit());
        batch.setTotalCount(dto.getTotalCount());
        batch.setUsedCount(0);
        batch.setUseLimit(dto.getUseLimit() != null ? dto.getUseLimit() : 0);
        batch.setUnbindLimit(dto.getUnbindLimit() != null ? dto.getUnbindLimit() : 0);
        batch.setDeviceCheckEnabled(dto.getDeviceCheckEnabled() != null ? dto.getDeviceCheckEnabled() : true);
        batch.setIpCheckEnabled(dto.getIpCheckEnabled() != null ? dto.getIpCheckEnabled() : false);
        batch.setRemark(dto.getRemark());
        batch.setCreatedAt(LocalDateTime.now());
        
        licenseBatchMapper.insert(batch);
        
        // 批量生成卡密
        List<LicenseKey> licenseKeys = new ArrayList<>();
        for (int i = 0; i < dto.getTotalCount(); i++) {
            LicenseKey licenseKey = new LicenseKey();
            licenseKey.setAppId(dto.getAppId());
            licenseKey.setOwnerId(userId);
            
            // 生成唯一卡密（在当前应用下唯一）
            String keyCode;
            do {
                keyCode = generateKeyCode();
            } while (isKeyCodeExistsInApp(keyCode, dto.getAppId()));
            
            licenseKey.setKeyCode(keyCode);
            licenseKey.setKeyType(dto.getKeyType());
            licenseKey.setDurationValue(dto.getDurationValue());
            licenseKey.setDurationUnit(dto.getDurationUnit());
            licenseKey.setBatchId(batch.getId());
            licenseKey.setSource("BATCH");
            licenseKey.setStatus(1);
            licenseKey.setUseCount(0);
            licenseKey.setUnbindCount(0);
            licenseKey.setUseLimit(batch.getUseLimit());
            licenseKey.setUnbindLimit(batch.getUnbindLimit());
            licenseKey.setDeviceCheckEnabled(batch.getDeviceCheckEnabled());
            licenseKey.setIpCheckEnabled(batch.getIpCheckEnabled());
            licenseKey.setIsOnline(false);
            licenseKey.setHeartbeatInterval(60);
            
            LocalDateTime now = LocalDateTime.now();
            licenseKey.setCreatedAt(now);
            licenseKey.setUpdatedAt(now);
            
            licenseKeyMapper.insert(licenseKey);
            licenseKeys.add(licenseKey);
        }
        
        log.info("批量生成卡密成功: batchId={}, count={}, appId={}, userId={}", 
                batch.getId(), dto.getTotalCount(), dto.getAppId(), userId);
        
        return licenseKeys;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LicenseKey updateLicenseKey(Long id, LicenseKeyDTO dto, Long userId) {
        LicenseKey licenseKey = licenseKeyMapper.selectById(id);
        if (licenseKey == null) {
            throw new RuntimeException("卡密不存在");
        }
        
        // 检查权限
        if (!hasPermission(licenseKey.getAppId(), userId)) {
            throw new RuntimeException("无权限操作此卡密");
        }
        
        // 更新字段
        if (dto.getUseLimit() != null) {
            licenseKey.setUseLimit(dto.getUseLimit());
        }
        if (dto.getUnbindLimit() != null) {
            licenseKey.setUnbindLimit(dto.getUnbindLimit());
        }
        if (dto.getUseTimeStart() != null) {
            licenseKey.setUseTimeStart(dto.getUseTimeStart());
        }
        if (dto.getUseTimeEnd() != null) {
            licenseKey.setUseTimeEnd(dto.getUseTimeEnd());
        }
        if (dto.getDeviceCheckEnabled() != null) {
            licenseKey.setDeviceCheckEnabled(dto.getDeviceCheckEnabled());
        }
        if (dto.getIpCheckEnabled() != null) {
            licenseKey.setIpCheckEnabled(dto.getIpCheckEnabled());
        }
        if (dto.getRemark() != null) {
            licenseKey.setRemark(dto.getRemark());
        }
        if (dto.getCoreData() != null) {
            licenseKey.setCoreData(dto.getCoreData());
        }
        if (dto.getMetadata() != null) {
            licenseKey.setMetadata(dto.getMetadata());
        }
        
        licenseKey.setUpdatedAt(LocalDateTime.now());
        licenseKeyMapper.updateById(licenseKey);
        
        log.info("更新卡密成功: id={}, userId={}", id, userId);
        
        return licenseKey;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLicenseKey(Long id, Long userId) {
        LicenseKey licenseKey = licenseKeyMapper.selectById(id);
        if (licenseKey == null) {
            throw new RuntimeException("卡密不存在");
        }
        
        // 检查权限
        if (!hasPermission(licenseKey.getAppId(), userId)) {
            throw new RuntimeException("无权限操作此卡密");
        }
        
        // 软删除
        licenseKeyMapper.deleteById(id);
        
        log.info("删除卡密成功: id={}, keyCode={}, userId={}", 
                id, licenseKey.getKeyCode(), userId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status, Long userId) {
        LicenseKey licenseKey = licenseKeyMapper.selectById(id);
        if (licenseKey == null) {
            throw new RuntimeException("卡密不存在");
        }
        
        // 检查权限
        if (!hasPermission(licenseKey.getAppId(), userId)) {
            throw new RuntimeException("无权限操作此卡密");
        }
        
        licenseKey.setStatus(status);
        licenseKey.setUpdatedAt(LocalDateTime.now());
        licenseKeyMapper.updateById(licenseKey);
        
        log.info("更新卡密状态成功: id={}, status={}, userId={}", id, status, userId);
    }
    
    @Override
    public String generateKeyCode() {
        // 生成格式: 16位随机字符（纯字母数字，无横杠）
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 去除易混淆字符 I,O,0,1
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        String keyCode = sb.toString();
        
        // 检查是否重复
        if (getByKeyCode(keyCode) != null) {
            return generateKeyCode(); // 递归重新生成
        }
        
        return keyCode;
    }
    
    /**
     * 生成指定长度的随机后缀
     */
    private String generateRandomSuffix(int length) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return sb.toString();
    }
    
    /**
     * 检查卡密是否在指定应用下已存在
     */
    private boolean isKeyCodeExistsInApp(String keyCode, Long appId) {
        LambdaQueryWrapper<LicenseKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LicenseKey::getKeyCode, keyCode)
               .eq(LicenseKey::getAppId, appId);
        return licenseKeyMapper.selectCount(wrapper) > 0;
    }
    
    @Override
    public List<LicenseKey> exportLicenseKeys(LicenseKeyQueryDTO queryDTO) {
        LambdaQueryWrapper<LicenseKey> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(queryDTO.getAppId() != null, LicenseKey::getAppId, queryDTO.getAppId())
               .like(StringUtils.hasText(queryDTO.getKeyCode()), LicenseKey::getKeyCode, queryDTO.getKeyCode())
               .eq(StringUtils.hasText(queryDTO.getKeyType()), LicenseKey::getKeyType, queryDTO.getKeyType())
               .eq(queryDTO.getBatchId() != null, LicenseKey::getBatchId, queryDTO.getBatchId())
               .eq(queryDTO.getStatus() != null, LicenseKey::getStatus, queryDTO.getStatus())
               .eq(queryDTO.getOwnerId() != null, LicenseKey::getOwnerId, queryDTO.getOwnerId())
               .orderByDesc(LicenseKey::getCreatedAt);
        
        return licenseKeyMapper.selectList(wrapper);
    }
    
    /**
     * 检查用户是否有权限操作应用的卡密
     */
    private boolean hasPermission(Long appId, Long userId) {
        Application application = applicationMapper.selectById(appId);
        if (application == null) {
            return false;
        }
        
        // 应用所有者或管理员可以操作
        return application.getOwnerId().equals(userId) || securityUtils.isAdmin(userId);
    }
    
    /**
     * 填充关联信息
     */
    private void fillRelatedInfo(LicenseKey licenseKey) {
        // 填充应用名称
        if (licenseKey.getAppId() != null) {
            Application application = applicationMapper.selectById(licenseKey.getAppId());
            if (application != null) {
                licenseKey.setAppName(application.getAppName());
            }
        }
        
        // 填充创建者名称
        if (licenseKey.getOwnerId() != null) {
            User user = userMapper.selectById(licenseKey.getOwnerId());
            if (user != null) {
                licenseKey.setOwnerName(user.getName() != null ? user.getName() : user.getLogin());
            }
        }
        
        // 填充批次名称
        if (licenseKey.getBatchId() != null) {
            LicenseBatch batch = licenseBatchMapper.selectById(licenseKey.getBatchId());
            if (batch != null) {
                licenseKey.setBatchName(batch.getBatchName());
            }
        }
    }
    
    /**
     * 生成批次编号
     */
    private String generateBatchCode() {
        return "BATCH-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
