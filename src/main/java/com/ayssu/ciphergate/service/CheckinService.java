package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.CheckinRecord;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface CheckinService extends IService<CheckinRecord> {

    Map<String, Object> doCheckin(Long userId);

    boolean hasCheckedInToday(Long userId);

    CheckinRecord getTodayRecord(Long userId);
}
