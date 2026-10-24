package com.ayssu.ciphergate.mapper;

import com.ayssu.ciphergate.entity.AppUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 应用终端用户Mapper
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
    @Select("SELECT * FROM app_user WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    AppUser selectForUpdate(@Param("id") Long id);
}
