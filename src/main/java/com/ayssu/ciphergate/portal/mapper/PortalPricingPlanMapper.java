package com.ayssu.ciphergate.portal.mapper;

import com.ayssu.ciphergate.portal.entity.PortalPricingPlan;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PortalPricingPlanMapper extends BaseMapper<PortalPricingPlan> {
    @Select("SELECT * FROM portal_pricing_plan WHERE id=#{id} FOR UPDATE")
    PortalPricingPlan selectForUpdate(@Param("id") Long id);

    @Update("UPDATE portal_pricing_plan SET stock_remaining=stock_remaining-1, revision=revision+1 "
        + "WHERE id=#{id} AND stock_remaining>0")
    int reserveStock(@Param("id") Long id);

    @Update("UPDATE portal_pricing_plan SET stock_remaining=stock_remaining+1, revision=revision+1 "
        + "WHERE id=#{id} AND stock_remaining IS NOT NULL")
    int releaseStock(@Param("id") Long id);
}
