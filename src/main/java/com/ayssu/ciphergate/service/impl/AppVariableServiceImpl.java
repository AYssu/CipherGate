package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.dto.AppVariableDTO;
import com.ayssu.ciphergate.dto.AppVariableQueryDTO;
import com.ayssu.ciphergate.entity.AppVariable;
import com.ayssu.ciphergate.entity.AppVariableHistory;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.AppVariableHistoryMapper;
import com.ayssu.ciphergate.mapper.AppVariableMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.AppVariableService;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 应用变量服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppVariableServiceImpl implements AppVariableService {
    
    private final AppVariableMapper appVariableMapper;
    private final AppVariableHistoryMapper appVariableHistoryMapper;
    private final ApplicationMapper applicationMapper;
    private final SecurityUtils securityUtils;
    private final ObjectMapper objectMapper;
    
    @Override
    public Page<AppVariable> getVariablePage(AppVariableQueryDTO queryDTO, Long operatorId) {
        if (!securityUtils.isAdmin(operatorId)) {
            if (queryDTO.getAppId() == null) {
                throw new RuntimeException("非管理员查询需指定应用ID");
            }
            if (!hasPermission(queryDTO.getAppId(), operatorId)) {
                throw new RuntimeException("无权限查看此应用的变量");
            }
        }
        Page<AppVariable> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        
        LambdaQueryWrapper<AppVariable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(queryDTO.getAppId() != null, AppVariable::getAppId, queryDTO.getAppId())
               .like(StringUtils.hasText(queryDTO.getVariableName()), AppVariable::getVariableName, queryDTO.getVariableName())
               .like(StringUtils.hasText(queryDTO.getDisplayName()), AppVariable::getDisplayName, queryDTO.getDisplayName())
               .eq(StringUtils.hasText(queryDTO.getVariableType()), AppVariable::getVariableType, queryDTO.getVariableType())
               .eq(queryDTO.getEnabled() != null, AppVariable::getEnabled, queryDTO.getEnabled())
               .eq(queryDTO.getCreatedBy() != null, AppVariable::getCreatedBy, queryDTO.getCreatedBy())
               .eq(AppVariable::getDeleted, 0)
               .orderByAsc(AppVariable::getSortOrder)
               .orderByDesc(AppVariable::getCreatedAt);
        
        // 标签模糊查询
        if (StringUtils.hasText(queryDTO.getTag())) {
            wrapper.like(AppVariable::getTags, queryDTO.getTag());
        }
        
        Page<AppVariable> result = appVariableMapper.selectPage(page, wrapper);
        
        // 填充关联信息
        result.getRecords().forEach(this::fillRelatedInfo);
        
        return result;
    }
    
    @Override
    public AppVariable getVariableById(Long id, Long operatorId) {
        AppVariable variable = appVariableMapper.selectById(id);
        if (variable == null || variable.getDeleted() == 1) {
            throw new RuntimeException("变量不存在");
        }
        if (!hasPermission(variable.getAppId(), operatorId)) {
            throw new RuntimeException("无权限查看此变量");
        }
        
        fillRelatedInfo(variable);
        return variable;
    }
    
    @Override
    public AppVariable getVariableByName(Long appId, String variableName, Long operatorId) {
        if (!hasPermission(appId, operatorId)) {
            throw new RuntimeException("无权限查看此应用变量");
        }
        LambdaQueryWrapper<AppVariable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppVariable::getAppId, appId)
               .eq(AppVariable::getVariableName, variableName)
               .eq(AppVariable::getDeleted, 0);
        
        AppVariable variable = appVariableMapper.selectOne(wrapper);
        if (variable != null) {
            fillRelatedInfo(variable);
        }
        return variable;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppVariable createVariable(AppVariableDTO dto, Long operatorId) {
        // 验证应用是否存在
        Application application = applicationMapper.selectById(dto.getAppId());
        if (application == null) {
            throw new RuntimeException("应用不存在");
        }
        
        // 检查权限
        if (!hasPermission(dto.getAppId(), operatorId)) {
            throw new RuntimeException("无权限操作此应用的变量");
        }
        
        // 检查变量名是否重复
        if (getVariableByName(dto.getAppId(), dto.getVariableName(), operatorId) != null) {
            throw new RuntimeException("变量名已存在");
        }
        
        // 验证变量值
        if (StringUtils.hasText(dto.getVariableValue()) && !validateVariableValue(dto)) {
            throw new RuntimeException("变量值格式不正确");
        }
        
        AppVariable variable = new AppVariable();
        BeanUtils.copyProperties(dto, variable);
        
        variable.setDeleted(0);
        variable.setCreatedBy(operatorId);
        variable.setUpdatedBy(operatorId);
        
        LocalDateTime now = LocalDateTime.now();
        variable.setCreatedAt(now);
        variable.setUpdatedAt(now);
        
        appVariableMapper.insert(variable);
        
        // 记录历史
        recordHistory(variable, "CREATE", null, variable.getVariableValue(), dto.getChangeReason(), operatorId);
        
        log.info("创建变量成功: variableName={}, appId={}, operatorId={}", 
                variable.getVariableName(), dto.getAppId(), operatorId);
        
        return variable;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppVariable updateVariable(Long id, AppVariableDTO dto, Long operatorId) {
        AppVariable variable = appVariableMapper.selectById(id);
        if (variable == null || variable.getDeleted() == 1) {
            throw new RuntimeException("变量不存在");
        }
        
        // 检查权限
        if (!hasPermission(variable.getAppId(), operatorId)) {
            throw new RuntimeException("无权限操作此变量");
        }
        
        // 验证变量值
        if (StringUtils.hasText(dto.getVariableValue()) && !validateVariableValue(dto)) {
            throw new RuntimeException("变量值格式不正确");
        }
        
        String oldValue = variable.getVariableValue();
        String newValue = dto.getVariableValue();
        
        // 更新字段
        if (StringUtils.hasText(dto.getDisplayName())) {
            variable.setDisplayName(dto.getDisplayName());
        }
        if (StringUtils.hasText(dto.getDescription())) {
            variable.setDescription(dto.getDescription());
        }
        if (StringUtils.hasText(dto.getVariableType())) {
            variable.setVariableType(dto.getVariableType());
        }
        if (StringUtils.hasText(dto.getVariableValue())) {
            variable.setVariableValue(dto.getVariableValue());
        }
        if (dto.getRequired() != null) {
            variable.setRequired(dto.getRequired());
        }
        if (dto.getSortOrder() != null) {
            variable.setSortOrder(dto.getSortOrder());
        }
        if (StringUtils.hasText(dto.getValidationRules())) {
            variable.setValidationRules(dto.getValidationRules());
        }
        if (StringUtils.hasText(dto.getOptions())) {
            variable.setOptions(dto.getOptions());
        }
        if (dto.getEnabled() != null) {
            variable.setEnabled(dto.getEnabled());
        }
        if (StringUtils.hasText(dto.getVersion())) {
            variable.setVersion(dto.getVersion());
        }
        if (StringUtils.hasText(dto.getTags())) {
            variable.setTags(dto.getTags());
        }
        if (dto.getMetadata() != null) {
            variable.setMetadata(dto.getMetadata());
        }
        
        variable.setUpdatedBy(operatorId);
        variable.setUpdatedAt(LocalDateTime.now());
        
        appVariableMapper.updateById(variable);
        
        // 记录历史
        recordHistory(variable, "UPDATE", oldValue, newValue, dto.getChangeReason(), operatorId);
        
        log.info("更新变量成功: id={}, variableName={}, operatorId={}", 
                id, variable.getVariableName(), operatorId);
        
        return variable;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVariable(Long id, Long operatorId) {
        AppVariable variable = appVariableMapper.selectById(id);
        if (variable == null || variable.getDeleted() == 1) {
            throw new RuntimeException("变量不存在");
        }
        
        // 检查权限
        if (!hasPermission(variable.getAppId(), operatorId)) {
            throw new RuntimeException("无权限操作此变量");
        }
        
        // 软删除
        variable.setDeleted(1);
        variable.setUpdatedBy(operatorId);
        variable.setUpdatedAt(LocalDateTime.now());
        appVariableMapper.updateById(variable);
        
        // 记录历史
        recordHistory(variable, "DELETE", variable.getVariableValue(), null, "删除变量", operatorId);
        
        log.info("删除变量成功: id={}, variableName={}, operatorId={}", 
                id, variable.getVariableName(), operatorId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteVariables(List<Long> ids, Long operatorId) {
        for (Long id : ids) {
            deleteVariable(id, operatorId);
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppVariable copyVariable(Long id, String newVariableName, Long operatorId) {
        AppVariable sourceVariable = getVariableById(id, operatorId);
        
        // 检查权限
        if (!hasPermission(sourceVariable.getAppId(), operatorId)) {
            throw new RuntimeException("无权限操作此变量");
        }
        
        // 检查新变量名是否重复
        if (getVariableByName(sourceVariable.getAppId(), newVariableName, operatorId) != null) {
            throw new RuntimeException("变量名已存在");
        }
        
        AppVariable newVariable = new AppVariable();
        BeanUtils.copyProperties(sourceVariable, newVariable);
        newVariable.setId(null);
        newVariable.setVariableName(newVariableName);
        newVariable.setDisplayName(sourceVariable.getDisplayName() + "_copy");
        newVariable.setCreatedBy(operatorId);
        newVariable.setUpdatedBy(operatorId);
        
        LocalDateTime now = LocalDateTime.now();
        newVariable.setCreatedAt(now);
        newVariable.setUpdatedAt(now);
        
        appVariableMapper.insert(newVariable);
        
        // 记录历史
        recordHistory(newVariable, "CREATE", null, newVariable.getVariableValue(), "复制变量", operatorId);
        
        log.info("复制变量成功: sourceId={}, newVariableName={}, operatorId={}", 
                id, newVariableName, operatorId);
        
        return newVariable;
    }
    
    @Override
    public Map<String, Object> getAppVariables(Long appId, Long operatorId) {
        if (!hasPermission(appId, operatorId)) {
            throw new RuntimeException("无权限查看此应用变量");
        }
        LambdaQueryWrapper<AppVariable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppVariable::getAppId, appId)
               .eq(AppVariable::getEnabled, true)
               .eq(AppVariable::getDeleted, 0);
        
        List<AppVariable> variables = appVariableMapper.selectList(wrapper);
        Map<String, Object> result = new HashMap<>();
        
        for (AppVariable variable : variables) {
            String value = variable.getVariableValue();

            // 类型转换
            Object convertedValue = convertValue(value, variable.getVariableType());
            result.put(variable.getVariableName(), convertedValue);
        }
        
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateVariables(Long appId, Map<String, Object> variables, Long operatorId) {
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String variableName = entry.getKey();
            Object value = entry.getValue();
            
            AppVariable variable = getVariableByName(appId, variableName, operatorId);
            if (variable != null) {
                String oldValue = variable.getVariableValue();
                String newValue = value != null ? value.toString() : null;
                
                variable.setVariableValue(newValue);
                
                variable.setUpdatedBy(operatorId);
                variable.setUpdatedAt(LocalDateTime.now());
                appVariableMapper.updateById(variable);
                
                // 记录历史
                recordHistory(variable, "UPDATE", oldValue, newValue, "批量更新", operatorId);
            }
        }
    }
    
    @Override
    public Page<AppVariableHistory> getVariableHistory(Long variableId, Integer current, Integer size, Long operatorId) {
        AppVariable variable = appVariableMapper.selectById(variableId);
        if (variable == null || variable.getDeleted() == 1) {
            throw new RuntimeException("变量不存在");
        }
        if (!hasPermission(variable.getAppId(), operatorId)) {
            throw new RuntimeException("无权限查看此变量历史");
        }

        Page<AppVariableHistory> page = new Page<>(current, size);
        
        LambdaQueryWrapper<AppVariableHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppVariableHistory::getVariableId, variableId)
               .orderByDesc(AppVariableHistory::getOperatedAt);
        
        return appVariableHistoryMapper.selectPage(page, wrapper);
    }
    
    @Override
    public String exportVariables(Long appId, String format, Long operatorId) {
        Map<String, Object> variables = getAppVariables(appId, operatorId);
        
        try {
            if ("json".equalsIgnoreCase(format)) {
                return objectMapper.writeValueAsString(variables);
            } else {
                // 可以扩展支持其他格式
                return objectMapper.writeValueAsString(variables);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importVariables(Long appId, String configData, String format, Long operatorId) {
        try {
            Map<String, Object> variables;
            if ("json".equalsIgnoreCase(format)) {
                variables = objectMapper.readValue(configData, Map.class);
            } else {
                throw new RuntimeException("不支持的格式: " + format);
            }
            
            batchUpdateVariables(appId, variables, operatorId);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("导入失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean validateVariableValue(AppVariable variable, String value) {
        if (!StringUtils.hasText(value)) {
            return !variable.getRequired();
        }
        
        // 根据类型验证
        switch (variable.getVariableType()) {
            case "NUMBER":
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    return false;
                }
                break;
            case "BOOLEAN":
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    return false;
                }
                break;
            case "JSON":
                try {
                    objectMapper.readTree(value);
                } catch (JsonProcessingException e) {
                    return false;
                }
                break;
            case "ARRAY":
                try {
                    objectMapper.readValue(value, List.class);
                } catch (JsonProcessingException e) {
                    return false;
                }
                break;
        }
        
        // 验证规则
        if (StringUtils.hasText(variable.getValidationRules())) {
            try {
                Map<String, Object> rules = objectMapper.readValue(variable.getValidationRules(), Map.class);
                
                // 长度验证
                if (rules.containsKey("minLength")) {
                    int minLength = (Integer) rules.get("minLength");
                    if (value.length() < minLength) {
                        return false;
                    }
                }
                if (rules.containsKey("maxLength")) {
                    int maxLength = (Integer) rules.get("maxLength");
                    if (value.length() > maxLength) {
                        return false;
                    }
                }
                
                // 正则验证
                if (rules.containsKey("pattern")) {
                    String pattern = (String) rules.get("pattern");
                    if (!Pattern.matches(pattern, value)) {
                        return false;
                    }
                }
            } catch (Exception e) {
                log.warn("验证规则解析失败: {}", e.getMessage());
            }
        }
        
        return true;
    }
    
    private boolean validateVariableValue(AppVariableDTO dto) {
        AppVariable variable = new AppVariable();
        BeanUtils.copyProperties(dto, variable);
        return validateVariableValue(variable, dto.getVariableValue());
    }
    
    /**
     * 检查用户是否有权限操作应用的变量
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
    private void fillRelatedInfo(AppVariable variable) {
        // 填充应用名称
        if (variable.getAppId() != null) {
            Application application = applicationMapper.selectById(variable.getAppId());
            if (application != null) {
                variable.setAppName(application.getAppName());
            }
        }
        
        // 这里可以填充创建者和更新者的用户名
        // variable.setCreatedByUsername(...);
        // variable.setUpdatedByUsername(...);
    }
    
    /**
     * 记录变量变更历史
     */
    private void recordHistory(AppVariable variable, String operationType, String oldValue, String newValue, String changeReason, Long operatorId) {
        AppVariableHistory history = new AppVariableHistory();
        history.setVariableId(variable.getId());
        history.setAppId(variable.getAppId());
        history.setVariableName(variable.getVariableName());
        history.setOperationType(operationType);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setChangeReason(changeReason);
        history.setOperatorId(operatorId);
        history.setVersion(variable.getVersion());
        history.setOperatedAt(LocalDateTime.now());
        
        appVariableHistoryMapper.insert(history);
    }
    
    /**
     * 根据类型转换值
     */
    private Object convertValue(String value, String type) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        
        try {
            switch (type) {
                case "NUMBER":
                    return Double.parseDouble(value);
                case "BOOLEAN":
                    return Boolean.parseBoolean(value);
                case "JSON":
                    return objectMapper.readTree(value);
                case "ARRAY":
                    return objectMapper.readValue(value, List.class);
                default:
                    return value;
            }
        } catch (Exception e) {
            log.warn("值转换失败: value={}, type={}, error={}", value, type, e.getMessage());
            return value;
        }
    }
}