package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.dto.AppUserLicenseRechargeRequest;
import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppUserLicenseRechargeServiceTest {
    ApplicationMapper apps; LicenseKeyMapper cards; AppUserMapper users; AppUserLicenseRechargeMapper ledger;
    AppUserLicenseRechargeService service; AppUserLicenseRechargeRequest request;
    Application app; LicenseKey card; AppUser user;
    @BeforeEach void setup() {
        for (Class<?> type : new Class<?>[]{AppUser.class, LicenseKey.class}) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), type);
        }
        apps=mock(ApplicationMapper.class); cards=mock(LicenseKeyMapper.class);
        users=mock(AppUserMapper.class); ledger=mock(AppUserLicenseRechargeMapper.class);
        service=new AppUserLicenseRechargeService(apps,cards,users,ledger);
        request=new AppUserLicenseRechargeRequest(); request.setAppId(10L);
        request.setEmail(" Alice@example.test "); request.setKeyCode(" recharge-card ");
        app=new Application(); app.setId(10L); app.setStatus(1); when(apps.selectById(10L)).thenReturn(app);
        card=new LicenseKey(); card.setId(1L); card.setAppId(10L); card.setKeyCode("RECHARGE-CARD");
        card.setKeyType("DAY"); card.setDurationValue(7); card.setStatus(1);
        when(cards.selectOne(any())).thenReturn(card);
        user=new AppUser(); user.setId(100L); user.setAppId(10L); user.setEmail("alice@example.test");
        when(users.selectOne(any())).thenReturn(user); when(users.selectForUpdate(100L)).thenReturn(user);
        when(users.update(isNull(),any())).thenReturn(1); when(cards.update(isNull(),any())).thenReturn(1);
        when(ledger.insert(any(AppUserLicenseRecharge.class))).thenReturn(1);
    }
    @Test void activeMembershipExtendsAndLedgerDoesNotStoreRawCard() {
        var before=LocalDateTime.of(2035,1,1,0,0); user.setMemberExpiresAt(before);
        var response=service.redeem(request,"192.0.2.1");
        assertEquals(before.plusDays(7),response.memberExpiresAt()); assertEquals(before,response.beforeExpiresAt());
        assertEquals("al***@example.test",response.emailMasked()); assertEquals("RECH****CARD",response.keyCodeMasked());
        var row=ArgumentCaptor.forClass(AppUserLicenseRecharge.class); verify(ledger).insert(row.capture());
        assertEquals(100L,row.getValue().getAppUserId()); assertEquals(10L,row.getValue().getAppId());
        assertEquals(before.plusDays(7),row.getValue().getAfterExpiresAt());
        assertFalse(row.getValue().toString().contains("RECHARGE-CARD"));
    }
    @Test void missingOrExpiredMembershipStartsNow() {
        for (LocalDateTime before : new LocalDateTime[]{null,LocalDateTime.of(2020,1,1,0,0)}) {
            user.setMemberExpiresAt(before); var start=LocalDateTime.now().minusSeconds(1);
            var after=service.redeem(request,null).memberExpiresAt();
            assertTrue(after.isAfter(start.plusDays(7))); assertTrue(after.isBefore(LocalDateTime.now().plusDays(7).plusSeconds(1)));
        }
    }
    @ParameterizedTest @CsvSource({"MINUTE,5,0,5","HOUR,2,2,0","WEEK,2,336,0","MONTH,1,744,0","QUARTER,1,2160,0","HALF_YEAR,1,4344,0","YEAR,1,8760,0"})
    void durationUnitsRespectCalendar(String unit,int amount,long hours,long minutes) {
        var before=LocalDateTime.of(2035,1,1,0,0); user.setMemberExpiresAt(before);
        card.setDurationUnit(unit); card.setDurationValue(amount);
        assertEquals(before.plusHours(hours).plusMinutes(minutes),service.redeem(request,null).memberExpiresAt());
    }
    @Test void disabledUsedRedeemedAndBoundCardsNeverCredit() {
        for(int status : new int[]{2,3,4}) { card.setStatus(status); assertThrows(IllegalArgumentException.class,()->service.redeem(request,null)); }
        card.setStatus(1); card.setRedeemedAt(LocalDateTime.now()); assertThrows(IllegalArgumentException.class,()->service.redeem(request,null));
        card.setRedeemedAt(null); card.setFirstUsedAt(LocalDateTime.now()); assertThrows(IllegalArgumentException.class,()->service.redeem(request,null));
        card.setFirstUsedAt(null); card.setBindUserId(100L); assertThrows(IllegalArgumentException.class,()->service.redeem(request,null));
        verifyNoInteractions(ledger); verify(users,never()).update(isNull(),any());
    }
    @Test void missingCardOrUserAndChangedApplicationNeverCredit() {
        when(cards.selectOne(any())).thenReturn(null); assertThrows(IllegalArgumentException.class,()->service.redeem(request,null));
        when(cards.selectOne(any())).thenReturn(card); when(users.selectOne(any())).thenReturn(null);
        assertThrows(IllegalArgumentException.class,()->service.redeem(request,null));
        when(users.selectOne(any())).thenReturn(user); user.setAppId(20L);
        assertThrows(IllegalArgumentException.class,()->service.redeem(request,null)); verifyNoInteractions(ledger);
    }
    @Test void permanentFixedExpiryAndInvalidDurationNeverCredit() {
        card.setKeyType("PERMANENT"); assertThrows(IllegalArgumentException.class,()->service.redeem(request,null));
        card.setKeyType("DAY"); card.setExpiresAt(LocalDateTime.now().plusDays(1)); assertThrows(IllegalArgumentException.class,()->service.redeem(request,null));
        card.setExpiresAt(null); card.setDurationValue(0); assertThrows(IllegalArgumentException.class,()->service.redeem(request,null));
        card.setDurationValue(1); card.setDurationUnit("INVALID"); assertThrows(IllegalArgumentException.class,()->service.redeem(request,null));
        card.setDurationUnit("YEAR"); card.setDurationValue(Integer.MAX_VALUE); assertThrows(IllegalArgumentException.class,()->service.redeem(request,null));
        verifyNoInteractions(ledger);
    }
    @Test void allDatabaseWritesMustSucceed() {
        when(ledger.insert(any(AppUserLicenseRecharge.class))).thenReturn(0);
        assertThrows(IllegalStateException.class,()->service.redeem(request,null)); verify(users,never()).update(isNull(),any());
        when(ledger.insert(any(AppUserLicenseRecharge.class))).thenReturn(1); when(users.update(isNull(),any())).thenReturn(0);
        assertThrows(IllegalStateException.class,()->service.redeem(request,null)); verify(cards,never()).update(isNull(),any());
        when(users.update(isNull(),any())).thenReturn(1); when(cards.update(isNull(),any())).thenReturn(0);
        assertThrows(IllegalStateException.class,()->service.redeem(request,null));
    }
}
