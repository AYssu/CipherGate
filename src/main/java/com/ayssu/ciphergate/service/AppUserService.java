package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.AppUserDTO;
import com.ayssu.ciphergate.dto.AppUserQueryDTO;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.AppUserBinding;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.LocalDateTime;

/**
 * 应用终端用户服务接口
 */
public interface AppUserService {
    
    /**
     * 分页查询终端用户
     */
    Page<AppUser> getAppUserPage(AppUserQueryDTO queryDTO, Long operatorId);
    
    /**
     * 根据ID查询终端用户
     */
    AppUser getAppUserById(Long id, Long operatorId);
    
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
    
    /**
     * 获取用户绑定设备列表
     */
    Page<AppUserBinding> getUserBindings(Long userId, Integer current, Integer size, Long operatorId);
    
    /**
     * 解绑用户设备
     */
    void unbindDevice(Long userId, Long bindingId, String reason, Long operatorId);

    /**
     * 在 max(当前时间, 原到期时间) 基础上增加会员天数（充值渠道可复用）
     */
    AppUser extendMemberByDays(Long id, int days, Long operatorId);

    /**
     * 直接设置或清空会员到期时间（null 表示清空）
     */
    AppUser setMemberExpiresAt(Long id, LocalDateTime memberExpiresAt, Long operatorId);
}
