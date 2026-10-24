package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.service.*;
import com.ayssu.ciphergate.dto.*;
import com.ayssu.ciphergate.handler.GlobalExceptionHandler;
import com.ayssu.ciphergate.thirdparty.service.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppUserLicenseRechargeControllerTest {
    AppUserLicenseRechargeService service; ThirdPartyUserRateLimitService rate; MockMvc mvc;
    String url="/api/public/app-user/self/redeem-license";
    String body="{\"appId\":10,\"email\":\"alice@example.test\",\"keyCode\":\"SECRET-CARD\"}";
    @BeforeEach void setup() {
        service=mock(AppUserLicenseRechargeService.class);rate=mock(ThirdPartyUserRateLimitService.class);
        mvc=MockMvcBuilders.standaloneSetup(new PublicAppUserSelfController(mock(PublicAppUserSelfService.class),service,rate))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void anonymousHolderCanRedeemAndOnlyReceivesMaskedIdentifiers() throws Exception {
        when(service.redeem(any(),any())).thenReturn(new AppUserLicenseRechargeResponse("al***@example.test","SE****",null,LocalDateTime.of(2035,1,1,0,0),100,true));
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.emailMasked").value("al***@example.test"))
            .andExpect(jsonPath("$.data.keyCode").doesNotExist());
        verify(rate).checkBeforeRedemption(eq(10L),any(),eq("alice@example.test"));
    }
    @Test void invalidBodyNeverReachesDatabaseOrLimiter() throws Exception {
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{\"appId\":0,\"email\":\"invalid\",\"keyCode\":\"\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service,rate);
    }
    @Test void rateLimitAndInfrastructureFailuresNeverCreditOrExposeSecrets() throws Exception {
        doThrow(new ThirdPartyUserLoginException(429,"请求过于频繁")).when(rate).checkBeforeRedemption(any(),any(),any());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(jsonPath("$.code").value(429));
        verifyNoInteractions(service);
        reset(rate);when(service.redeem(any(),any())).thenThrow(new IllegalStateException("SQL SECRET-CARD alice@example.test"));
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(jsonPath("$.message").value("充值服务暂不可用，请稍后重试")).andExpect(jsonPath("$.data").doesNotExist());
    }
}
