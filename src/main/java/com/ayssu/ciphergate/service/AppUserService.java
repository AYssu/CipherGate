package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.AppUserDTO;
import com.ayssu.ciphergate.dto.AppUserQueryDTO;
import com.ayssu.ciphergate.entity.AppUser;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 应用终端用户服务接口
 */
public interface AppUserService {
    
    /**
     * 分页查询终端用户
     */
    Page<AppUser> getAppUserPage(AppUserQueryDTO queryDTO);
    
    /**
     * 根据ID查询终端用户
     */
    AppUser getAppUserById(Long id);
    
    /**
     * 创建终端用户
     */
    AppUser createAppUser(AppUserDTO dto, Long operatorId);
    
    /**
     * 更新终端用户
     */
    AppUser updateAppUser(Long id, AppUserDTO dto, Long operatorId);
    
    /**
     * 删除终端用户
     */
    void deleteAppUser(Long id, Long operatorId);
    
    /**
     * 重置用户密码
     */
    void resetPassword(Long id, String newPassword, Long operatorId);
    
    /**
     * 封禁/解封用户
     */
    void banUser(Long id, Long bindingId, Boolean ban, String reason, Long operatorId);
}
