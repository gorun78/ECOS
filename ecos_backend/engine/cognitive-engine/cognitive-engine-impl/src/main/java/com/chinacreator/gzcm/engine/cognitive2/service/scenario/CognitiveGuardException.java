package com.chinacreator.gzcm.engine.cognitive2.service.scenario;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;

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
     *
     * <p>#146ag：case 标签一律取 {@link CognitiveErrorCode} 常量（{@code public static final String}
     * 字面量初始化＝编译期常量，合法作 case 标签）。本方法此前是 COG 族最大的字面量副本面，
     * 属主改值时副本不报错、只在运行时静默走 {@code default} ⇒ 状态行漂移；单源后码值只有属主一处。</p>
     */
    static int httpOf(String code) {
        if (code == null) return 500;
        return switch (code) {
            case CognitiveErrorCode.E400_MIND_NOT_ON_SCENARIO, CognitiveErrorCode.E400_MIND_NOT_FOUND,
                 CognitiveErrorCode.E400_INVALID_PARAM, CognitiveErrorCode.E400_MODEL_TYPE_UNMAPPED,
                 CognitiveErrorCode.E400_SOURCE_REF_MISSING, CognitiveErrorCode.E400_OUTCOME_INVALID,
                 CognitiveErrorCode.E400_FORMULA_SYNTAX -> 400;   // FORMULA_SYNTAX = 公式 DSL V1 语法（详设-09 H-2b）
            case CognitiveErrorCode.E404_SCENARIO, CognitiveErrorCode.E404_MODEL_ROW,
                 CognitiveErrorCode.E404_OUTCOME, CognitiveErrorCode.E404_FORECAST_RUN,
                 CognitiveErrorCode.E404_MIND_UPDATE, CognitiveErrorCode.E404_MIND_AXIS_COLUMN,
                 CognitiveErrorCode.E404_DECISION_BASIS -> 404;    // MIND_UPDATE/MIND_AXIS_COLUMN = R4 候选行不存在 / 三轴列未落库；DECISION_BASIS = 附录A 第 19 条决策依据解析不存在（含 pinned 引用无法锚定）
            case CognitiveErrorCode.E403_CLEARANCE, CognitiveErrorCode.E403_SOURCE_DENIED -> 403;
            case CognitiveErrorCode.E409_MODEL_IMMUTABLE, CognitiveErrorCode.E409_STATE_ILLEGAL,
                 CognitiveErrorCode.E409_VERSION_STALE, CognitiveErrorCode.E409_MIND_UPDATE_STATE -> 409; // + MIND_UPDATE_STATE = R4 候选已置终态
            case CognitiveErrorCode.E422_GATES_NOT_PASSED, CognitiveErrorCode.E422_GAPS_BLOCKER_OPEN,
                 CognitiveErrorCode.E422_CONFLICT_UNRESOLVED, CognitiveErrorCode.E422_FORMULA_EVALUATION -> 422; // FORMULA_EVALUATION = 公式求值期语义失败
            case CognitiveErrorCode.E429_BUDGET_EXCEEDED -> 429;   // 预算耗尽（Run 转 PAUSED）
            case CognitiveErrorCode.E500_ENGINE_DISABLED, CognitiveErrorCode.E501_SCHEMA_NOT_READY,
                 CognitiveErrorCode.E502_BALANCE_NOT_OK, CognitiveErrorCode.E502_DEPENDENCY_DOWN -> 503;
            case CognitiveErrorCode.E503_NOT_IMPLEMENTED -> 501;
            default -> 500;
        };
    }
}
