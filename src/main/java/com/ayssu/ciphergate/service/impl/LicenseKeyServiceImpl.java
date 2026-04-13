package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.dto.LicenseBatchAddTimeDTO;
import com.ayssu.ciphergate.dto.LicenseBatchAddTimeFailItem;
import com.ayssu.ciphergate.dto.LicenseBatchAddTimeResultDTO;
import com.ayssu.ciphergate.dto.LicenseBatchCreateDTO;
import com.ayssu.ciphergate.dto.LicenseKeyDTO;
import com.ayssu.ciphergate.dto.LicenseKeyQueryDTO;
import com.ayssu.ciphergate.agent.AgentAuthorizationService;
import com.ayssu.ciphergate.agent.AgentPermissionCodes;
import com.ayssu.ciphergate.entity.AppAgent;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.LicenseBatch;
import com.ayssu.ciphergate.entity.LicenseKey;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.mapper.AppAgentMapper;
import com.ayssu.ciphergate.mapper.LicenseBatchMapper;
import com.ayssu.ciphergate.mapper.LicenseKeyMapper;
import com.ayssu.ciphergate.mapper.UserMapper;
import com.ayssu.ciphergate.service.LicenseKeyService;
import com.ayssu.ciphergate.util.SecurityUtils;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import org.apache.poi.ss.usermodel.Sheet;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private final AppAgentMapper appAgentMapper;
    private final UserMapper userMapper;
    private final SecurityUtils securityUtils;
    private final AgentAuthorizationService agentAuthorizationService;
    
    @Override
    public Page<LicenseKey> getLicenseKeyPage(LicenseKeyQueryDTO queryDTO, Long operatorId) {
        Page<LicenseKey> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        LocalDateTime onlineCutoff = LocalDateTime.now().minusMinutes(5);
        log.info("卡密列表查询开始: operatorId={}, appId={}, keyType={}, status={}, batchId={}, keyCode={}",
                operatorId, queryDTO.getAppId(), queryDTO.getKeyType(), queryDTO.getStatus(), queryDTO.getBatchId(), queryDTO.getKeyCode());
        
        LambdaQueryWrapper<LicenseKey> wrapper = new LambdaQueryWrapper<>();
        applyApplicationScopeForLicenseQuery(wrapper, queryDTO, operatorId);
        wrapper.like(StringUtils.hasText(queryDTO.getKeyCode()), LicenseKey::getKeyCode, queryDTO.getKeyCode())
               .eq(StringUtils.hasText(queryDTO.getKeyType()), LicenseKey::getKeyType, queryDTO.getKeyType())
               .eq(queryDTO.getBatchId() != null, LicenseKey::getBatchId, queryDTO.getBatchId())
               .eq(queryDTO.getStatus() != null, LicenseKey::getStatus, queryDTO.getStatus())
               .orderByDesc(LicenseKey::getCreatedAt);
        if (queryDTO.getIsOnline() != null) {
            if (Boolean.TRUE.equals(queryDTO.getIsOnline())) {
                wrapper.ge(LicenseKey::getLastUsedAt, onlineCutoff);
            } else {
                wrapper.and(w -> w.isNull(LicenseKey::getLastUsedAt)
                        .or()
                        .lt(LicenseKey::getLastUsedAt, onlineCutoff));
            }
        }
        
        Page<LicenseKey> result = licenseKeyMapper.selectPage(page, wrapper);
        log.info("卡密列表查询完成: operatorId={}, total={}, records={}",
                operatorId, result.getTotal(), result.getRecords() == null ? 0 : result.getRecords().size());
        
        // 填充关联信息（含到期自动更正状态）
        result.getRecords().forEach(this::fillRelatedInfo);
        
        return result;
    }
    
    @Override
    public LicenseKey getLicenseKeyById(Long id, Long operatorId) {
        LicenseKey licenseKey = licenseKeyMapper.selectById(id);
        if (licenseKey == null) {
            throw new RuntimeException("卡密不存在");
        }
        ensureLicensePermission(licenseKey.getAppId(), operatorId, AgentPermissionCodes.LICENSE_LIST, "无权限查看此卡密");
        
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
        
        AppAgent agent = ensureLicensePermission(dto.getAppId(), userId, AgentPermissionCodes.LICENSE_CREATE, "无权限操作此应用的卡密");
        
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
        if (agent != null) {
            licenseKey.setAgentId(agent.getId());
            agentAuthorizationService.consumeQuotaOrThrow(agent.getId(), licenseKey.getKeyType(), 1);
        }
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
        ensurePresetDurationUnitStored(licenseKey);
        
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
        
        AppAgent agent = ensureLicensePermission(dto.getAppId(), userId, AgentPermissionCodes.LICENSE_CREATE, "无权限操作此应用的卡密");
        if (agent != null) {
            long total = dto.getTotalCount() == null ? 0 : dto.getTotalCount();
            agentAuthorizationService.consumeQuotaOrThrow(agent.getId(), dto.getKeyType(), total);
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
            if (agent != null) {
                licenseKey.setAgentId(agent.getId());
            }
            
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
            ensurePresetDurationUnitStored(licenseKey);
            
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
        ensureLicensePermission(licenseKey.getAppId(), userId, AgentPermissionCodes.LICENSE_UPDATE, "无权限操作此卡密");
        
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
        
        fillRelatedInfo(licenseKey);
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
        ensureLicensePermission(licenseKey.getAppId(), userId, AgentPermissionCodes.LICENSE_DELETE, "无权限操作此卡密");
        
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
        ensureLicensePermission(licenseKey.getAppId(), userId, AgentPermissionCodes.LICENSE_UPDATE, "无权限操作此卡密");
        
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
    public byte[] exportLicenseKeysExcel(LicenseKeyQueryDTO queryDTO, Long operatorId) {
        List<LicenseKey> list = queryLicenseKeysForExport(queryDTO, operatorId);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        ExcelWriter writer = ExcelUtil.getWriter(true);
        try {
            writer.writeHeadRow(List.of(
                    "卡密码", "应用", "类型", "状态", "绑定设备", "绑定IP",
                    "使用次数", "解绑次数", "到期时间", "创建时间"));
            for (LicenseKey k : list) {
                fillRelatedInfo(k);
                List<Object> row = new ArrayList<>();
                row.add(k.getKeyCode());
                row.add(StringUtils.hasText(k.getAppName()) ? k.getAppName() : "");
                row.add(formatExportKeyType(k));
                row.add(formatExportStatus(k.getStatus()));
                row.add(StringUtils.hasText(k.getBindDeviceId()) ? k.getBindDeviceId() : "");
                row.add(StringUtils.hasText(k.getBindIp()) ? k.getBindIp() : "");
                int uc = k.getUseCount() == null ? 0 : k.getUseCount();
                int ul = k.getUseLimit() == null ? 0 : k.getUseLimit();
                row.add(ul <= 0 ? uc + " / 不限" : uc + " / " + ul);
                int ubc = k.getUnbindCount() == null ? 0 : k.getUnbindCount();
                int ubl = k.getUnbindLimit() == null ? 0 : k.getUnbindLimit();
                row.add(ubl <= 0 ? ubc + " / 不限" : ubc + " / " + ubl);
                row.add(k.getExpiresAt() != null ? dtf.format(k.getExpiresAt()) : "");
                row.add(k.getCreatedAt() != null ? dtf.format(k.getCreatedAt()) : "");
                writer.writeRow(row);
            }
            autoSizeExportColumns(writer, 10);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writer.flush(out, true);
            return out.toByteArray();
        } finally {
            writer.close();
        }
    }

    private List<LicenseKey> queryLicenseKeysForExport(LicenseKeyQueryDTO queryDTO, Long operatorId) {
        LambdaQueryWrapper<LicenseKey> wrapper = new LambdaQueryWrapper<>();
        applyApplicationScopeForLicenseQuery(wrapper, queryDTO, operatorId);
        wrapper.like(StringUtils.hasText(queryDTO.getKeyCode()), LicenseKey::getKeyCode, queryDTO.getKeyCode())
               .eq(StringUtils.hasText(queryDTO.getKeyType()), LicenseKey::getKeyType, queryDTO.getKeyType())
               .eq(queryDTO.getBatchId() != null, LicenseKey::getBatchId, queryDTO.getBatchId())
               .eq(queryDTO.getStatus() != null, LicenseKey::getStatus, queryDTO.getStatus())
               .orderByDesc(LicenseKey::getCreatedAt);
        List<LicenseKey> list = licenseKeyMapper.selectList(wrapper);
        list.forEach(this::syncExpiredStatusIfNeeded);
        return list;
    }

    private static void autoSizeExportColumns(ExcelWriter writer, int columnCount) {
        try {
            Sheet sheet = writer.getSheet();
            for (int i = 0; i < columnCount; i++) {
                sheet.autoSizeColumn(i);
            }
        } catch (Exception ignored) {
            // 部分环境下 autoSize 可能失败，不影响导出文件可用性
        }
    }

    private static String formatExportStatus(Integer status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case 1 -> "未使用";
            case 2 -> "使用中";
            case 3 -> "已到期";
            case 4 -> "已禁用";
            default -> String.valueOf(status);
        };
    }

    private static String formatExportKeyType(LicenseKey k) {
        String type = k.getKeyType();
        if (!StringUtils.hasText(type)) {
            return "";
        }
        String t = type.trim().toUpperCase();
        String base = switch (t) {
            case "DAY" -> "天卡";
            case "WEEK" -> "周卡";
            case "MONTH" -> "月卡";
            case "QUARTER" -> "季卡";
            case "HALF_YEAR" -> "半年卡";
            case "YEAR" -> "年卡";
            case "PERMANENT" -> "永久卡";
            case "CUSTOM" -> "自定义";
            default -> type;
        };
        if ("CUSTOM".equals(t) && k.getDurationValue() != null && StringUtils.hasText(k.getDurationUnit())) {
            String unit = switch (k.getDurationUnit().trim().toUpperCase()) {
                case "HOUR", "HOURS" -> "小时";
                case "DAY", "DAYS" -> "天";
                case "MONTH", "MONTHS" -> "月";
                case "YEAR", "YEARS" -> "年";
                default -> k.getDurationUnit();
            };
            return k.getDurationValue() + unit;
        }
        if (!"PERMANENT".equals(t) && !"CUSTOM".equals(t) && k.getDurationValue() != null) {
            return k.getDurationValue() + "x" + base;
        }
        return base;
    }

    private static LocalDateTime plusDurationOnBase(LocalDateTime base, int mult, String unit) {
        if (!StringUtils.hasText(unit)) {
            return base.plusDays(mult);
        }
        String u = unit.trim().toUpperCase();
        return switch (u) {
            case "MIN", "MINUTE", "MINUTES" -> base.plusMinutes(mult);
            case "H", "HOUR", "HOURS" -> base.plusHours(mult);
            case "D", "DAY", "DAYS" -> base.plusDays(mult);
            case "W", "WEEK", "WEEKS" -> base.plusWeeks(mult);
            case "M", "MONTH", "MONTHS" -> base.plusMonths(mult);
            case "Y", "YEAR", "YEARS" -> base.plusYears(mult);
            default -> base.plusDays(mult);
        };
    }

    @Override
    public LicenseBatchAddTimeResultDTO batchAddExpiryTime(LicenseBatchAddTimeDTO dto, Long operatorId) {
        LicenseBatchAddTimeResultDTO result = new LicenseBatchAddTimeResultDTO();
        result.setFailures(new ArrayList<>());
        if (dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new RuntimeException("请至少选择一条卡密");
        }
        Integer dv = dto.getDurationValue();
        if (dv == null || dv < 1) {
            throw new RuntimeException("加时数值必须大于0");
        }
        if (!StringUtils.hasText(dto.getDurationUnit())) {
            throw new RuntimeException("请选择加时单位");
        }
        LocalDateTime now = LocalDateTime.now();
        int success = 0;
        LinkedHashSet<Long> idSet = new LinkedHashSet<>(dto.getIds());
        for (Long id : idSet) {
            if (id == null) {
                continue;
            }
            LicenseKey key = licenseKeyMapper.selectById(id);
            if (key == null) {
                result.getFailures().add(new LicenseBatchAddTimeFailItem(id, null, "卡密不存在"));
                continue;
            }
            if (!hasPermission(key.getAppId(), operatorId)) {
                result.getFailures().add(new LicenseBatchAddTimeFailItem(id, key.getKeyCode(), "无权限操作该卡密"));
                continue;
            }
            if (key.getFirstUsedAt() == null) {
                result.getFailures().add(new LicenseBatchAddTimeFailItem(id, key.getKeyCode(), "该卡密未激活"));
                continue;
            }
            if (key.getStatus() != null && key.getStatus() == 4) {
                result.getFailures().add(new LicenseBatchAddTimeFailItem(id, key.getKeyCode(), "该卡密已禁用"));
                continue;
            }
            if (key.getExpiresAt() == null) {
                result.getFailures().add(new LicenseBatchAddTimeFailItem(id, key.getKeyCode(), "该卡密无到期时间，无法加时"));
                continue;
            }
            LocalDateTime base = key.getExpiresAt();
            if (!base.isAfter(now)) {
                base = now;
            }
            LocalDateTime newExp = plusDurationOnBase(base, dv, dto.getDurationUnit());
            key.setExpiresAt(newExp);
            if (key.getStatus() != null && key.getStatus() == 3 && newExp.isAfter(now)) {
                key.setStatus(2);
            }
            key.setUpdatedAt(now);
            licenseKeyMapper.updateById(key);
            success++;
        }
        result.setSuccessCount(success);
        result.setFailCount(result.getFailures().size());
        log.info("卡密批量加时: operatorId={}, success={}, fail={}", operatorId, success, result.getFailCount());
        return result;
    }

    /**
     * 后台管理员解绑设备：不扣减 {@code expires_at}（与三方换绑扣时无关）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LicenseKey unbindDevice(Long id, Long operatorId) {
        LicenseKey licenseKey = licenseKeyMapper.selectById(id);
        if (licenseKey == null) {
            throw new RuntimeException("卡密不存在");
        }
        ensureLicensePermission(licenseKey.getAppId(), operatorId, AgentPermissionCodes.LICENSE_UPDATE, "无权限操作此卡密");
        if (!StringUtils.hasText(licenseKey.getBindDeviceId())) {
            throw new RuntimeException("当前未绑定设备");
        }
        ensureUnbindQuota(licenseKey);
        bumpUnbindCount(licenseKey);
        LocalDateTime now = LocalDateTime.now();
        // updateById 默认忽略 null 字段，无法清空 bind_device_id，必须用 UpdateWrapper 显式置空
        licenseKeyMapper.update(null, new LambdaUpdateWrapper<LicenseKey>()
                .eq(LicenseKey::getId, licenseKey.getId())
                .set(LicenseKey::getBindDeviceId, null)
                .set(LicenseKey::getUnbindCount, licenseKey.getUnbindCount())
                .set(LicenseKey::getUpdatedAt, now));
        licenseKey.setBindDeviceId(null);
        licenseKey.setUpdatedAt(now);
        log.info("卡密解绑设备: id={}, keyCode={}, operatorId={}", id, licenseKey.getKeyCode(), operatorId);
        fillRelatedInfo(licenseKey);
        return licenseKey;
    }

    /**
     * 后台管理员解绑 IP：不扣减 {@code expires_at}。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LicenseKey unbindIp(Long id, Long operatorId) {
        LicenseKey licenseKey = licenseKeyMapper.selectById(id);
        if (licenseKey == null) {
            throw new RuntimeException("卡密不存在");
        }
        ensureLicensePermission(licenseKey.getAppId(), operatorId, AgentPermissionCodes.LICENSE_UPDATE, "无权限操作此卡密");
        if (!StringUtils.hasText(licenseKey.getBindIp())) {
            throw new RuntimeException("当前未绑定IP");
        }
        ensureUnbindQuota(licenseKey);
        bumpUnbindCount(licenseKey);
        LocalDateTime now = LocalDateTime.now();
        licenseKeyMapper.update(null, new LambdaUpdateWrapper<LicenseKey>()
                .eq(LicenseKey::getId, licenseKey.getId())
                .set(LicenseKey::getBindIp, null)
                .set(LicenseKey::getUnbindCount, licenseKey.getUnbindCount())
                .set(LicenseKey::getUpdatedAt, now));
        licenseKey.setBindIp(null);
        licenseKey.setUpdatedAt(now);
        log.info("卡密解绑IP: id={}, keyCode={}, operatorId={}", id, licenseKey.getKeyCode(), operatorId);
        fillRelatedInfo(licenseKey);
        return licenseKey;
    }

    private void ensureUnbindQuota(LicenseKey key) {
        int limit = key.getUnbindLimit() == null ? 0 : key.getUnbindLimit();
        if (limit <= 0) {
            return;
        }
        int used = key.getUnbindCount() == null ? 0 : key.getUnbindCount();
        if (used >= limit) {
            throw new RuntimeException("解绑次数已达上限（" + limit + " 次），无法继续解绑");
        }
    }

    private void bumpUnbindCount(LicenseKey key) {
        int used = key.getUnbindCount() == null ? 0 : key.getUnbindCount();
        key.setUnbindCount(used + 1);
    }

    @Override
    public void syncExpiredStatusIfNeeded(LicenseKey licenseKey) {
        if (licenseKey == null || licenseKey.getId() == null) {
            return;
        }
        if (licenseKey.getStatus() != null && licenseKey.getStatus() == 4) {
            return;
        }
        if (licenseKey.getExpiresAt() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (licenseKey.getExpiresAt().isAfter(now)) {
            return;
        }
        // 已到期：到期时间 <= 当前时间（含刚好到期）
        if (licenseKey.getStatus() != null && licenseKey.getStatus() == 3) {
            return;
        }
        licenseKey.setStatus(3);
        licenseKey.setUpdatedAt(now);
        licenseKeyMapper.updateById(licenseKey);
    }
    
    /**
     * 检查用户是否有权限操作应用的卡密
     */
    private boolean hasPermission(Long appId, Long userId) {
        if (securityUtils.isAdmin(userId) || agentAuthorizationService.isOwner(appId, userId)) {
            return true;
        }
        return agentAuthorizationService.findEnabledAgentForUser(appId, userId) != null;
    }
    
    private List<Long> listOwnedApplicationIds(Long userId) {
        return applicationMapper.selectList(
                new LambdaQueryWrapper<Application>().eq(Application::getOwnerId, userId))
                .stream()
                .map(Application::getId)
                .toList();
    }
    
    /**
     * 非管理员仅能查询/导出本人拥有应用下的卡密；客户端传入的 ownerId 对非管理员不生效。
     */
    private void applyApplicationScopeForLicenseQuery(LambdaQueryWrapper<LicenseKey> wrapper,
                                                      LicenseKeyQueryDTO queryDTO,
                                                      Long operatorId) {
        if (securityUtils.isAdmin(operatorId)) {
            wrapper.eq(queryDTO.getAppId() != null, LicenseKey::getAppId, queryDTO.getAppId())
                   .eq(queryDTO.getOwnerId() != null, LicenseKey::getOwnerId, queryDTO.getOwnerId());
            return;
        }
        if (queryDTO.getAppId() != null) {
            Long appId = queryDTO.getAppId();
            if (agentAuthorizationService.isOwner(appId, operatorId)) {
                wrapper.eq(LicenseKey::getAppId, appId);
                return;
            }
            AppAgent agent = ensureLicenseListPermission(appId, operatorId, "无权限查询该应用");
            if (agentAuthorizationService.isScopeAllInApp(agent) || hasAgentPermission(agent, AgentPermissionCodes.LICENSE_VIEW_ALL)) {
                wrapper.eq(LicenseKey::getAppId, appId);
            } else {
                wrapper.eq(LicenseKey::getAppId, appId).eq(LicenseKey::getAgentId, agent.getId());
            }
            return;
        }
        List<Long> ownedAppIds = listOwnedApplicationIds(operatorId);
        List<AppAgent> agents = agentAuthorizationService.listEnabledAgentsForUser(operatorId);
        log.info("卡密查询代理范围: operatorId={}, ownedAppIds={}, agentCount={}",
                operatorId, ownedAppIds, agents.size());
        if (ownedAppIds.isEmpty() && agents.isEmpty()) {
            wrapper.apply("1=0");
        } else {
            wrapper.and(w -> {
                boolean hasCond = false;
                if (!ownedAppIds.isEmpty()) {
                    w.in(LicenseKey::getAppId, ownedAppIds);
                    hasCond = true;
                }
                for (AppAgent agent : agents) {
                    boolean canList = canAgentListLicense(agent);
                    boolean viewAll = hasAgentPermission(agent, AgentPermissionCodes.LICENSE_VIEW_ALL);
                    log.info("卡密查询代理命中: operatorId={}, agentId={}, appId={}, canList={}, viewAll={}, scopeMode={}",
                            operatorId, agent.getId(), agent.getAppId(), canList, viewAll, agent.getScopeMode());
                    if (!canAgentListLicense(agent)) {
                        continue;
                    }
                    if (agentAuthorizationService.isScopeAllInApp(agent) || hasAgentPermission(agent, AgentPermissionCodes.LICENSE_VIEW_ALL)) {
                        if (hasCond) {
                            w.or();
                        }
                        w.eq(LicenseKey::getAppId, agent.getAppId());
                        hasCond = true;
                    } else {
                        if (hasCond) {
                            w.or();
                        }
                        w.eq(LicenseKey::getAppId, agent.getAppId()).eq(LicenseKey::getAgentId, agent.getId());
                        hasCond = true;
                    }
                }
                if (!hasCond) {
                    w.apply("1=0");
                }
            });
        }
    }

    private AppAgent ensureLicensePermission(Long appId, Long userId, String permissionCode, String message) {
        if (securityUtils.isAdmin(userId) || agentAuthorizationService.isOwner(appId, userId)) {
            return null;
        }
        AppAgent agent = agentAuthorizationService.findEnabledAgentForUser(appId, userId);
        if (agent == null) {
            throw new RuntimeException(message);
        }
        if (!hasAgentPermission(agent, permissionCode)) {
            throw new RuntimeException(message);
        }
        return agent;
    }

    private AppAgent ensureLicenseListPermission(Long appId, Long userId, String message) {
        if (securityUtils.isAdmin(userId) || agentAuthorizationService.isOwner(appId, userId)) {
            return null;
        }
        AppAgent agent = agentAuthorizationService.findEnabledAgentForUser(appId, userId);
        if (agent == null) {
            throw new RuntimeException(message);
        }
        if (!canAgentListLicense(agent)) {
            throw new RuntimeException(message);
        }
        return agent;
    }

    private boolean canAgentListLicense(AppAgent agent) {
        return hasAgentPermission(agent, AgentPermissionCodes.LICENSE_LIST)
                || hasAgentPermission(agent, AgentPermissionCodes.LICENSE_VIEW_ALL);
    }

    private boolean hasAgentPermission(AppAgent agent, String permissionCode) {
        if (agent == null || !StringUtils.hasText(permissionCode)) {
            return false;
        }
        return agentAuthorizationService.getAgentPermissions(agent.getId()).contains(permissionCode.trim().toUpperCase());
    }
    
    /**
     * 填充关联信息
     */
    /**
     * 预设卡密类型在创建时若未带 durationUnit，写入与类型一致的单位，便于展示与到期计算一致。
     */
    private void ensurePresetDurationUnitStored(LicenseKey licenseKey) {
        if (licenseKey == null || !StringUtils.hasText(licenseKey.getKeyType())) {
            return;
        }
        String kt = licenseKey.getKeyType().trim().toUpperCase();
        if ("CUSTOM".equals(kt) || "PERMANENT".equals(kt)) {
            return;
        }
        if (StringUtils.hasText(licenseKey.getDurationUnit())) {
            return;
        }
        String unit = switch (kt) {
            case "DAY" -> "DAY";
            case "WEEK" -> "WEEK";
            case "MONTH" -> "MONTH";
            case "QUARTER" -> "QUARTER";
            case "HALF_YEAR" -> "HALF_YEAR";
            case "YEAR" -> "YEAR";
            default -> null;
        };
        if (unit != null) {
            licenseKey.setDurationUnit(unit);
        }
    }

    private void fillRelatedInfo(LicenseKey licenseKey) {
        syncExpiredStatusIfNeeded(licenseKey);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(5);
        boolean online = licenseKey.getLastUsedAt() != null && licenseKey.getLastUsedAt().isAfter(cutoff);
        licenseKey.setIsOnline(online);

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

        // 填充创建来源与代理名称
        if (licenseKey.getAgentId() != null) {
            licenseKey.setCreatorType("AGENT");
            AppAgent agent = appAgentMapper.selectById(licenseKey.getAgentId());
            if (agent != null && agent.getUserId() != null) {
                User agentUser = userMapper.selectById(agent.getUserId());
                if (agentUser != null) {
                    String base = StringUtils.hasText(agentUser.getName()) ? agentUser.getName() : agentUser.getLogin();
                    licenseKey.setAgentDisplayName(base + " #" + agentUser.getId());
                } else {
                    licenseKey.setAgentDisplayName("#" + agent.getUserId());
                }
            }
        } else {
            licenseKey.setCreatorType("SELF");
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
