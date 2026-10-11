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

    /**
     * 错误码 → HTTP 状态。
     *
     * <p>补充设计 v1.0 增量码（400S/403S/409S/409V/422G/422C/502D/429B）与 §9 结果评估码
     * （400O/404O）此前全部落到 {@code default -> 500}，即"403 拒绝被判成 500 内部错误"，
     * 违反 §13.1 统一错误语义与 DG-6（200/4xx/5xx 严格可分）。本 switch 补齐全部已声明码，
     * {@code default} 只留给真正未登记的码 —— 未登记即 500 fail-loud，禁静默降级。</p>
     */
    static int httpOf(String code) {
        if (code == null) return 500;
        return switch (code) {
            case "ECOS-COG-400", "ECOS-COG-400M", "ECOS-COG-400P", "ECOS-COG-400C",
                 "ECOS-COG-400S", "ECOS-COG-400O", "ECOS-COG-400F" -> 400;   // 400F = 公式 DSL V1 语法（详设-09 H-2b）
            case "ECOS-COG-404", "ECOS-COG-404R", "ECOS-COG-404O", "ECOS-COG-404F" -> 404;  // 404F = 预测运行快照不存在
            case "ECOS-COG-403", "ECOS-COG-403S" -> 403;
            case "ECOS-COG-409R", "ECOS-COG-409S", "ECOS-COG-409V" -> 409;   // 已发布版本行不可变 / 状态迁移非法 / 版本过期
            case "ECOS-COG-422R", "ECOS-COG-422G", "ECOS-COG-422C", "ECOS-COG-422F" -> 422; // 422F = 公式求值期语义失败
            case "ECOS-COG-429B" -> 429;                                     // 预算耗尽（Run 转 PAUSED）
            case "ECOS-COG-500", "ECOS-COG-501", "ECOS-COG-502", "ECOS-COG-502D" -> 503;
            case "ECOS-COG-503" -> 501;
            default -> 500;
        };
    }
}
