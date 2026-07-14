package com.chinacreator.gzcm.sysman.boot.handler;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.exception.ForbiddenException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/**
 * 全局异常处理器 — 统一拦截 Controller 层异常并返回标准 {@link ApiResponse}。
 *
 * <pre>
 *   IllegalArgumentException              → 400 Bad Request
 *   MethodArgumentNotValidException       → 400 Bad Request
 *   MissingServletRequestParameterException → 400 Bad Request
 *   HttpMessageNotReadableException       → 400 Bad Request
 *   UnauthorizedException                 → 401 Unauthorized
 *   ForbiddenException                    → 403 Forbidden
 *   NotFoundException                     → 404 Not Found
 *   NoSuchElementException               → 404 Not Found
 *   HttpRequestMethodNotSupportedException → 405 Method Not Allowed
 *   DataIntegrityViolationException       → 409 Conflict
 *   DataBridgeException (通用)            → 使用异常内置 httpStatus
 *   Exception                             → 500 Internal Server Error
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── 400 Bad Request ──────────────────────────────

    /**
     * 参数校验 / 非法参数 → 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ApiResponse.badRequest(ex.getMessage());
    }

    /**
     * @Valid 校验失败 → 400 Bad Request
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("Validation failed: {}", msg);
        return ApiResponse.badRequest(msg);
    }

    /**
     * 缺少必填请求参数 → 400 Bad Request
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Missing required parameter: {}", ex.getParameterName());
        return ApiResponse.badRequest("缺少必填参数: " + ex.getParameterName());
    }

    /**
     * 请求体无法解析（JSON 格式错误等）→ 400 Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Request body not readable: {}", ex.getMessage());
        return ApiResponse.badRequest("请求体格式错误，请检查 JSON 格式");
    }

    // ── 401 Unauthorized ─────────────────────────────

    /**
     * 未认证（未登录或 Token 失效）→ 401 Unauthorized
     */
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> handleUnauthorized(UnauthorizedException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return ApiResponse.unauthorized(ex.getMessage());
    }

    // ── 403 Forbidden ────────────────────────────────

    /**
     * 无权限 → 403 Forbidden
     */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleForbidden(ForbiddenException ex) {
        log.warn("Forbidden: {}", ex.getMessage());
        return ApiResponse.forbidden(ex.getMessage());
    }

    // ── 404 Not Found ────────────────────────────────

    /**
     * 资源不存在 → 404 Not Found
     */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(NotFoundException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return ApiResponse.notFound(ex.getMessage());
    }

    /**
     * 资源不存在 → 404 Not Found
     */
    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoSuchElement(NoSuchElementException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ApiResponse.notFound(ex.getMessage());
    }

    // ── 405 Method Not Allowed ───────────────────────

    /**
     * HTTP 方法不支持 → 405 Method Not Allowed
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not allowed: {}", ex.getMessage());
        return ApiResponse.error(405, "请求方法不允许: " + ex.getMethod());
    }

    // ── 409 Conflict ─────────────────────────────────

    /**
     * 数据完整性冲突（唯一约束、外键等）→ 409 Conflict
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return ApiResponse.error(409, "数据冲突，可能已存在相同记录");
    }

    // ── DataBridgeException 兜底 ─────────────────────

    /**
     * DataBridge 领域异常通用处理（使用异常内置的 httpStatus 和 errorCode）
     */
    @ExceptionHandler(DataBridgeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleDataBridgeException(DataBridgeException ex) {
        log.warn("DataBridge exception (httpStatus={}, errorCode={}): {}",
                ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage());
        return ApiResponse.error(ex.getErrorCode(), ex.getMessage());
    }

    // ── 500 Internal Server Error ────────────────────

    /**
     * 未捕获的系统异常 → 500 Internal Server Error
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ApiResponse.internalError("服务器内部错误: " + ex.getMessage());
    }
}
