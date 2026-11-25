package com.ayssu.ciphergate.portal.mapper;

import com.ayssu.ciphergate.portal.entity.PortalPaymentOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PortalPaymentOrderMapper extends BaseMapper<PortalPaymentOrder> {
    @Select("SELECT * FROM portal_payment_order WHERE id=#{id} FOR UPDATE")
    PortalPaymentOrder selectForUpdate(@Param("id") Long id);
}
