package com.chinacreator.gzcm.common.exception;

import java.io.Serial;

/**
 * 禁止访问异常（无权限）。
 */
public class ForbiddenException extends DataBridgeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_HTTP_STATUS = 403;
    public static final int DEFAULT_ERROR_CODE = 403;

    public ForbiddenException(String message) {
        super(DEFAULT_HTTP_STATUS, DEFAULT_ERROR_CODE, message);
    }

    public static ForbiddenException noPermission(String resource, String action) {
        return new ForbiddenException("无权 " + action + " 资源: " + resource);
    }
}
