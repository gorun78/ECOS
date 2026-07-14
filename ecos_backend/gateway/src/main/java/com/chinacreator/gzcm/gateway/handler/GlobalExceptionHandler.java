package com.chinacreator.gzcm.gateway.handler;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * S6-1.2 全局异常处理器 — 拦截 gateway 模块 Controller 层异常。
 *
 * <p>
 * 主要处理 {@link AccessDeniedException}，将 Spring Security 权限异常
 * 转换为统一的 {@link ApiResponse#forbidden(String)} 响应。
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 权限不足 / 未授权访问 → 403 Forbidden
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ApiResponse.forbidden(ex.getMessage());
    }
}
