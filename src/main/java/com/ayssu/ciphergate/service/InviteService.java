package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.InviteRecord;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface InviteService extends IService<InviteRecord> {

    String getInviteCode(Long userId);

    void processInvite(String inviteCode, Long newUserId);

    String bindInviteCode(Long userId, String inviteCode);

    Page<InviteRecord> getInviteRecords(Long userId, int page, int size);

    Map<String, Object> getInviteStats(Long userId);
}
