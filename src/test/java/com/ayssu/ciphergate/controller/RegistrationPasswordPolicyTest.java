package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.handler.GlobalExceptionHandler;
import com.ayssu.ciphergate.service.*;
import com.ayssu.ciphergate.util.PasswordPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationPasswordPolicyTest {
    private MockMvc mvc;
    private AppUserRegisterVerificationService service;

    @BeforeEach void setup() {
        service = mock(AppUserRegisterVerificationService.class);
        doAnswer(invocation -> { PasswordPolicy.validate(invocation.getArgument(4)); return null; })
            .when(service).completeSelfRegister(anyLong(), anyString(), anyString(), anyString(), nullable(String.class));
        mvc = MockMvcBuilders.standaloneSetup(new PublicAppUserRegisterController(
                service, mock(PublicChallengeService.class), mock(PublicAbuseGuard.class)))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "abcdef", "abcdefg", "密密密密密密", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void registrationAcceptsSharedPolicyWithoutLegacyComplexityRule(String password) throws Exception {
        submit(password).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).completeSelfRegister(1L, "3636672835", "test@example.com", "123456", password);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "        ", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "密密密密密密密密密密密密密密密密密密密密密密密密密"})
    void rejectedPasswordsHaveActionableSafeMessage(String password) throws Exception {
        submit(password).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("密码长度须为6-50位，UTF-8编码不能超过72字节"));
    }

    private org.springframework.test.web.servlet.ResultActions submit(String password) throws Exception {
        return mvc.perform(post("/api/public/app-user/register/submit").contentType(MediaType.APPLICATION_JSON)
            .content("{\"appId\":1,\"username\":\"3636672835\",\"email\":\"test@example.com\",\"emailCode\":\"123456\",\"password\":\"" + password + "\"}"));
    }
}
