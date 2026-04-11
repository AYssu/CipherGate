package com.ayssu.ciphergate.mapper;

import com.ayssu.ciphergate.entity.AppUserBinding;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 应用用户绑定Mapper
 */
@Mapper
public interface AppUserBindingMapper extends BaseMapper<AppUserBinding> {

    /**
     * 按 app+user+device 查询一行（含已逻辑删除），用于 WS 登录恢复/更新设备绑定。
     */
    AppUserBinding selectByAppUserDeviceRaw(@Param("appId") Long appId, @Param("userId") Long userId, @Param("deviceId") String deviceId);

    int updateWsAccountDeviceTouchById(@Param("id") Long id,
                                       @Param("bindType") String bindType,
                                       @Param("deviceName") String deviceName,
                                       @Param("deviceOs") String deviceOs,
                                       @Param("deviceIp") String deviceIp,
                                       @Param("lastActive") LocalDateTime lastActive);
}
