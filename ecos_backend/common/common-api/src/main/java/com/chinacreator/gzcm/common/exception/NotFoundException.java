package com.chinacreator.gzcm.common.exception;

import java.io.Serial;

/**
 * 资源不存在异常。
 */
public class NotFoundException extends DataBridgeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_HTTP_STATUS = 404;
    public static final int DEFAULT_ERROR_CODE = 404;

    public NotFoundException(String message) {
        super(DEFAULT_HTTP_STATUS, DEFAULT_ERROR_CODE, message);
    }

    public static NotFoundException entity(String entity, String id) {
        return new NotFoundException(entity + " 不存在: id=" + id);
    }
}
