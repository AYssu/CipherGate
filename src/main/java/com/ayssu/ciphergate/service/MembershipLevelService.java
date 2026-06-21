package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.MembershipLevel;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface MembershipLevelService extends IService<MembershipLevel> {

    List<MembershipLevel> getAllLevels();

    MembershipLevel getLevelByNumber(Integer level);
}
