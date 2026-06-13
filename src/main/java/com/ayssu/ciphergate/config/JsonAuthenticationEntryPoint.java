package com.ayssu.ciphergate.config;

import com.alibaba.fastjson2.JSON;
import com.ayssu.ciphergate.common.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 统一未认证响应：所有请求（包括浏览器页面请求）都返回 JSON 401，
 * 而不是让 Spring Security 重定向到 OAuth2 登录页。
 * 前端根据 401 状态码自行决定跳转逻辑。
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Result<Void> result = Result.unauthorized("未登录或登录已过期，请重新登录");
        response.getWriter().write(JSON.toJSONString(result));
    }
}
