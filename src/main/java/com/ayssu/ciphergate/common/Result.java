package com.ayssu.ciphergate.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一返回结果（企业级标准）
 *
 * @param <T> 数据类型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)  // 空值不返回
@Schema(description = "CipherGate 统一 API 响应封装")
public class Result<T> {

    /**
     * 状态码
     */
    @Schema(description = "业务状态码；200 表示成功", example = "200")
    private Integer code;

    /**
     * 提示信息
     */
    @Schema(description = "面向调用方的提示信息", example = "操作成功")
    private String message;

    /**
     * 数据
     */
    @Schema(description = "响应数据；无数据或发生错误时省略")
    private T data;

    /**
     * 时间戳（格式：yyyy-MM-dd HH:mm:ss）
     */
    @Schema(description = "服务端响应时间", example = "2026-09-30 12:00:00")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime timestamp;

    /**
     * 是否成功
     */
    @Schema(description = "是否成功；与 code==200 一致", example = "true")
    private Boolean success;

    public Result() {
        this.timestamp = LocalDateTime.now();
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.success = code == 200;
        this.timestamp = LocalDateTime.now();
    }

    // ==================== 成功返回 ====================

    /**
     * 成功（无数据）
     */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    /**
     * 成功（有数据）
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /**
     * 成功（自定义消息）
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    // ==================== 失败返回 ====================

    /**
     * 失败（默认消息）
     */
    public static <T> Result<T> error() {
        return new Result<>(500, "操作失败", null);
    }

    /**
     * 失败（自定义消息）
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    /**
     * 失败（自定义状态码和消息）
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    // ==================== 业务异常 ====================

    /**
     * 参数错误
     */
    public static <T> Result<T> badRequest(String message) {
        return new Result<>(400, message, null);
    }

    /**
     * 未授权
     */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    /**
     * 无权限
     */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(403, message, null);
    }

    /**
     * 资源不存在
     */
    public static <T> Result<T> notFound(String message) {
        return new Result<>(404, message, null);
    }
}