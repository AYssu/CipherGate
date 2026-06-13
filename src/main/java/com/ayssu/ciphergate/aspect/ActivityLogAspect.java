package com.ayssu.ciphergate.aspect;

import com.ayssu.ciphergate.util.AuthUtils;
import com.ayssu.ciphergate.annotation.ActivityLog;
import com.ayssu.ciphergate.entity.User;
import com.ayssu.ciphergate.service.ActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 活动日志切面
 * 自动拦截带有 @ActivityLog 注解的方法并记录日志
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ActivityLogAspect {
    
    private final ActivityLogService activityLogService;
    
    @Around("@annotation(activityLog)")
    public Object logActivity(ProceedingJoinPoint joinPoint, ActivityLog activityLog) throws Throwable {
        // 获取请求信息
        HttpServletRequest request = getHttpServletRequest();
        String ipAddress = getIpAddress(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : "";
        
        // 获取当前用户信息
        Authentication authentication = AuthUtils.getAuthentication();
        Long userId = null;
        String username = "匿名用户";
        
        if (authentication != null && authentication.isAuthenticated()) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof User user) {
                userId = user.getId();
                username = user.getName();
            } else if (principal instanceof OAuth2User) {
                OAuth2User oauth2User = (OAuth2User) principal;
                username = oauth2User.getAttribute("login");
            }
        }
        
        String status = "SUCCESS";
        try {
            // 执行目标方法
            Object result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            status = "FAILED";
            throw e;
        } finally {
            // 记录日志（异步执行，不影响主流程）
            try {
                activityLogService.log(
                    userId,
                    username,
                    activityLog.actionType(),
                    activityLog.actionTarget(),
                    activityLog.description(),
                    ipAddress,
                    userAgent,
                    status
                );
            } catch (Exception e) {
                log.error("记录活动日志失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 获取 HttpServletRequest
     */
    private HttpServletRequest getHttpServletRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        // 处理多个IP的情况，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }
}
