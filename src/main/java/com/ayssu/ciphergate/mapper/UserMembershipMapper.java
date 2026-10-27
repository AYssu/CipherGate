package com.ayssu.ciphergate.mapper;

import com.ayssu.ciphergate.entity.UserMembership;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMembershipMapper extends BaseMapper<UserMembership> {

    // Lock the stable parent first, including when a legacy user has no membership yet.
    @Select("SELECT id FROM users WHERE id = #{userId} FOR UPDATE")
    Long lockUser(@Param("userId") Long userId);

    @Select("SELECT * FROM user_membership WHERE user_id = #{userId} FOR UPDATE")
    UserMembership selectByUserIdForUpdate(@Param("userId") Long userId);

    @Select("SELECT * FROM user_membership WHERE user_id = #{userId}")
    UserMembership selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM user_membership WHERE invite_code = #{inviteCode}")
    UserMembership selectByInviteCode(@Param("inviteCode") String inviteCode);
}
