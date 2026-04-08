package com.ayssu.ciphergate.mapper;

import com.ayssu.ciphergate.entity.AppUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用终端用户Mapper
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
}
