package com.ayssu.ciphergate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ayssu.ciphergate.entity.ActivityLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 活动日志 Mapper
 */
@Mapper
public interface ActivityLogMapper extends BaseMapper<ActivityLogEntity> {
}
