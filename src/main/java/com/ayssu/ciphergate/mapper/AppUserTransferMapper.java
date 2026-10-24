package com.ayssu.ciphergate.mapper;

import com.ayssu.ciphergate.entity.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 专用 SQL：覆盖检查必须包含逻辑删除行；所有参数均为绑定值。 */
@Mapper
public interface AppUserTransferMapper {
    @ResultMap("com.ayssu.ciphergate.mapper.ApplicationMapper.mybatis-plus_Application")
    @Select("SELECT * FROM application WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    Application lockApplication(Long id);

    @Select("SELECT id FROM users WHERE id = #{id} FOR UPDATE")
    Long lockOwner(Long id);

    @Select("<script>SELECT * FROM app_user WHERE app_id = #{appId} AND deleted = 0 AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + " ORDER BY id <if test='lock'>FOR UPDATE</if></script>")
    List<AppUser> selectedUsers(@Param("appId") Long appId, @Param("ids") List<Long> ids, @Param("lock") boolean lock);

    // 与数据库排序规则一致，同时检查用户名/邮箱交叉冲突。最多取三行即可判断多账号歧义。
    @Select("<script>SELECT * FROM app_user WHERE app_id = #{appId} "
            + "<if test='activeOnly'>AND deleted = 0 </if>"
            + "AND (username = #{username} OR LOWER(email) = LOWER(#{username}) "
            + "<if test='email != null and email != &quot;&quot;'>"
            + "OR LOWER(username) = LOWER(#{email}) OR LOWER(email) = LOWER(#{email})</if>) "
            + "ORDER BY id LIMIT 3 <if test='lock'>FOR UPDATE</if></script>")
    List<AppUser> conflicts(@Param("appId") Long appId, @Param("username") String username,
                           @Param("email") String email, @Param("activeOnly") boolean activeOnly, @Param("lock") boolean lock);

    @Select("<script>SELECT * FROM app_user_binding WHERE app_id = #{appId} AND user_id = #{userId} "
            + "ORDER BY id <if test='lock'>FOR UPDATE</if></script>")
    List<AppUserBinding> bindings(@Param("appId") Long appId, @Param("userId") Long userId, @Param("lock") boolean lock);

    @Select("<script>SELECT * FROM app_user_trial WHERE app_id = #{appId} AND user_id = #{userId} "
            + "<if test='lock'>FOR UPDATE</if></script>")
    AppUserTrial trial(@Param("appId") Long appId, @Param("userId") Long userId, @Param("lock") boolean lock);

    @Select("SELECT * FROM app_user_binding WHERE app_id=#{appId} AND user_id=#{userId} AND device_id=#{deviceId} FOR UPDATE")
    AppUserBinding deviceBinding(@Param("appId") Long appId, @Param("userId") Long userId, @Param("deviceId") String deviceId);

    @Update("UPDATE app_user_binding SET is_banned=1, status=3, deleted=0, ban_reason=#{reason}, ban_at=#{banAt}, updated_at=#{now} WHERE id=#{id}")
    int retainBan(@Param("id") Long id, @Param("reason") String reason, @Param("banAt") java.time.LocalDateTime banAt,
                  @Param("now") java.time.LocalDateTime now);

    @Insert("INSERT INTO app_user_transfer_batch(request_id,operator_id,source_app_id,target_app_id,mode,force_overwrite,"
            + "create_count,overwrite_count,skip_count,selected_ids) VALUES(#{requestId},#{operatorId},#{sourceAppId},#{targetAppId},"
            + "#{mode},#{forceOverwrite},#{createCount},#{overwriteCount},#{skipCount},#{selectedIds})")
    int claimBatch(com.ayssu.ciphergate.dto.transfer.AppUserTransferBatch batch);

    /** 显式更新 null 字段，不能用 updateById 的默认 NOT_NULL 策略漏掉需要清空的数据。 */
    @Update("UPDATE app_user SET username=#{username}, email=#{email}, phone=#{phone}, password=#{password}, "
            + "nickname=#{nickname}, avatar_url=#{avatarUrl}, signature=#{signature}, member_expires_at=#{memberExpiresAt}, "
            + "updated_at=#{updatedAt} WHERE id=#{id} AND app_id=#{appId} AND deleted=0")
    int overwrite(AppUser user);

    /** 原地恢复逻辑删除的目标行；保留创建时间、代理归属、登录统计和所有历史关联。 */
    @Update("UPDATE app_user SET username=#{username}, email=#{email}, phone=#{phone}, password=#{password}, "
            + "nickname=#{nickname}, avatar_url=#{avatarUrl}, signature=#{signature}, member_expires_at=#{memberExpiresAt}, "
            + "deleted=0, updated_at=#{updatedAt} WHERE id=#{id} AND app_id=#{appId} AND deleted=1")
    int restore(AppUser user);

    @Update("UPDATE app_user_trial SET trial_expires_at=#{trialExpiresAt} WHERE id=#{id}")
    int updateTrial(AppUserTrial trial);
}
