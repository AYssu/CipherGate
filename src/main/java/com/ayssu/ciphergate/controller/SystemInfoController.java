package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.util.AuthUtils;
import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.UserService;
import com.ayssu.ciphergate.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Tag(name = "系统信息", description = "系统运行状态与环境信息接口")
public class SystemInfoController {

    private final UserService userService;
    private final SecurityUtils securityUtils;

    private User getCurrentUser() {
        User user = AuthUtils.getCurrentUser();
        if (user != null) return user;

        Authentication authentication = AuthUtils.getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            String githubId = oauth2User.getAttribute("id").toString();
            user = userService.getUserByGithubId(githubId);
            if (user != null) return user;
        }

        throw new SecurityException("用户未登录");
    }

    private void requireSuperAdmin() {
        User user = getCurrentUser();
        if (!securityUtils.isSuperAdmin(user.getId())) {
            throw new SecurityException("仅超级管理员可操作");
        }
    }

    @GetMapping("/info")
    @RequirePermission("CONFIG_LIST")
    @Operation(summary = "获取系统信息")
    public Result<Map<String, Object>> getSystemInfo() {
        try {
            requireSuperAdmin();
            Map<String, Object> systemInfo = new HashMap<>();
            
            // 获取系统基本信息
            Properties props = System.getProperties();
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            
            // 应用信息
            Map<String, Object> appInfo = new HashMap<>();
            appInfo.put("name", "CipherGate");
            appInfo.put("version", "1.0.0");
            appInfo.put("startTime", runtimeBean.getStartTime());
            appInfo.put("uptime", runtimeBean.getUptime());
            systemInfo.put("application", appInfo);
            
            // 系统信息
            Map<String, Object> osInfo = new HashMap<>();
            osInfo.put("name", props.getProperty("os.name"));
            osInfo.put("version", props.getProperty("os.version"));
            osInfo.put("arch", props.getProperty("os.arch"));
            osInfo.put("processors", osBean.getAvailableProcessors());
            systemInfo.put("operatingSystem", osInfo);
            
            // Java信息
            Map<String, Object> javaInfo = new HashMap<>();
            javaInfo.put("version", props.getProperty("java.version"));
            javaInfo.put("vendor", props.getProperty("java.vendor"));
            javaInfo.put("home", props.getProperty("java.home"));
            systemInfo.put("java", javaInfo);
            
            // 内存信息
            Map<String, Object> memoryInfo = new HashMap<>();
            long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            long freeMemory = maxMemory - usedMemory;
            
            memoryInfo.put("max", maxMemory);
            memoryInfo.put("used", usedMemory);
            memoryInfo.put("free", freeMemory);
            memoryInfo.put("usagePercent", Math.round((double) usedMemory / maxMemory * 100));
            systemInfo.put("memory", memoryInfo);
            
            // 技术栈信息
            Map<String, Object> techStack = new HashMap<>();
            techStack.put("backend", "Spring Boot " + getSpringBootVersion());
            techStack.put("frontend", "React + TypeScript");
            techStack.put("database", "MySQL");
            techStack.put("authentication", "OAuth2 + GitHub");
            systemInfo.put("techStack", techStack);
            
            return Result.success(systemInfo);
            
        } catch (Exception e) {
            log.error("获取系统信息失败: {}", e.getMessage());
            return Result.error("获取系统信息失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/status")
    @RequirePermission("CONFIG_LIST")
    @Operation(summary = "获取系统运行状态")
    public Result<Map<String, Object>> getSystemStatus() {
        try {
            requireSuperAdmin();
            Map<String, Object> status = new HashMap<>();
            
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            
            // 系统负载（简化版本，实际可能需要更复杂的计算）
            double systemLoad = osBean.getSystemLoadAverage();
            if (systemLoad < 0) {
                systemLoad = 0.12; // 默认值，当系统不支持负载平均值时
            }
            
            // 内存使用率
            long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
            long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
            double memoryUsage = (double) usedMemory / maxMemory * 100;
            
            // 运行时间
            long uptime = runtimeBean.getUptime();
            
            status.put("systemLoad", Math.round(systemLoad * 100));
            status.put("memoryUsage", Math.round(memoryUsage));
            status.put("uptime", uptime);
            status.put("status", "正常运行");
            status.put("processors", osBean.getAvailableProcessors());
            
            return Result.success(status);
            
        } catch (Exception e) {
            log.error("获取系统状态失败: {}", e.getMessage());
            return Result.error("获取系统状态失败: " + e.getMessage());
        }
    }
    
    private String getSpringBootVersion() {
        try {
            Package pkg = org.springframework.boot.SpringBootVersion.class.getPackage();
            return pkg.getImplementationVersion() != null ? pkg.getImplementationVersion() : "3.x";
        } catch (Exception e) {
            return "3.x";
        }
    }
}
