package com.ayssu.ciphergate.mapper;

import com.ayssu.ciphergate.entity.UserMembership;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMembershipMapper extends BaseMapper<UserMembership> {

    @Select("SELECT * FROM user_membership WHERE user_id = #{userId}")
    UserMembership selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_membership WHERE invite_code = #{inviteCode}")
    UserMembership selectByInviteCode(@Param("inviteCode") String inviteCode);
}
