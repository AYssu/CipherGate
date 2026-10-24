package com.ayssu.ciphergate.mapper;

import com.ayssu.ciphergate.entity.ThirdPartyCredential;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ThirdPartyCredentialMapper extends BaseMapper<ThirdPartyCredential> {
    @Select("SELECT * FROM third_party_credential WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ThirdPartyCredential selectForUpdate(@Param("id") Long id);
}
