package com.chinacreator.gzcm.gateway.handler;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.DataAccessException;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.exception.UnauthorizedException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * S6-1.2 全局异常处理器 — 网关层统一拦截 {@link DataBridgeException} 体系异常
 * 并将其转换为标准 {@link ApiResponse} 响应。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>满足架构铁律 §1.4：禁止裸 500，所有 Exception 必须有兜底处理器</li>
 *   <li>每个异常类型独立处理器，便于故障定位与日志分级</li>
 *   <li>HTTP 状态码与 ApiResponse.code 保持语义一致</li>
 *   <li>debugContext 属于运维上下文，禁止暴露给外部</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * DataBridge 根异常兜底：未匹配到具体子类时按异常携带的 httpStatus 透出。
     * 该 handler 必须在所有具体子类 handler 之后声明（Spring 按类型精确匹配选择 handler）。
     */
    @ExceptionHandler(DataBridgeException.class)
    public ApiResponse<Void> handleDataBridge(DataBridgeException ex) {
        log.error("DataBridgeException: httpStatus={}, errorCode={}, message={}",
                ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), ex.getCause());
        return ApiResponse.error(ex.getErrorCode(), String.valueOf(ex.getErrorCode()), ex.getMessage());
    }

    /**
     * 业务异常 → 400 业务错误
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBusiness(BusinessException ex) {
        log.warn("BusinessException: {}", ex.getMessage(), ex);
        return ApiResponse.error(ex.getErrorCode(), String.valueOf(ex.getErrorCode()), ex.getMessage());
    }

    /**
     * 参数校验异常 → 400 参数错误
     */
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(ValidationException ex) {
        log.warn("ValidationException: {}", ex.getMessage(), ex);
        return ApiResponse.badRequest(ex.getMessage());
    }

    /**
     * 资源不存在 → 404 Not Found
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(NotFoundException ex) {
        log.info("NotFoundException: {}", ex.getMessage());
        return ApiResponse.notFound(ex.getMessage());
    }

    /**
     * 未认证（Token 缺失/无效/过期）→ 401 Unauthorized
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorized(UnauthorizedException ex) {
        log.warn("UnauthorizedException: {}", ex.getMessage(), ex);
        return ApiResponse.unauthorized(ex.getMessage());
    }

    /**
     * 业务侧权限不足 → 403 Forbidden
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbidden(ForbiddenException ex) {
        log.warn("ForbiddenException: {}", ex.getMessage(), ex);
        return ApiResponse.forbidden(ex.getMessage());
    }

    /**
     * 数据层访问异常（DB/Neo4j/MinIO 等）→ 跟随异常携带的 httpStatus，默认 500
     */
    @ExceptionHandler(DataAccessException.class)
    public ApiResponse<Void> handleDataAccess(DataAccessException ex) {
        log.error("DataAccessException: httpStatus={}, message={}", ex.getHttpStatus(), ex.getMessage(), ex);
        return ApiResponse.error(ex.getErrorCode(), String.valueOf(ex.getErrorCode()), "系统繁忙，请稍后重试");
    }

    /**
     * Spring Security 权限不足（未走 DataBridge 体系的安全链抛出的原生异常）→ 403
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDenied(AccessDeniedException ex) {
        log.warn("AccessDeniedException: {}", ex.getMessage());
        return ApiResponse.forbidden(ex.getMessage() != null ? ex.getMessage() : "无访问权限");
    }

    /**
     * 兜底：所有未识别的 Exception → 500，禁止裸抛导致 HTML 错误页。
     * 仅暴露"系统繁忙"提示，不暴露具体堆栈/异常类型给外部。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ApiResponse<Void> handleAny(Exception ex) {
        log.error("Unhandled exception", ex);
        return ApiResponse.internalError("系统繁忙，请稍后重试");
    }
}
