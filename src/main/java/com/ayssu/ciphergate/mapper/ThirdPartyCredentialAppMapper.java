package com.ayssu.ciphergate.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 三方充值凭证的授权应用集合；无关联记录的旧凭证回退到 app_id。 */
@Mapper
public interface ThirdPartyCredentialAppMapper {
    @Data
    class Binding {
        private Long credentialId;
        private Long appId;
    }

    @Select("""
        SELECT app_id FROM third_party_credential_app WHERE credential_id = #{id}
        UNION ALL
        SELECT app_id FROM third_party_credential c WHERE c.id = #{id} AND c.deleted = 0
          AND NOT EXISTS (SELECT 1 FROM third_party_credential_app b WHERE b.credential_id = c.id)
        ORDER BY app_id
        """)
    List<Long> selectAppIds(@Param("id") Long id);

    @Select("""
        <script>
        SELECT credential_id, app_id FROM third_party_credential_app WHERE credential_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        UNION ALL
        SELECT c.id AS credential_id, c.app_id FROM third_party_credential c WHERE c.deleted = 0 AND c.id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
          AND NOT EXISTS (SELECT 1 FROM third_party_credential_app b WHERE b.credential_id = c.id)
        ORDER BY app_id
        </script>
        """)
    List<Binding> selectBindings(@Param("ids") List<Long> ids);

    @Select("""
        SELECT COUNT(*) FROM third_party_credential c WHERE c.id = #{id} AND c.deleted = 0 AND
          (EXISTS (SELECT 1 FROM third_party_credential_app b WHERE b.credential_id = c.id AND b.app_id = #{appId})
           OR (c.app_id = #{appId} AND NOT EXISTS
               (SELECT 1 FROM third_party_credential_app b WHERE b.credential_id = c.id)))
        """)
    boolean isAppBound(@Param("id") Long id, @Param("appId") Long appId);

    @Delete("DELETE FROM third_party_credential_app WHERE credential_id = #{id}")
    int deleteBindings(@Param("id") Long id);

    @Insert("""
        <script>
        INSERT INTO third_party_credential_app (credential_id, app_id) VALUES
        <foreach collection="appIds" item="appId" separator=",">(#{id}, #{appId})</foreach>
        </script>
        """)
    int insertBindings(@Param("id") Long id, @Param("appIds") List<Long> appIds);
}
