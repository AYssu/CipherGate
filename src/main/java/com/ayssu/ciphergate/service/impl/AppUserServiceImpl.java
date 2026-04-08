package com.ayssu.ciphergate.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.ayssu.ciphergate.dto.AppUserDTO;
import com.ayssu.ciphergate.dto.AppUserQueryDTO;
import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.entity.AppUserBinding;
import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.mapper.AppUserBindingMapper;
import com.ayssu.ciphergate.mapper.AppUserMapper;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.service.AppUserService;
import com.ayssu.ciphergate.util.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 应用终端用户服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppUserServiceImpl implements AppUserService {
    
    private final AppUserMapper appUserMapper;
    private final AppUserBindingMapper appUserBindingMapper;
    private final ApplicationMapper applicationMapper;
    private final SecurityUtils securityUtils;
    
    @Override
    public Page<AppUser> getAppUserPage(AppUserQueryDTO queryDTO) {
        Page<AppUser> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(queryDTO.getAppId() != null, AppUser::getAppId, queryDTO.getAppId())
               .like(StringUtils.hasText(queryDTO.getUsername()), AppUser::getUsername, queryDTO.getUsername())
               .like(StringUtils.hasText(queryDTO.getEmail()), AppUser::getEmail, queryDTO.getEmail())
               .like(StringUtils.hasText(queryDTO.getPhone()), AppUser::getPhone, queryDTO.getPhone())
               .like(StringUtils.hasText(queryDTO.getNickname()), AppUser::getNickname, queryDTO.getNickname())
               .eq(AppUser::getDeleted, 0)
               .orderByDesc(AppUser::getCreatedAt);
        
        Page<AppUser> result = appUserMapper.selectPage(page, wrapper);
        
        // 填充关联信息
        result.getRecords().forEach(this::fillRelatedInfo);
        
        return result;
    }
    
    @Override
    public AppUser getAppUserById(Long id) {
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        
        fillRelatedInfo(appUser);
        return appUser;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser createAppUser(AppUserDTO dto, Long operatorId) {
        // 验证应用是否存在
        Application application = applicationMapper.selectById(dto.getAppId());
        if (application == null) {
            throw new RuntimeException("应用不存在");
        }
        
        // 检查权限
        if (!hasPermission(dto.getAppId(), operatorId)) {
            throw new RuntimeException("无权限操作此应用的用户");
        }
        
        // 检查用户名是否重复
        LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppUser::getAppId, dto.getAppId())
               .eq(AppUser::getUsername, dto.getUsername())
               .eq(AppUser::getDeleted, 0);
        if (appUserMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查邮箱是否重复
        if (StringUtils.hasText(dto.getEmail())) {
            wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AppUser::getAppId, dto.getAppId())
                   .eq(AppUser::getEmail, dto.getEmail())
                   .eq(AppUser::getDeleted, 0);
            if (appUserMapper.selectCount(wrapper) > 0) {
                throw new RuntimeException("邮箱已存在");
            }
        }
        
        AppUser appUser = new AppUser();
        BeanUtils.copyProperties(dto, appUser);
        
        // 使用 Hutool BCrypt 加密密码
        if (StringUtils.hasText(dto.getPassword())) {
            appUser.setPassword(BCrypt.hashpw(dto.getPassword()));
        }
        
        appUser.setLoginCount(0);
        appUser.setDeleted(0);
        
        LocalDateTime now = LocalDateTime.now();
        appUser.setCreatedAt(now);
        appUser.setUpdatedAt(now);
        
        appUserMapper.insert(appUser);
        
        log.info("创建终端用户成功: username={}, appId={}, operatorId={}", 
                appUser.getUsername(), dto.getAppId(), operatorId);
        
        return appUser;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppUser updateAppUser(Long id, AppUserDTO dto, Long operatorId) {
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查权限
        if (!hasPermission(appUser.getAppId(), operatorId)) {
            throw new RuntimeException("无权限操作此用户");
        }
        
        // 检查邮箱是否重复
        if (StringUtils.hasText(dto.getEmail()) && !dto.getEmail().equals(appUser.getEmail())) {
            LambdaQueryWrapper<AppUser> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AppUser::getAppId, appUser.getAppId())
                   .eq(AppUser::getEmail, dto.getEmail())
                   .eq(AppUser::getDeleted, 0)
                   .ne(AppUser::getId, id);
            if (appUserMapper.selectCount(wrapper) > 0) {
                throw new RuntimeException("邮箱已存在");
            }
        }
        
        // 更新字段
        if (StringUtils.hasText(dto.getEmail())) {
            appUser.setEmail(dto.getEmail());
        }
        if (StringUtils.hasText(dto.getPhone())) {
            appUser.setPhone(dto.getPhone());
        }
        if (StringUtils.hasText(dto.getNickname())) {
            appUser.setNickname(dto.getNickname());
        }
        if (StringUtils.hasText(dto.getAvatarUrl())) {
            appUser.setAvatarUrl(dto.getAvatarUrl());
        }
        if (StringUtils.hasText(dto.getSignature())) {
            appUser.setSignature(dto.getSignature());
        }
        
        appUser.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(appUser);
        
        log.info("更新终端用户成功: id={}, operatorId={}", id, operatorId);
        
        return appUser;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAppUser(Long id, Long operatorId) {
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查权限
        if (!hasPermission(appUser.getAppId(), operatorId)) {
            throw new RuntimeException("无权限操作此用户");
        }
        
        // 软删除
        appUser.setDeleted(1);
        appUser.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(appUser);
        
        log.info("删除终端用户成功: id={}, username={}, operatorId={}", 
                id, appUser.getUsername(), operatorId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword, Long operatorId) {
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查权限
        if (!hasPermission(appUser.getAppId(), operatorId)) {
            throw new RuntimeException("无权限操作此用户");
        }
        
        // 使用 Hutool BCrypt 加密并更新密码
        appUser.setPassword(BCrypt.hashpw(newPassword));
        appUser.setUpdatedAt(LocalDateTime.now());
        appUserMapper.updateById(appUser);
        
        log.info("重置用户密码成功: id={}, username={}, operatorId={}", 
                id, appUser.getUsername(), operatorId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void banUser(Long id, Long bindingId, Boolean ban, String reason, Long operatorId) {
        AppUser appUser = appUserMapper.selectById(id);
        if (appUser == null || appUser.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查权限
        if (!hasPermission(appUser.getAppId(), operatorId)) {
            throw new RuntimeException("无权限操作此用户");
        }
        
        // 更新绑定记录的封禁状态
        if (bindingId != null) {
            AppUserBinding binding = appUserBindingMapper.selectById(bindingId);
            if (binding != null) {
                binding.setIsBanned(ban);
                binding.setBanReason(ban ? reason : null);
                binding.setBanAt(ban ? LocalDateTime.now() : null);
                binding.setStatus(ban ? 3 : 1); // 3=已封禁, 1=正常
                binding.setUpdatedAt(LocalDateTime.now());
                appUserBindingMapper.updateById(binding);
            }
        } else {
            // 封禁该用户的所有绑定
            LambdaQueryWrapper<AppUserBinding> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AppUserBinding::getUserId, id)
                   .eq(AppUserBinding::getDeleted, 0);
            
            appUserBindingMapper.selectList(wrapper).forEach(binding -> {
                binding.setIsBanned(ban);
                binding.setBanReason(ban ? reason : null);
                binding.setBanAt(ban ? LocalDateTime.now() : null);
                binding.setStatus(ban ? 3 : 1);
                binding.setUpdatedAt(LocalDateTime.now());
                appUserBindingMapper.updateById(binding);
            });
        }
        
        log.info("{}用户成功: id={}, username={}, operatorId={}", 
                ban ? "封禁" : "解封", id, appUser.getUsername(), operatorId);
    }
    
    /**
     * 检查用户是否有权限操作应用的终端用户
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
    private void fillRelatedInfo(AppUser appUser) {
        // 填充应用名称
        if (appUser.getAppId() != null) {
            Application application = applicationMapper.selectById(appUser.getAppId());
            if (application != null) {
                appUser.setAppName(application.getAppName());
            }
        }
        
        // 统计绑定设备数量
        LambdaQueryWrapper<AppUserBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppUserBinding::getUserId, appUser.getId())
               .eq(AppUserBinding::getDeleted, 0);
        long count = appUserBindingMapper.selectCount(wrapper);
        appUser.setBindingCount((int) count);
    }
}
