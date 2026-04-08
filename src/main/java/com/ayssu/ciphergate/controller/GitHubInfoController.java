package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/github")
@Tag(name = "GitHub信息", description = "GitHub OAuth2用户信息调试接口")
public class GitHubInfoController {

    @GetMapping("/user/basic")
    @Operation(summary = "获取GitHub用户基础信息")
    public Result<Map<String, Object>> getUserBasicInfo(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            log.warn("用户未认证，无法获取基本信息");
            return Result.error("User not authenticated");
        }
        
        log.info("=== 获取用户基本信息 ===");


        Map<String, Object> basicInfo = Map.of(
        );

        log.info("基本信息: {}", basicInfo);
        return Result.success(basicInfo);
    }

    @GetMapping("/user/all")
    @Operation(summary = "获取GitHub用户完整信息")
    public Result<Map<String, Object>> getAllUserInfo(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            log.warn("用户未认证，无法获取完整信息");
            return Result.error("User not authenticated");
        }
        
        log.info("=== 获取用户完整信息 ===");
        Map<String, Object> allInfo = principal.getAttributes();
        log.info("完整信息: {}", allInfo);
        
        return Result.success(allInfo);
    }

    @GetMapping("/user/emails")
    @Operation(summary = "获取GitHub用户邮箱列表")
    public Result<Object> getUserEmails(@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient authorizedClient) {
        if (authorizedClient == null) {
            log.warn("用户未认证，无法获取邮箱信息");
            return Result.error("User not authenticated");
        }
        
        log.info("=== 获取用户邮箱信息 ===");
        log.info("Access Token: {}", authorizedClient.getAccessToken().getTokenValue().substring(0, 10) + "...");
        
        WebClient webClient = WebClient.builder()
            .defaultHeader("Authorization", "Bearer " + authorizedClient.getAccessToken().getTokenValue())
            .build();
        
        try {
            Object emails = webClient.get()
                .uri("https://api.github.com/user/emails")
                .retrieve()
                .bodyToMono(Object.class)
                .block();
            
            log.info("邮箱信息: {}", emails);
            return Result.success(emails);
        } catch (Exception e) {
            log.error("获取邮箱信息失败", e);
            return Result.error("Failed to fetch emails: " + e.getMessage());
        }
    }

    @GetMapping("/user/repos")
    @Operation(summary = "获取GitHub用户仓库列表")
    public Result<Object> getUserRepos(@RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient authorizedClient) {
        if (authorizedClient == null) {
            log.warn("用户未认证，无法获取仓库信息");
            return Result.error("User not authenticated");
        }
        
        log.info("=== 获取用户仓库信息 ===");
        
        WebClient webClient = WebClient.builder()
            .defaultHeader("Authorization", "Bearer " + authorizedClient.getAccessToken().getTokenValue())
            .build();
        
        try {
            Object repos = webClient.get()
                .uri("https://api.github.com/user/repos?per_page=10&sort=updated")
                .retrieve()
                .bodyToMono(Object.class)
                .block();
            
            log.info("仓库信息: {}", repos);
            return Result.success(repos);
        } catch (Exception e) {
            log.error("获取仓库信息失败", e);
            return Result.error("Failed to fetch repositories: " + e.getMessage());
        }
    }
}