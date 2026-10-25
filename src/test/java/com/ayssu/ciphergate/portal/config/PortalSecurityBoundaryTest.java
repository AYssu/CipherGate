package com.ayssu.ciphergate.portal.config;

import com.ayssu.ciphergate.entity.*;
import com.ayssu.ciphergate.mapper.*;
import com.ayssu.ciphergate.portal.filter.PortalJwtAuthenticationFilter;
import com.ayssu.ciphergate.portal.util.PortalJwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortalSecurityBoundaryTest {
    AnnotationConfigWebApplicationContext context; MockMvc mvc; PortalJwtUtil jwt;AppUser user;
    @Configuration @EnableWebMvc @Import(PortalSecurityConfig.class)
    static class TestConfig {
        @Bean PortalJwtUtil jwt(){return new PortalJwtUtil("portal-config-test-secret-long-enough-123456789",3600000);}
        @Bean AppUserMapper users(){return mock(AppUserMapper.class);}
        @Bean ApplicationMapper apps(){return mock(ApplicationMapper.class);}
        @Bean PortalJwtAuthenticationFilter filter(PortalJwtUtil jwt,AppUserMapper users,ApplicationMapper apps){return new PortalJwtAuthenticationFilter(jwt,users,apps);}
        @Bean Endpoints endpoints(){return new Endpoints();}
    }
    @RestController static class Endpoints {
        @RequestMapping({"/api/portal/settings/profile","/api/portal/payment/create","/api/portal/auth/select-app","/api/portal/auth/verify-email-code"}) String protectedEndpoint(){return "ok";}
    }
    @BeforeEach void setup(){
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(TestConfig.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();jwt=context.getBean(PortalJwtUtil.class);
        user=new AppUser();user.setId(7L);user.setAppId(3L);user.setEmail("u@example.test");user.setPassword("stored-hash");
        when(context.getBean(AppUserMapper.class).selectById(7L)).thenReturn(user);
        Application app=new Application();app.setId(3L);app.setStatus(1);when(context.getBean(ApplicationMapper.class).selectById(3L)).thenReturn(app);
    }
    @AfterEach void close(){context.close();}
    @Test void anonymousEmailChangeChallengeIsUnauthorized()throws Exception{mvc.perform(post("/api/portal/auth/verify-email-code")).andExpect(status().isUnauthorized());}
    @Test void selectionTicketCannotReadSettingsSendEmailOrCreatePayment()throws Exception{
        String token=jwt.generateSelectionToken(user.getEmail(),List.of(user));
        for(String path:List.of("/api/portal/settings/profile","/api/portal/auth/verify-email-code","/api/portal/payment/create"))
            mvc.perform(post(path).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/portal/auth/select-app").header("Authorization","Bearer "+token)).andExpect(status().isOk());
    }
    @Test void formalSessionCanUseSettingsButCannotActAsSelectionTicket()throws Exception{
        String token=jwt.generateSessionToken(user);
        mvc.perform(get("/api/portal/settings/profile").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(post("/api/portal/auth/select-app").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
}
