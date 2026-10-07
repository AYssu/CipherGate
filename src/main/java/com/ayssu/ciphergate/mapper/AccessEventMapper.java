package com.ayssu.ciphergate.mapper;

import com.ayssu.ciphergate.entity.AccessEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.ayssu.ciphergate.dto.DashboardAccessStatsDTO;
import com.ayssu.ciphergate.dto.DashboardAccessRecordDTO;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AccessEventMapper extends BaseMapper<AccessEvent> {
    DashboardAccessStatsDTO selectLoginStats(@Param("appIds") List<Long> appIds,
                                             @Param("start7d") LocalDateTime start7d,
                                             @Param("todayStart") LocalDateTime todayStart,
                                             @Param("end") LocalDateTime end,
                                             @Param("recentCutoff") LocalDateTime recentCutoff);

    long countRecentLogins(@Param("appIds") List<Long> appIds,
                           @Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end);

    List<DashboardAccessRecordDTO> selectRecentLogins(@Param("appIds") List<Long> appIds,
                                                     @Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end,
                                                     @Param("limit") long limit,
                                                     @Param("offset") long offset);
}
