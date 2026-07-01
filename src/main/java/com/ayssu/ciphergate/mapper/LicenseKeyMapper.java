package com.ayssu.ciphergate.mapper;

import com.ayssu.ciphergate.entity.LicenseKey;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 卡密Mapper
 */
@Mapper
public interface LicenseKeyMapper extends BaseMapper<LicenseKey> {

    @Select("SELECT COUNT(*) FROM license_key WHERE key_code = #{keyCode}")
    int countByKeyCodeGlobal(@Param("keyCode") String keyCode);

    @Select("SELECT COUNT(*) FROM license_key WHERE key_code = #{keyCode} AND deleted = 1")
    int countByKeyCodeDeleted(@Param("keyCode") String keyCode);

    @Select("SELECT COUNT(*) FROM license_key WHERE owner_id = #{userId}")
    long countAllByKeyOwner(@Param("userId") Long userId);
}
