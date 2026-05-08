package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.AppUserDTO;
import com.ayssu.ciphergate.dto.AppUserBatchExtendMemberDTO;
import com.ayssu.ciphergate.dto.AppUserBatchExtendMemberDurationDTO;
import com.ayssu.ciphergate.dto.AppUserBatchExtendMemberResultDTO;
import com.ayssu.ciphergate.dto.AppUserQueryDTO;
import com.ayssu.ciphergate.dto.ExtendMemberDurationDTO;
import com.ayssu.ciphergate.dto.AppUserBatchIdsDTO;
import com.ayssu.ciphergate.dto.AppUserBatchBanDTO;
import com.ayssu.ciphergate.dto.AppUserAppNotExpiredDurationDTO;
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
     * 按单位延长会员：MINUTE/HOUR/DAY/WEEK/MONTH/YEAR/PERMANENT
     */
    AppUser extendMemberByDuration(Long id, ExtendMemberDurationDTO body, Long operatorId);

    /**
     * 直接设置或清空会员到期时间（null 表示清空）
     */
    AppUser setMemberExpiresAt(Long id, LocalDateTime memberExpiresAt, Long operatorId);

    /**
     * 批量延长会员天数（逐条处理，返回成功/失败明细）
     */
    AppUserBatchExtendMemberResultDTO batchExtendMemberDays(AppUserBatchExtendMemberDTO dto, Long operatorId);

    /**
     * 批量按单位延长会员：MINUTE/HOUR/DAY/WEEK/MONTH/YEAR/PERMANENT
     */
    AppUserBatchExtendMemberResultDTO batchExtendMemberDuration(AppUserBatchExtendMemberDurationDTO dto, Long operatorId);

    /** 批量按单位扣减会员（不支持 PERMANENT） */
    AppUserBatchExtendMemberResultDTO batchSubtractMemberDuration(AppUserBatchExtendMemberDurationDTO dto, Long operatorId);

    /**
     * 强制下线终端用户的第三方 WS（客户端按 MEMBER_EXPIRED 处理）
     */
    void kickWs(Long id, Long operatorId);

    /** 批量强制下线 WS（CloseStatus=MEMBER_EXPIRED） */
    AppUserBatchExtendMemberResultDTO batchKickWs(AppUserBatchIdsDTO dto, Long operatorId);

    /** 批量封禁/解禁（封禁会踢线） */
    AppUserBatchExtendMemberResultDTO batchBan(AppUserBatchBanDTO dto, Long operatorId);

    /** 批量删除 */
    AppUserBatchExtendMemberResultDTO batchDelete(AppUserBatchIdsDTO dto, Long operatorId);

    /** 选中应用：对该应用下「未到期会员」批量加时 */
    AppUserBatchExtendMemberResultDTO extendNotExpiredInApp(AppUserAppNotExpiredDurationDTO dto, Long operatorId);

    /** 选中应用：对该应用下「未到期会员」批量扣时 */
    AppUserBatchExtendMemberResultDTO subtractNotExpiredInApp(AppUserAppNotExpiredDurationDTO dto, Long operatorId);
}
