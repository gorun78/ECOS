package com.chinacreator.gzcm.engine.cognitive2.service.context;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;

/**
 * Context Builder 失败语义异常（《认知域与智能域补充详细设计 v1.0》§5.5 失败语义表）。
 *
 * <p>每个失败面映射到唯一冻结错误码 + HTTP 状态（{@link #httpStatus}），
 * 由 {@link ContextBuildErrorAdvice}（impl 内独立 advice，只管本包异常）转
 * {@code ApiResponse} 错误体（code = 冻结码，message = {@code MSG_*} 冻结字面量，
 * traceId 走 MDC 回显 —— 与 cognitive2 既有 {@code CognitiveErrorAdvice} 同纪律，
 * 但本异常族属 context 子域，独立 advice 避免误改既有 advice 的覆盖面）。</p>
 *
 * <p>语义红线（SUP-CTX-03：空集合、404、403、503 语义不混淆，禁以空数据伪装依赖失败）：
 * 依赖缺失=503，权限拒绝=403，版本失配=409，冲突未解=422，超预算=429，
 * 引用缺三要素=400 —— 五类失败各自独立状态码，禁止折叠为 200 + 空 Pack。</p>
 */
public class ContextBuildException extends RuntimeException {

    /**
     * E503_DEPENDENCY_MISSING：必需源/必需能力不可用（引擎未启用/服务不可达 → 真实 503，禁伪空集合）。
     *
     * <p>#146ag 取证：本枚是 COG 族唯一<b>未登记在设计单源错误码表</b>的码位（属主
     * {@link CognitiveErrorCode} 里依赖不可用登记为 {@code E502_DEPENDENCY_DOWN = "ECOS-COG-502D"}），
     * 但它已被前端契约三面绑定——{@code ecos_frontend/src/locales/workspace/{zh-CN,en}.json}
     * 的 {@code workspace.ctx.err.engine} 文案与 {@code cognitiveClient.ts} 的失败语义注释、
     * 以及 {@code ecos-tests/tests/e2e/workspace/cognition-surfaces-live.spec.ts} 的 503 断言。
     * 改值＝跨语言契约变更（须先在设计单源登记该码位或裁决迁到 502D），故本批<b>保留字面量</b>
     * 并在门禁里显式白名单登记为余项，禁以"副本消除"名义顺手改码值。</p>
     */
    public static final String E503_DEPENDENCY_MISSING = "ECOS-COG-503D";
    /** E403_SOURCE_DENIED：权限/密级拒绝（security 裁剪面不可用 = fail-closed 默认 DENY）。 */
    public static final String E403_SOURCE_DENIED     = CognitiveErrorCode.E403_SOURCE_DENIED;
    /** E409_VERSION_STALE：引用版本失配（期望版本 vs 读回版本不一致 ⇒ 拒绝使用旧版本执行）。 */
    public static final String E409_VERSION_STALE     = CognitiveErrorCode.E409_VERSION_STALE;
    /** E422_CONFLICT_UNRESOLVED：语义冲突未解决（禁 LLM 自行裁决为事实）。 */
    public static final String E422_CONFLICT_UNRESOLVED = CognitiveErrorCode.E422_CONFLICT_UNRESOLVED;
    /** E429_BUDGET_EXCEEDED：超 Token/来源预算（显式截断失败态；部分截断走 warnings 不抛）。 */
    public static final String E429_BUDGET_EXCEEDED   = CognitiveErrorCode.E429_BUDGET_EXCEEDED;
    /** E400_SOURCE_REF_MISSING：来源引用缺 ID/版本/时点三要素（SUP-CTX-01）。 */
    public static final String E400_SOURCE_REF_MISSING = CognitiveErrorCode.E400_SOURCE_REF_MISSING;

    public final String errorCode;
    public final int httpStatus;
    /** 缺项定位（依赖名/源 ID/字段名，供运维与 Agent 排障；禁带敏感正文）。 */
    public final String missing;

    public ContextBuildException(String errorCode, String message) {
        this(errorCode, message, null);
    }

    public ContextBuildException(String errorCode, String message, String missing) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpOf(errorCode);
        this.missing = missing;
    }

    static int httpOf(String code) {
        if (code == null) return 500;
        return switch (code) {
            case E400_SOURCE_REF_MISSING  -> 400;
            case E403_SOURCE_DENIED       -> 403;
            case E409_VERSION_STALE       -> 409;
            case E422_CONFLICT_UNRESOLVED -> 422;
            case E429_BUDGET_EXCEEDED     -> 429;
            case E503_DEPENDENCY_MISSING  -> 503;
            default -> 500;
        };
    }

    // ── 冻结 message（与 CognitiveErrorCode.MSG_* 同纪律：禁改写）──────────────

    public static final String MSG_DEPENDENCY_MISSING = "required dependency unavailable";
    public static final String MSG_SOURCE_DENIED      = "source access denied";
    public static final String MSG_VERSION_STALE      = "context or blueprint version stale, rebuild required";
    public static final String MSG_CONFLICT_UNRESOLVED = "semantic conflict unresolved, manual resolution required";
    public static final String MSG_BUDGET_EXCEEDED    = "budget exceeded, context build rejected";
    public static final String MSG_SOURCE_REF_MISSING = "context source reference missing (id/version/asOfTime)";
}
