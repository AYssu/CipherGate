package com.ayssu.ciphergate.service;

import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.dto.OpenTrialRequest;
import com.ayssu.ciphergate.service.impl.OpenTrialServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;

class OpenTrialIntegrationAccessTest {
    ApplicationMapper apps;AppUserMapper users;AppUserTrialMapper records;OpenTrialServiceImpl service;AppUser user;Application app;
    @BeforeEach void setup(){
        apps=mock(ApplicationMapper.class);users=mock(AppUserMapper.class);records=mock(AppUserTrialMapper.class);
        service=new OpenTrialServiceImpl(apps,users,records,mock(ActivityLogService.class));
        app=new Application();app.setId(10L);app.setStatus(1);app.setBusinessModel(3);when(apps.selectById(10L)).thenReturn(app);
        user=new AppUser();user.setId(100L);user.setAppId(10L);user.setEmail("user@example.test");user.setMemberExpiresAt(LocalDateTime.of(2035,1,1,12,0));
        when(users.selectOne(any())).thenReturn(user);when(users.updateById(any(AppUser.class))).thenReturn(1);
    }
    @Test void authorizedTrialExtendsExistingMembershipWithoutPassword(){
        var result=service.applyAuthorizedTrial(10L," USER@example.test ","192.0.2.1",null);
        assertEquals(LocalDateTime.of(2035,1,2,12,0),result.expiresAt());assertEquals(1,result.trialDays());
        verify(records).insert(any(AppUserTrial.class));verify(users).updateById(user);
    }
    @Test void duplicateTrialDoesNotUpdateMembership(){
        when(records.insert(any(AppUserTrial.class))).thenThrow(new DuplicateKeyException("unique trial"));
        assertThrows(IllegalStateException.class,()->service.applyAuthorizedTrial(10L,"user@example.test","192.0.2.1",null));verify(users,never()).updateById(any(AppUser.class));
    }
    @Test void publicEntryStillRequiresUserPassword(){
        var request=new OpenTrialRequest();request.setPid("10");request.setEmail("user@example.test");request.setPassword("guess");
        assertThrows(IllegalArgumentException.class,()->service.applyTrial(request,"192.0.2.1",null));verifyNoInteractions(records);
        assertThrows(IllegalArgumentException.class,()->service.queryTrialExpireAt(10L,"user@example.test","guess"));
    }
    @Test void noTrialWhenDisabledOrMissingAccount(){
        app.setBusinessModel(1);assertThrows(IllegalStateException.class,()->service.applyAuthorizedTrial(10L,"user@example.test","192.0.2.1",null));
        app.setBusinessModel(3);when(users.selectOne(any())).thenReturn(null);assertThrows(IllegalArgumentException.class,()->service.applyAuthorizedTrial(10L,"user@example.test","192.0.2.1",null));
    }
}
