package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.portal.mapper.*;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalOrderPaginationTest {
    @org.junit.jupiter.api.BeforeAll static void metadata() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        for (Class<?> type : java.util.List.of(com.ayssu.ciphergate.portal.entity.PortalPaymentOrder.class,
                com.ayssu.ciphergate.portal.entity.PortalLoginLog.class))
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration,"pagination-test"),type);
    }
    @Test void loginHistoryHasTheSamePaginationBoundaries() {
        var logs=mock(PortalLoginLogMapper.class);
        var dashboard=new PortalDashboardService(logs,mock(AppUserMapper.class),mock(AppUserBindingMapper.class));
        assertThrows(IllegalArgumentException.class,()->dashboard.getLoginHistory(41L,0,10));
        assertThrows(IllegalArgumentException.class,()->dashboard.getLoginHistory(41L,1,Integer.MAX_VALUE));
        verifyNoInteractions(logs);
        dashboard.getLoginHistory(41L,Integer.MAX_VALUE,100);
        var capture=org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(logs).selectList(capture.capture());
        assertTrue(capture.getValue().getCustomSqlSegment().contains("OFFSET 214748364600"));
    }
    final PortalPaymentOrderMapper orders = mock(PortalPaymentOrderMapper.class);
    final PortalPaymentService service = new PortalPaymentService(orders, mock(PortalPricingPlanMapper.class),
        mock(ApplicationEpayConfigMapper.class), mock(ApplicationMapper.class), mock(AppUserMapper.class), mock(SystemConfigService.class));
    @Test void rejectsInvalidOrExcessivePagesBeforeDatabase() {
        for (int[] input : new int[][]{{0,10},{-1,10},{1,0},{1,-1},{1,101},{1,Integer.MAX_VALUE}})
            assertThrows(IllegalArgumentException.class, () -> service.getOrders(1L,input[0],input[1]));
        verifyNoInteractions(orders);
    }
    @Test void extremePageDoesNotOverflowIntAndRetainsOwnerScope() {
        service.getOrders(41L, Integer.MAX_VALUE, 100);
        var capture = org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(orders).selectList(capture.capture());
        String sql = capture.getValue().getCustomSqlSegment();
        assertTrue(sql.contains("LIMIT 100 OFFSET 214748364600"), sql);
        assertTrue(sql.contains("app_user_id"), sql);
    }
}
