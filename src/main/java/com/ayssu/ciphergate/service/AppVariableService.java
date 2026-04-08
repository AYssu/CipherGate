package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.AppVariableDTO;
import com.ayssu.ciphergate.dto.AppVariableQueryDTO;
import com.ayssu.ciphergate.entity.AppVariable;
import com.ayssu.ciphergate.entity.AppVariableHistory;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

/**
 * 应用变量服务接口
 */
public interface AppVariableService {
    
    /**
     * 分页查询变量
     */
    Page<AppVariable> getVariablePage(AppVariableQueryDTO queryDTO, Long operatorId);
    
    /**
     * 根据ID查询变量
     */
    AppVariable getVariableById(Long id, Long operatorId);
    
    /**
     * 根据应用ID和变量名称查询
     */
    AppVariable getVariableByName(Long appId, String variableName, Long operatorId);
    
    /**
     * 创建变量
     */
    AppVariable createVariable(AppVariableDTO dto, Long operatorId);
    
    /**
     * 更新变量
     */
    AppVariable updateVariable(Long id, AppVariableDTO dto, Long operatorId);
    
    /**
     * 删除变量
     */
    void deleteVariable(Long id, Long operatorId);
    
    /**
     * 批量删除变量
     */
    void deleteVariables(List<Long> ids, Long operatorId);
    
    /**
     * 复制变量
     */
    AppVariable copyVariable(Long id, String newVariableName, Long operatorId);
    
    /**
     * 获取应用变量（按变量名返回键值）
     */
    Map<String, Object> getAppVariables(Long appId, Long operatorId);
    
    /**
     * 批量更新变量值
     */
    void batchUpdateVariables(Long appId, Map<String, Object> variables, Long operatorId);
    
    /**
     * 获取变量历史记录
     */
    Page<AppVariableHistory> getVariableHistory(Long variableId, Integer current, Integer size, Long operatorId);
    
    /**
     * 导出变量配置
     */
    String exportVariables(Long appId, String format, Long operatorId);
    
    /**
     * 导入变量配置
     */
    void importVariables(Long appId, String configData, String format, Long operatorId);
    
    /**
     * 验证变量值
     */
    boolean validateVariableValue(AppVariable variable, String value);
    
}
