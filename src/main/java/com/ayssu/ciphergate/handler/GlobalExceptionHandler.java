package com.ayssu.ciphergate.handler;

import com.ayssu.ciphergate.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：将权限/登录相关异常转换为标准 JSON，并返回正确的 HTTP 状态码。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Result<Void>> handleSecurityException(SecurityException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().trim();
        if (msg.contains("未登录") || msg.contains("未授权")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.unauthorized(msg.isEmpty() ? "未授权" : msg));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.forbidden(msg.isEmpty() ? "权限不足" : msg));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDeniedException(AccessDeniedException e) {
        String msg = e.getMessage() == null ? "权限不足" : e.getMessage();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.forbidden(msg));
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Result<Void>> handleStatus(org.springframework.web.server.ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Result.error(e.getStatusCode().value(),
                e.getReason() == null ? "请求失败" : e.getReason()));
    }

    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<Result<Void>> invalidRequest(Exception e) {
        // Validation messages can contain rejected passwords, OTPs or card numbers.
        return ResponseEntity.badRequest().body(Result.badRequest("请求参数无效，请检查输入"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("未处理异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Result.error(500, "服务器内部错误"));
    }
}

