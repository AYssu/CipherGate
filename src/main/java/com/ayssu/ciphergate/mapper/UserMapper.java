package com.ayssu.ciphergate.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ayssu.ciphergate.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT u.* FROM users u " +
            "INNER JOIN user_roles ur ON u.id = ur.user_id " +
            "INNER JOIN roles r ON ur.role_id = r.id " +
            "WHERE r.role_code = #{roleCode} AND u.status = 1")
    List<User> selectUsersByRoleCode(@Param("roleCode") String roleCode);
}