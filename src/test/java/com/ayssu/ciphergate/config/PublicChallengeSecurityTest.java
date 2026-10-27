package com.ayssu.ciphergate.config;

import com.ayssu.ciphergate.controller.PublicChallengeController;
import com.ayssu.ciphergate.service.*;
import com.ayssu.ciphergate.mapper.ApplicationMapper;
import com.ayssu.ciphergate.thirdparty.auth.*;
import com.ayssu.ciphergate.thirdparty.crypto.CryptoRuntimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicChallengeSecurityTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, PublicChallengeController.class, com.ayssu.ciphergate.controller.CsrfController.class})
    static class TestConfig {
        @Bean OAuth2LoginSuccessHandler success(){return mock(OAuth2LoginSuccessHandler.class);}
        @Bean OAuth2LoginFailureHandler failure(){return mock(OAuth2LoginFailureHandler.class);}
        @Bean CustomOAuth2UserService oauthUsers(){return mock(CustomOAuth2UserService.class);}
        @Bean SystemConfigService config(){return mock(SystemConfigService.class);}
        @Bean OAuth2ProxyConfig proxy(SystemConfigService config){return new OAuth2ProxyConfig(config);}
        @Bean ObjectMapper json(){return new ObjectMapper().findAndRegisterModules();}
        @Bean JsonAuthenticationEntryPoint entry(ObjectMapper json){return new JsonAuthenticationEntryPoint();}
        @Bean ActiveUserSessionFilter active(){return new ActiveUserSessionFilter(mock(UserService.class));}
        @Bean ThirdPartyAuthFilter thirdparty(){return new ThirdPartyAuthFilter(mock(ApplicationMapper.class),mock(CryptoRuntimeService.class),mock(ReplayProtectionService.class));}
        @Bean PublicChallengeService challenges(){return mock(PublicChallengeService.class);}
        @Bean Endpoints endpoints(){return new Endpoints();}
        @Bean ClientRegistrationRepository clients(){return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("github")
            .clientId("test-client").clientSecret("test-secret").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").authorizationUri("https://example.test/authorize")
            .tokenUri("https://example.test/token").userInfoUri("https://example.test/user").userNameAttributeName("id").build());}
    }
    @RestController static class Endpoints {
        @PostMapping({"/api/payment/create", "/api/payment/notify"}) String mutate(){return "ok";}
        @GetMapping({"/api/users", "/api/public/challenge/admin"}) String protectedEndpoint(){return "private";}
    }
    @BeforeEach void setup(){
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.register(TestConfig.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(context.getBean(PublicChallengeService.class).issue("register-email","127.0.0.1"))
            .thenReturn(Map.of("captchaId","test-id","image","data:image/png;base64,test"));
    }
    @AfterEach void close(){if(context!=null)context.close();}
    @Test void freshBrowserWithoutCookiesCanLoadRegistrationChallenge()throws Exception{
        mvc.perform(get("/api/public/challenge").param("purpose","register-email")
            .header("User-Agent","Mozilla/5.0 Android QQ/9.0.0 MQQBrowser/6.2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.captchaId").value("test-id"))
            .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
        verify(context.getBean(PublicChallengeService.class)).issue("register-email","127.0.0.1");
    }
    @Test void challengeExceptionDoesNotOpenOtherAnonymousApiPaths()throws Exception{
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/public/challenge/admin")).andExpect(status().isUnauthorized());
    }
    @Test void sessionMutationRequiresTokenFromTheSameSessionButNotifyDoesNot() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payment/create")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("member")))
                .andExpect(status().isForbidden());
        var issued = mvc.perform(get("/api/csrf")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))).andReturn();
        var token = new ObjectMapper().readTree(issued.getResponse().getContentAsString());
        var session = (org.springframework.mock.web.MockHttpSession) issued.getRequest().getSession(false);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payment/create")
                .session(session).header(token.get("headerName").asText(), token.get("token").asText())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("member")))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payment/create")
                .session(new org.springframework.mock.web.MockHttpSession())
                .header(token.get("headerName").asText(), token.get("token").asText())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("member")))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/payment/notify"))
                .andExpect(status().isOk());
    }

}
