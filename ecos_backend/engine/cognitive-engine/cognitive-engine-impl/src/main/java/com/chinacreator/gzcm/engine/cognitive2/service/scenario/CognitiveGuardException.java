package com.chinacreator.gzcm.engine.cognitive2.service.scenario;

/**
 * 认知域前置门异常（设计-05 C.1）— 由 {@code CognitiveErrorAdvice} 拦下 → 具体 HTTP 码与冻结 message。
 *
 * <p>禁 fail-open：message 采用 {@link com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode}
 * 冻结字面量，角色 {@code e.getMessage()} 泄漏进响应。</p>
 */
public class CognitiveGuardException extends RuntimeException {

    public final String errorCode;
    public final int httpStatus;
    /** D.3 统一错误响应体的 {@code data.missing}（可空；表名 / 依赖能力标识，供运维定位）。 */
    public final String missing;

    public CognitiveGuardException(String errorCode, String message) {
        this(errorCode, message, null);
    }

    public CognitiveGuardException(String errorCode, String message, String missing) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpOf(errorCode);
        this.missing = missing;
    }

    static int httpOf(String code) {
        if (code == null) return 500;
        return switch (code) {
            case "ECOS-COG-400", "ECOS-COG-400M", "ECOS-COG-400P", "ECOS-COG-400C" -> 400;
            case "ECOS-COG-404", "ECOS-COG-404R" -> 404;
            case "ECOS-COG-403" -> 403;
            case "ECOS-COG-409R", "ECOS-COG-409S", "ECOS-COG-409V" -> 409;   // 已发布版本行不可变 / 状态迁移非法 / 版本过期
            case "ECOS-COG-422R" -> 422;   // REQ-COG-07 规格 4：四类门禁任一非 PASS ⇒ 显式 4xx
            case "ECOS-COG-500", "ECOS-COG-501", "ECOS-COG-502" -> 503;
            case "ECOS-COG-503" -> 501;
            default -> 500;
        };
    }
}
