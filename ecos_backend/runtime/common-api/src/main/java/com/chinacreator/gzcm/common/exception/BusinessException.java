package com.chinacreator.gzcm.common.exception;

import java.io.Serial;

/**
 * 业务逻辑异常。
 * <p>
 * 由 Service 层在业务规则校验失败时抛出。HTTP 状态码默认为 400。
 */
public class BusinessException extends DataBridgeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_HTTP_STATUS = 400;
    public static final int DEFAULT_ERROR_CODE = 400;

    public BusinessException(String message) {
        super(DEFAULT_HTTP_STATUS, DEFAULT_ERROR_CODE, message);
    }

    public BusinessException(int errorCode, String message) {
        super(DEFAULT_HTTP_STATUS, errorCode, message);
    }
}
