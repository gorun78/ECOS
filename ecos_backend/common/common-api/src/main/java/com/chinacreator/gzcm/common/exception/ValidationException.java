package com.chinacreator.gzcm.common.exception;

import java.io.Serial;

/**
 * 参数校验异常。
 * <p>
 * 由 Controller 层或 Service 层在入参校验失败时抛出。
 */
public class ValidationException extends DataBridgeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_HTTP_STATUS = 400;
    public static final int DEFAULT_ERROR_CODE = 400;

    public ValidationException(String message) {
        super(DEFAULT_HTTP_STATUS, DEFAULT_ERROR_CODE, message);
    }

    public ValidationException(String field, String reason) {
        super(DEFAULT_HTTP_STATUS, DEFAULT_ERROR_CODE,
                "字段 '" + field + "' 校验失败: " + reason);
    }
}
