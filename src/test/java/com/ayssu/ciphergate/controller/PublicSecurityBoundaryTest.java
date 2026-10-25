package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.handler.GlobalExceptionHandler;
import com.ayssu.ciphergate.service.*;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicSecurityBoundaryTest {
    PublicChallengeService challenges; PublicLicenseSelfService licenses; MockMvc mvc;
    @BeforeEach void setup() {
        challenges=mock(PublicChallengeService.class); licenses=mock(PublicLicenseSelfService.class);
        mvc=MockMvcBuilders.standaloneSetup(new PublicLicenseSelfServiceController(licenses,challenges))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @Test void missingChallengeIsRejectedBeforeBusinessCodeWithoutRejectedSecretInResponse() throws Exception {
        mvc.perform(post("/api/public/license/unbind").contentType(MediaType.APPLICATION_JSON)
            .content("{\"appId\":1,\"keyCode\":\"SECRET-CARD\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("请求参数无效，请检查输入"));
        verifyNoInteractions(challenges,licenses);
    }
    @Test void exhaustedChallengeQuotaPreserves429AndNeverQueriesCard() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"请求过于频繁"))
            .when(challenges).verify(anyString(),anyString(),anyString(),any());
        mvc.perform(post("/api/public/license/query-remaining").contentType(MediaType.APPLICATION_JSON)
            .content("{\"appId\":1,\"keyCode\":\"SECRET-CARD\",\"captchaId\":\"id\",\"captchaCode\":\"12345\"}"))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value(429));
        verifyNoInteractions(licenses);
    }
}
