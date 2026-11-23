package com.ayssu.ciphergate.portal.service;

import com.ayssu.ciphergate.entity.AppUser;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.portal.entity.*;
import com.ayssu.ciphergate.portal.mapper.*;
import com.ayssu.ciphergate.service.SystemConfigService;
import com.ayssu.ciphergate.util.SkPaymentSignature;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkPortalPaymentCallbackTest {
    @org.junit.jupiter.api.BeforeAll
    static void initializeMybatisMetadata() {
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
            new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, "sk-payment-test"),
            PortalPaymentOrder.class);
    }

    @Test void signedNotifyCannotBeUsedAsReturnAndRetryDoesNotExtendTwice() {
        PortalPaymentOrderMapper orders=mock(PortalPaymentOrderMapper.class);
        ApplicationEpayConfigMapper configs=mock(ApplicationEpayConfigMapper.class);
        AppUserMapper users=mock(AppUserMapper.class);
        PortalPaymentService service=new PortalPaymentService(orders,mock(PortalPricingPlanMapper.class),configs,
            mock(ApplicationMapper.class),users,mock(SystemConfigService.class));
        PortalPaymentOrder order=new PortalPaymentOrder();order.setOrderNo("P1");order.setAppId(10L);
        order.setAppUserId(20L);order.setAmountFen(101L);order.setDurationDays(30);
        when(orders.selectOne(any(Wrapper.class))).thenReturn(order);
        ApplicationEpayConfig config=new ApplicationEpayConfig();config.setEpayPid("123");config.setEpayKey("test-secret");
        when(configs.selectOne(any(Wrapper.class))).thenReturn(config);
        when(orders.update(isNull(),any(Wrapper.class))).thenReturn(1,0);
        AppUser user=new AppUser();user.setId(20L);when(users.selectForUpdate(20L)).thenReturn(user);
        Map<String,String> p=new TreeMap<>(Map.of("pid","123","money","1.01","name","会员","out_trade_no","P1","trade_no","T1","type","alipay","trade_status","TRADE_SUCCESS"));
        SkPaymentSignature.seal(p,"test-secret","payment.notify");
        assertFalse(service.verifyReturnSign("P1",p));
        assertFalse(service.handlePaymentReturn("P1","T1","TRADE_SUCCESS",p));
        assertFalse(service.handlePaymentNotify("P1","other","TRADE_SUCCESS",p));
        assertTrue(service.handlePaymentNotify("P1","T1","TRADE_SUCCESS",p));
        assertTrue(service.handlePaymentNotify("P1","T1","TRADE_SUCCESS",p));
        verify(users,times(1)).updateById(any(AppUser.class));
        p.put("money","2.00");SkPaymentSignature.seal(p,"test-secret","payment.notify");
        assertFalse(service.handlePaymentNotify("P1","T1","TRADE_SUCCESS",p));
        SkPaymentSignature.seal(p,"test-secret","payment.return");assertTrue(service.verifyReturnSign("P1",p));
    }
}
