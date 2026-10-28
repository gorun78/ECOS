package com.chinacreator.gzcm.engine.cognitive.api;

/**
 * 认知域错误码冻结表（详细设计-05 D.5 / F05-07；{@code code} 与 HTTP 状态一致）。
 *
 * <p>用户裁定 A3：503 原样穿透，禁 200 空 body；DG-6 场景无 Mind → {@code 200 data:[]} 而非 404。</p>
 */
public final class CognitiveErrorCode {

    private CognitiveErrorCode() {}

    public static final String E400_MIND_NOT_ON_SCENARIO = "ECOS-COG-400";   // 400 mind not found on this scenario
    public static final String E400_MIND_NOT_FOUND       = "ECOS-COG-400M"; // 400 mind not found
    public static final String E400_INVALID_PARAM        = "ECOS-COG-400P"; // 400 invalid parameter
    public static final String E400_MODEL_TYPE_UNMAPPED  = "ECOS-COG-400C"; // 400 cognitive_model_type unmapped (REQ-COG-07 规格 1, fail-loud)
    public static final String E404_SCENARIO             = "ECOS-COG-404";  // 404 scenario not found / scenario deleted
    public static final String E404_MODEL_ROW            = "ECOS-COG-404R"; // 404 model registry version row not found (REQ-COG-07)
    public static final String E403_CLEARANCE            = "ECOS-COG-403";  // 403 clearance denied
    public static final String E409_MODEL_IMMUTABLE      = "ECOS-COG-409R"; // 409 published model version immutable (REQ-COG-07 规格 3)
    public static final String E422_GATES_NOT_PASSED     = "ECOS-COG-422R"; // 422 publish gates not all PASS (REQ-COG-07 规格 4)

    public static final String E500_ENGINE_DISABLED      = "ECOS-COG-500";  // 503 cognitive engine not enabled
    public static final String E501_SCHEMA_NOT_READY     = "ECOS-COG-501";  // 503 cognitive schema not ready
    public static final String E502_BALANCE_NOT_OK       = "ECOS-COG-502";  // 503 scenario attribution not balanced
    public static final String E503_NOT_IMPLEMENTED      = "ECOS-COG-503";  // 501 capability not implemented (stub=true)
    public static final String E504_INTERNAL             = "ECOS-COG-504";  // 500 cognitive internal error

    // ── 《认知域与智能域补充详细设计 v1.0》增量错误码（§13.1 统一错误语义；只增不改上方冻结表）──
    public static final String E400_SOURCE_REF_MISSING   = "ECOS-COG-400S"; // 400 Context Pack 来源项缺 ID/版本/时点（SUP-CTX-01）
    public static final String E403_SOURCE_DENIED        = "ECOS-COG-403S"; // 403 权限/密级拒绝（不暴露对象存在性，SUP-CTX-02）
    public static final String E409_STATE_ILLEGAL        = "ECOS-COG-409S"; // 409 资产状态迁移非法（SUP-BP-01 / §8.4 同语义）
    public static final String E409_VERSION_STALE        = "ECOS-COG-409V"; // 409 上下文/蓝图版本过期，须重建（§5.5 版本不匹配）
    public static final String E422_GAPS_BLOCKER_OPEN    = "ECOS-COG-422G"; // 422 BLOCKER 缺口未关闭，禁进 READY_FOR_APPROVAL/发布
    public static final String E422_CONFLICT_UNRESOLVED  = "ECOS-COG-422C"; // 422 语义冲突未解决（禁 LLM 自行裁决为事实）
    public static final String E502_DEPENDENCY_DOWN      = "ECOS-COG-502D"; // 502/503 必需依赖不可用（真实透传，禁伪空集合，SUP-CTX-03）
    public static final String E429_BUDGET_EXCEEDED      = "ECOS-COG-429B"; // 429 预算耗尽（Run 转 PAUSED，禁伪成功）

    // ── §9 W→D 结果评估错误码（POST /api/v1/cognitive/outcomes/evaluate）──
    // 注意：§9.4 七情形命中<b>不</b>走错误码 —— 评估仍 200 落库，status 压 NEEDS_RECONCILE 并回显
    // blockers[]（"评估没做成"是业务结论而非故障，禁包装成 4xx/5xx；DG-6 200 空集与 5xx 严格可分）。
    public static final String E400_OUTCOME_INVALID      = "ECOS-COG-400O"; // 400 评估请求结构性非法（§9.3 必填/引用三要素缺失）
    public static final String E404_OUTCOME              = "ECOS-COG-404O"; // 404 评估记录不存在（§9.2 步骤 10 闭环记录回查）

    // ── 详设-09 v1.2 §十一 H-2b 增量码（口径公式求值 + 预测运行快照读；只增不改上方冻结表）──
    public static final String E400_FORMULA_SYNTAX       = "ECOS-COG-400F"; // 400 公式 DSL V1 语法违例（含非白名单函数，定位到符号行列）
    public static final String E422_FORMULA_EVALUATION   = "ECOS-COG-422F"; // 422 已校验 DSL 求值期语义失败（未知标识符/维度缺失/除零/量纲冲突）
    public static final String E404_FORECAST_RUN         = "ECOS-COG-404F"; // 404 预测运行快照行不存在（data-engine 读通道回业务 404，禁伪装空快照）

    // ── 差距分析 P0-6 / R4 Mind 聚合根（V292 + MindUpdateGate；只增不改上方冻结表，2026-10-27 #141/B3）──
    public static final String E404_MIND_UPDATE          = "ECOS-COG-404U";   // 404 候选更新行不存在/已软删
    public static final String E409_MIND_UPDATE_STATE    = "ECOS-COG-409U";   // 409 候选已置终态（ACCEPTED/REJECTED 不可再受理/驳回）
    public static final String E404_MIND_AXIS_COLUMN     = "ECOS-COG-404A";   // 404 V292 三轴列未落库（诚实的「未迁移」态，禁冒充 DRAFT/PENDING_REVIEW）

    /** 冻结字面量（禁止改写，防前端 i18n 不一致）。 */
    public static final String MSG_MIND_NOT_ON_SCENARIO = "mind not found on this scenario";
    public static final String MSG_MIND_NOT_FOUND       = "mind not found";
    public static final String MSG_INVALID_PARAM        = "invalid parameter";
    public static final String MSG_SCENARIO_NOT_FOUND   = "scenario not found";
    public static final String MSG_SCENARIO_DELETED     = "scenario deleted";
    public static final String MSG_CLEARANCE_DENIED     = "clearance denied";
    public static final String MSG_ENGINE_NOT_ENABLED   = "cognitive engine not enabled";
    public static final String MSG_SCHEMA_NOT_READY     = "cognitive schema not ready";
    public static final String MSG_NOT_BALANCED         = "scenario attribution not balanced";
    public static final String MSG_NOT_IMPLEMENTED      = "capability not implemented";
    public static final String MSG_INTERNAL             = "cognitive internal error";

    /** REQ-COG-07 注册中心冻结字面量（与上方同纪律：禁改写，只可新增）。 */
    public static final String MSG_MODEL_TYPE_UNMAPPED  = "cognitive model type unmapped";
    public static final String MSG_MODEL_ROW_NOT_FOUND  = "cognitive model version not found";
    public static final String MSG_MODEL_IMMUTABLE      = "published model version is immutable";
    public static final String MSG_GATES_NOT_PASSED     = "publish gates not all passed";

    /** 《认知域与智能域补充详细设计 v1.0》增量错误码冻结字面量（与上方同纪律：禁改写，只可新增）。 */
    public static final String MSG_SOURCE_REF_MISSING   = "context source reference missing (id/version/asOfTime)";
    public static final String MSG_SOURCE_DENIED        = "source access denied";
    public static final String MSG_STATE_ILLEGAL        = "illegal asset state transition";
    public static final String MSG_VERSION_STALE        = "context or blueprint version stale, rebuild required";
    public static final String MSG_GAPS_BLOCKER_OPEN    = "blocker gaps must be resolved before approval";
    public static final String MSG_CONFLICT_UNRESOLVED  = "semantic conflict unresolved, manual resolution required";
    public static final String MSG_DEPENDENCY_DOWN      = "required dependency unavailable";
    public static final String MSG_BUDGET_EXCEEDED      = "budget exceeded, run paused";

    /** §9 W→D 结果评估冻结字面量（与上方同纪律：禁改写，只可新增）。 */
    public static final String MSG_OUTCOME_INVALID      = "outcome evaluation request invalid";
    public static final String MSG_OUTCOME_NOT_FOUND    = "outcome evaluation not found";

    /** 详设-09 v1.2 H-2b 增量冻结字面量（同纪律：禁改写，只可新增）。 */
    public static final String MSG_FORMULA_SYNTAX       = "caliber formula dsl syntax violation";
    public static final String MSG_FORMULA_EVALUATION   = "caliber formula evaluation failed";
    public static final String MSG_FORECAST_RUN_NOT_FOUND = "forecast run snapshot not found";

    /** 差距分析 P0-6 / R4 Mind 聚合根增量冻结字面量（同纪律：禁改写，只可新增）。 */
    public static final String MSG_MIND_UPDATE_NOT_FOUND   = "mind candidate update not found";
    public static final String MSG_MIND_UPDATE_FINALIZED   = "mind candidate update already finalized";
    public static final String MSG_MIND_AXIS_COLUMN_NOT_READY = "r4 mind status axes not provisioned";

    /**
     * 差距分析附录A 第 19 条 Decision Basis 只读解析增量冻结码（同纪律：禁改写，只可新增）。
     *
     * <p>404 覆盖两种真实不存在：① 决策行在认知底座不存在；② 调用方引用了 pinned 版本/hash
     * 而该引用无法锚定（认知底座 {@code ecos_cognitive.ecos_decision} 无 version/hash 列，
     * 唯一带 (decision_id, version_no, hash) 三要素的载体 {@code ecos_cognitive.ecos_decision_source_ref}
     * 的 decision_id 按 V211 头注指向控制域回执表，不属认知决策 id 空间）。
     * 时间锚定原则（补充设计 §10.2.9）禁止把无法锚定的引用静默降级成"最新版本"返回，
     * 因此 ② 必须走 404 + {@code data.missing} 显式取证，禁 200 伪锚定。</p>
     */
    public static final String E404_DECISION_BASIS = "ECOS-COG-404D";

    /** 附录A 第 19 条增量冻结字面量（响应 message 只取本常量，禁拼接角色信息）。 */
    public static final String MSG_DECISION_BASIS_NOT_FOUND = "decision basis not found";
}
