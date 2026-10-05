package com.chinacreator.gzcm.ai.wagent;

/**
 * 分册10 F10-03/F10-05 · W Agent 控制面 API 前缀与 operationId 单源常量（R-59 契约同源 / ADR-21 §2.2）。
 *
 * <p>不新增 Maven 模块，仅 ai-engine 内建包：前缀 {@code /api/v1/wagent/**}
 * 与 §5.2 24 REST operationId 全集中一处，禁被 controller 用字面量重复展开，
 * 与 OpenAPI 契约片段（P-5 前置件）共源。ledger §5.2 端点列表逐条映射。</p>
 */
public final class WAgentApiPaths {

    private WAgentApiPaths() {}

    /** 唯一入口前缀（同时反注册 {@code /api/wagent/**} 双轨口，双路径经 {@link #V1_BASE_START}）。 */
    public static final String V1_BASE_START = "/api/v1/wagent";
    public static final String V1_BASE = V1_BASE_START + "/**";

    // ── §5.2 端点（24 个，路径与 operationId 一一对应）──

    /** #1 createGoal · POST /goals */
    public static final String CREATE_GOAL = V1_BASE_START + "/goals";
    /** #2 reviseGoal · PATCH /goals/{goalId}（被引用则 409 E-WA-STATE） */
    public static final String REVISE_GOAL = V1_BASE_START + "/goals/{goalId}";
    /** #3 createQuestion · POST /questions */
    public static final String CREATE_QUESTION = V1_BASE_START + "/questions";
    /** #4 getQuestion · GET /questions/{questionId} */
    public static final String GET_QUESTION = V1_BASE_START + "/questions/{questionId}";
    /** #5 startRun · POST /runs · {@code Prefer: respond-async} 支持 202 */
    public static final String START_RUN = V1_BASE_START + "/runs";
    /** #6 getRun · GET /runs/{runId} */
    public static final String GET_RUN = V1_BASE_START + "/runs/{runId}";
    /** #7 streamRunEvents · GET /runs/{runId}/events（SSE，P-4 未闭合前 501 或 polling 降级） */
    public static final String STREAM_RUN_EVENTS = V1_BASE_START + "/runs/{runId}/events";
    /** #8 submitRunInput · POST /runs/{runId}/input · {@code awaiting_input} 应答 */
    public static final String SUBMIT_RUN_INPUT = V1_BASE_START + "/runs/{runId}/input";
    /** #9 approveRunGate · POST /runs/{runId}/approval */
    public static final String APPROVE_RUN_GATE = V1_BASE_START + "/runs/{runId}/approval";
    /** #10 cancelRun · POST /runs/{runId}/cancel · 幂等 */
    public static final String CANCEL_RUN = V1_BASE_START + "/runs/{runId}/cancel";
    /** #11 setKillSwitch · POST /flags/kill-switch · 租户/系统级 admin + audit */
    public static final String SET_KILL_SWITCH = V1_BASE_START + "/flags/kill-switch";
    /** #12 getReadiness · GET /readiness/{questionId} */
    public static final String GET_READINESS = V1_BASE_START + "/readiness/{questionId}";
    /** #13 refillReadiness · POST /readiness/{questionId}/refill（循环 ≤3） */
    public static final String REFILL_READINESS = V1_BASE_START + "/readiness/{questionId}/refill";
    /** #14 searchTools · GET /tools（权限过滤后返回，渐进披露入口） */
    public static final String SEARCH_TOOLS = V1_BASE_START + "/tools";
    /** #15 describeTool · GET /tools/{name} */
    public static final String DESCRIBE_TOOL = V1_BASE_START + "/tools/{name}";
    /** #16 validateToolContract · POST /tools/{name}/validate · 契约测试五关 */
    public static final String VALIDATE_TOOL_CONTRACT = V1_BASE_START + "/tools/{name}/validate";
    /** #17 listCandidates · GET /candidates */
    public static final String LIST_CANDIDATES = V1_BASE_START + "/candidates";
    /** #18 getCandidate · GET /candidates/{candidateId} · 含 basis/五段校验 */
    public static final String GET_CANDIDATE = V1_BASE_START + "/candidates/{candidateId}";
    /** #19 reviewCandidate · POST /candidates/{candidateId}/review · 四眼；语义/口径/知识禁批量 */
    public static final String REVIEW_CANDIDATE = V1_BASE_START + "/candidates/{candidateId}/review";
    /** #20 publishCandidate · POST /candidates/{candidateId}/publish · pre-commit 裁决 + Git */
    public static final String PUBLISH_CANDIDATE = V1_BASE_START + "/candidates/{candidateId}/publish";
    /** #21 listClaims · GET /claims/{runId} */
    public static final String LIST_CLAIMS = V1_BASE_START + "/claims/{runId}";
    /** #22 traceEvidence · GET /evidence/{evidenceId} */
    public static final String TRACE_EVIDENCE = V1_BASE_START + "/evidence/{evidenceId}";
    /** #23 recordDecision · POST /decisions */
    public static final String RECORD_DECISION = V1_BASE_START + "/decisions";
    /** #24 draftAction · POST /actions 与 commitAction · POST /actions/{id}/commit */
    public static final String DRAFT_ACTION = V1_BASE_START + "/actions";
    public static final String COMMIT_ACTION = V1_BASE_START + "/actions/{id}/commit";

    /** §5.1 前缀策略：进 VersionPrefixRewriteFilter 时<b>不加前向重写条目</b>（本版新前缀，target=source，避免 R1 FAIL）。 */
    public static final String PREFIX_ONLY = V1_BASE_START;
}
