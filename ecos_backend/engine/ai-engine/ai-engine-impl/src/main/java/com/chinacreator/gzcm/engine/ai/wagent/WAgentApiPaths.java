package com.chinacreator.gzcm.engine.ai.wagent;

/**
 * W Agent 编排面 API 路径单源（详细设计-10 §5.2 首批 24 端点 / 25 个 operationId 落位，REST + SSE）。
 *
 * <p>文档口径包名 {@code com.chinacreator.gzcm.ai.wagent} 与本包
 * {@code com.chinacreator.gzcm.engine.ai.wagent} 属同义（F10-01）。</p>
 *
 * <p><b>前缀策略（§5.1，只增不改）</b>：W 域新面一律挂 {@link #BASE_PREFIX}
 * = {@code /api/v1/wagent}，<b>不复用</b>已混乱的 {@code /api/v1/agent} 族（X-2/W252/R-43）；
 * 三滤波器双路径口径：{@code /api/v1/wagent} 与 {@code /api/wagent}（legacy 反前缀形态，
 * 经 {@code VersionPrefixRewriteFilter} REVERSE 映射收敛到 v1 正典，见 {@link #LEGACY_PREFIX}）。
 * 禁引入 {@code /api/v1/cognition} 前缀（X-18 → R-43，认知模型属主 cognitive-engine，
 * 见 {@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog}）。</p>
 *
 * <p>OpenAPI 制品 {@code docs/30-设计/openapi/wagent-v1.yaml}（R2-C/#133）与本类同源，
 * operationId 对齐 §5.2 表；wagent 端点全部要求 JWT 认证，<b>禁</b>进入
 * {@code AnonymousEndpointRegistry.permitAllPatterns()} 匿名面。</p>
 */
public final class WAgentApiPaths {

    private WAgentApiPaths() {}

    /** 版本化正典前缀（§5.2 全表基准）。 */
    public static final String BASE_PREFIX = "/api/v1/wagent";

    /** 双路径之去 v1 形态（仅供网关反向重写映射登记，Controller 禁直接映射此裸前缀新增功能）。 */
    public static final String LEGACY_PREFIX = "/api/wagent";

    // ── 路径参数名单源（OpenAPI 片段与 @PathVariable 同字面量）──────────────
    public static final String P_GOAL_ID = "goalId";
    public static final String P_QUESTION_ID = "questionId";
    public static final String P_RUN_ID = "runId";
    public static final String P_NAME = "name";
    public static final String P_CANDIDATE_ID = "candidateId";
    public static final String P_ACTION_ID = "actionId";
    public static final String P_EVIDENCE_ID = "evidenceId";

    // ── §5.2 #1~#4 Goal / Question ────────────────────────────────────
    /** #1 POST 201；幂等键头可选（createGoal）。 */
    public static final String GOALS_COLLECTION = BASE_PREFIX + "/goals";
    /** #2 PATCH；口径变更⇒新版本；被引用则 409 ECOS_WA_CALIBER_LOCKED（reviseGoal）。 */
    public static final String GOAL_REVISE = BASE_PREFIX + "/goals/{" + P_GOAL_ID + "}";

    /** #3 POST；意图识别 + 澄清返回 CLARIFY（createQuestion）。 */
    public static final String QUESTIONS_COLLECTION = BASE_PREFIX + "/questions";
    /** #4 GET；含 latest_run 摘要（getQuestion）。 */
    public static final String QUESTION_BY_ID = BASE_PREFIX + "/questions/{" + P_QUESTION_ID + "}";

    // ── §5.2 #5~#10 Run ───────────────────────────────────────────────
    /** #5 POST；Prefer: respond-async 支持 202 + run_id（startRun）。 */
    public static final String RUNS_COLLECTION = BASE_PREFIX + "/runs";
    /** #6 GET；状态 + 时间线（getRun）。 */
    public static final String RUN_BY_ID = BASE_PREFIX + "/runs/{" + P_RUN_ID + "}";
    /** #7 GET SSE；Last-Event-ID 续传，依赖 P-4 就绪（streamRunEvents，F10-16）。 */
    public static final String RUN_EVENTS_SSE = BASE_PREFIX + "/runs/{" + P_RUN_ID + "}/events";
    /** #8 POST；awaiting_input 应答（submitRunInput）。 */
    public static final String RUN_INPUT = BASE_PREFIX + "/runs/{" + P_RUN_ID + "}/input";
    /** #9 POST；awaiting_approval + 凭证校验（approveRunGate）。 */
    public static final String RUN_APPROVAL = BASE_PREFIX + "/runs/{" + P_RUN_ID + "}/approval";
    /** #10 POST；幂等取消（cancelRun）。 */
    public static final String RUN_CANCEL = BASE_PREFIX + "/runs/{" + P_RUN_ID + "}/cancel";

    // ── §5.2 #11 Kill Switch ──────────────────────────────────────────
    /** #11 POST；租户/系统级；仅 admin 角色 + 审计（setKillSwitch，F10-21）。 */
    public static final String FLAGS_KILL_SWITCH = BASE_PREFIX + "/flags/kill-switch";

    // ── §5.2 #12~#13 Readiness ────────────────────────────────────────
    /** #12 GET；等级 + 缺项 + 缺口（getReadiness，F10-08/09）。 */
    public static final String READINESS_BY_QUESTION = BASE_PREFIX + "/readiness/{" + P_QUESTION_ID + "}";
    /** #13 POST；补齐循环 ≤3（refillReadiness）。 */
    public static final String READINESS_REFILL = READINESS_BY_QUESTION + "/refill";

    // ── §5.2 #14~#16 Tool Contract ────────────────────────────────────
    /** #14 GET；权限过滤后返回（searchTools，渐进披露入口 F10-18）。 */
    public static final String TOOLS_COLLECTION = BASE_PREFIX + "/tools";
    /** #15 GET；完整契约（describeTool）。 */
    public static final String TOOL_BY_NAME = BASE_PREFIX + "/tools/{" + P_NAME + "}";
    /** #16 POST；契约测试五关（validateToolContract，F10-17）。 */
    public static final String TOOL_VALIDATE = TOOL_BY_NAME + "/validate";

    // ── §5.2 #17~#20 Candidate ────────────────────────────────────────
    /** #17 GET；筛选：类型/状态/批次（listCandidates）。 */
    public static final String CANDIDATES_COLLECTION = BASE_PREFIX + "/candidates";
    /** #18 GET；含 basis/五段校验（getCandidate）。 */
    public static final String CANDIDATE_BY_ID = BASE_PREFIX + "/candidates/{" + P_CANDIDATE_ID + "}";
    /** #19 POST；四眼；语义/口径/知识禁批量（reviewCandidate）。 */
    public static final String CANDIDATE_REVIEW = CANDIDATE_BY_ID + "/review";
    /** #20 POST；approval_token 必填；pre-commit 裁决 + Git 归档（publishCandidate，F10-23）。 */
    public static final String CANDIDATE_PUBLISH = CANDIDATE_BY_ID + "/publish";

    // ── §5.2 #21~#22 Evidence / Claim ─────────────────────────────────
    /** #21 GET；Claim + Evidence + 等级（listClaims，F10-24）。 */
    public static final String CLAIMS_BY_RUN = BASE_PREFIX + "/claims/{" + P_RUN_ID + "}";
    /** #22 GET；引擎端追溯（traceEvidence）。 */
    public static final String EVIDENCE_BY_ID = BASE_PREFIX + "/evidence/{" + P_EVIDENCE_ID + "}";

    // ── §5.2 #23~#24 Decision / Action ────────────────────────────────
    /** #23 POST；引用 pinned 评估记录（recordDecision）。 */
    public static final String DECISIONS_COLLECTION = BASE_PREFIX + "/decisions";
    /** #24a POST；行动草稿（draftAction，属主 ai-engine，REQ-WAG-02/R-32①）。 */
    public static final String ACTIONS_COLLECTION = BASE_PREFIX + "/actions";
    /** #24b POST；提交（commitAction，经属主 Engine 写端点，Agent 不写对方表）。 */
    public static final String ACTION_COMMIT = BASE_PREFIX + "/actions/{" + P_ACTION_ID + "}/commit";

    // ── 能力探测（F10-16 附属端点，RunProgressFacade；随 #7 同族登记）──
    /** GET .../progress-capability → sse|polling（F10-16；§5.2 表外能力位，SSE 未就绪即回 polling）。 */
    public static final String RUN_PROGRESS_CAPABILITY = BASE_PREFIX + "/runs/{" + P_RUN_ID + "}/progress-capability";

    /**
     * 端点路径模板填充（与 {@link #P_QUESTION_ID} 等参数名单源配套）。
     *
     * @param template 本类常量（含 {@code {param}} 占位）
     * @param param    占位名（禁字面量，一律取 {@code P_*} 常量）
     * @param value    实际 ID
     */
    public static String resolve(String template, String param, String value) {
        if (template == null || !template.contains("{" + param + "}")) {
            throw new IllegalArgumentException("路径模板不含占位 {" + param + "}: " + template);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("路径参数值不得为空: " + param);
        }
        return template.replace("{" + param + "}", value);
    }
}
