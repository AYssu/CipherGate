package com.ayssu.ciphergate.portal.controller;

import com.ayssu.ciphergate.entity.Application;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.portal.mapper.ApplicationEpayConfigMapper;
import com.ayssu.ciphergate.portal.mapper.PortalPaymentOrderMapper;
import com.ayssu.ciphergate.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortalPaymentAccessTest {
    ApplicationMapper applications = mock(ApplicationMapper.class);
    ApplicationEpayConfigMapper configurations = mock(ApplicationEpayConfigMapper.class);
    PortalPaymentOrderMapper orders = mock(PortalPaymentOrderMapper.class);
    SecurityUtils security = mock(SecurityUtils.class);
    PortalEpayConfigController epay = new PortalEpayConfigController(configurations, applications, security);
    PortalAdminOrderController admin = new PortalAdminOrderController(orders, security);

    @BeforeEach void setup() {
        var app = new Application(); app.setId(10L); app.setOwnerId(7L);
        when(applications.selectById(10L)).thenReturn(app);
        login(7L);
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void applicationOwnerCanReadAndSaveOwnPaymentSettings() {
        epay.getConfig(10L);
        epay.saveConfig(10L, Map.of("epayUrl", "https://pay.example.test", "epayPid", "123"));
        verify(configurations).insert(any(com.ayssu.ciphergate.portal.entity.ApplicationEpayConfig.class));
    }

    @Test void differentApplicationOwnerCannotReadOrOverwritePaymentKeys() {
        login(8L);
        assertThrows(SecurityException.class, () -> epay.getConfig(10L));
        assertThrows(SecurityException.class, () -> epay.saveConfig(10L, Map.of("epayKey", "attacker")));
        verifyNoInteractions(configurations);
    }

    @Test void administratorCanManageApplicationPaymentSettings() {
        login(8L); when(security.isAdmin(8L)).thenReturn(true);
        epay.getConfig(10L);
        verify(configurations).selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test void anonymousCannotReadPaymentSecretsOrChangePaymentAvailability() {
        SecurityContextHolder.clearContext();
        assertThrows(SecurityException.class, () -> epay.getConfig(10L));
        assertThrows(SecurityException.class, () -> epay.togglePayment(10L, Map.of("enabled", false)));
        verifyNoInteractions(configurations);
    }

    @Test void applicationOwnerCannotEnablePaymentOrReadOtherApplicationsOrders() {
        assertThrows(SecurityException.class, () -> epay.togglePayment(10L, Map.of("enabled", false)));
        assertThrows(SecurityException.class, () -> admin.getAllOrders(1, 20));
        verifyNoInteractions(configurations, orders);
    }

    @Test void superAdministratorCanDisablePaymentsAndQueryBoundedOrderPages() {
        when(security.isSuperAdmin(7L)).thenReturn(true);
        epay.togglePayment(10L, Map.of("enabled", false));
        verify(applications).updateById(any(Application.class));
        admin.getAllOrders(1, 20);
        verify(orders).selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
    }

    @Test void invalidPaginationCannotReachOrderQuery() {
        when(security.isSuperAdmin(7L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> admin.getAllOrders(0, 20));
        assertThrows(IllegalArgumentException.class, () -> admin.getAllOrders(1, 101));
        verifyNoInteractions(orders);
    }

    private void login(Long id) {
        var user = new User(); user.setId(id);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
