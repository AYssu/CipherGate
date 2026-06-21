package com.ayssu.ciphergate.service.impl;

import com.ayssu.ciphergate.entity.MembershipLevel;
import com.ayssu.ciphergate.mapper.MembershipLevelMapper;
import com.ayssu.ciphergate.service.MembershipLevelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class MembershipLevelServiceImpl extends ServiceImpl<MembershipLevelMapper, MembershipLevel> implements MembershipLevelService {

    @Override
    public List<MembershipLevel> getAllLevels() {
        return list();
    }

    @Override
    public MembershipLevel getLevelByNumber(Integer level) {
        return lambdaQuery().eq(MembershipLevel::getLevel, level).one();
    }
}
