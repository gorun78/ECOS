package com.chinacreator.gzcm.workspace.exception;

/**
 * 跨服务依赖不可达（选项池联查 / 认知透传以外的外部 service 调用失败）（C-5 / 铁律 §2.4-6 默认 DENY）。
 * <p>HTTP 503，错误码 EXTERNAL_SERVICE_UNAVAILABLE。A3 红线：禁 HTTP 200 + body.code=503 伪装（X-17）。</p>
 */
public class ExternalServiceUnavailableException extends WorkspaceException {

    public static final String CODE = "EXTERNAL_SERVICE_UNAVAILABLE";

    public ExternalServiceUnavailableException(String serviceName, String reason) {
        super(503, -503, CODE, serviceName + " unavailable: " + reason);
    }

    public ExternalServiceUnavailableException(String serviceName, String reason, Throwable cause) {
        super(503, -503, CODE, serviceName + " unavailable: " + reason, cause);
    }
}
