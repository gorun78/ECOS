package com.chinacreator.gzcm.common.exception;

import java.io.Serial;

/**
 * 未认证异常（未登录或 Token 失效）。
 */
public class UnauthorizedException extends DataBridgeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_HTTP_STATUS = 401;
    public static final int DEFAULT_ERROR_CODE = 401;

    public UnauthorizedException(String message) {
        super(DEFAULT_HTTP_STATUS, DEFAULT_ERROR_CODE, message);
    }

    public static UnauthorizedException tokenExpired() {
        return new UnauthorizedException("Token 已过期，请重新登录");
    }

    public static UnauthorizedException invalidToken() {
        return new UnauthorizedException("Token 无效");
    }
}
