package com.chinacreator.gzcm.common.exception;

import java.io.Serial;

/**
 * DataBridge 所有异常的统一根类（非受检）。
 * <p>
 * 设计决策：使用 RuntimeException 而非 checked Exception，
 * 避免老工程中 {@code throws Exception} 扩散的问题。
 * 所有子类异常通过 GlobalExceptionHandler 统一转换为 ApiResponse。
 *
 * @see com.chinacreator.gzcm.common.base.ApiResponse
 */
public class DataBridgeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** HTTP 状态码，用于 GlobalExceptionHandler */
    private final int httpStatus;

    /** 业务错误码，映射到 ApiResponse.code */
    private final int errorCode;

    /** 调试上下文（不暴露给前端） */
    private final transient Object debugContext;

    public DataBridgeException(int httpStatus, int errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.debugContext = null;
    }

    public DataBridgeException(int httpStatus, int errorCode, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.debugContext = null;
    }

    public DataBridgeException(int httpStatus, int errorCode, String message,
                               Throwable cause, Object debugContext) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.debugContext = debugContext;
    }

    public int getHttpStatus() { return httpStatus; }
    public int getErrorCode() { return errorCode; }
    public Object getDebugContext() { return debugContext; }
}
