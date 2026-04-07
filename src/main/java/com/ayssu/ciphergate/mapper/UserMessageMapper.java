package com.ayssu.ciphergate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ayssu.ciphergate.entity.UserMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户消息关联 Mapper
 */
@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessageEntity> {
}
