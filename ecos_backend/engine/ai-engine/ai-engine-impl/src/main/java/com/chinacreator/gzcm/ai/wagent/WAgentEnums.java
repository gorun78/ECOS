package com.chinacreator.gzcm.ai.wagent;

/**
 * 分册10 F10-11/F10-12/F10-20/F10-21/F10-05/F10-10 · W Agent 状态机与枚举单源
 * （PRD 冻结 ⇒ 代码 ⇒ DDL CHECK 三段一致，铁律 §4 D-12 / R-15 预防）。
 * 仅字面量收拢：状态机迁移表在 {@code WAgentRunStateMachine}；
 * 本类只定义"允许出现哪些字面量"。禁在 controller/service 里用字面 `String` 存状态。
 */
public final class WAgentEnums {

    private WAgentEnums() {}

    /** §6.2 V233 Run 状态机 12 值（附件二册 §2.2 全 11 态 + CANCELLED；非法迁移 = {@code E-WA-STATE}）。 */
    public enum RunStatus {
        PLANNING, AWAITING_INPUT, AWAITING_APPROVAL, PLAN_VALIDATED, EXECUTING,
        WAITING_TASK, PAUSED, COMPLETED, COMPLETED_DEGRADED, FAILED, CANCELLED, TIMED_OUT;

        /** 终态集合（COMPLETED / COMPLETED_DEGRADED / FAILED / CANCELLED / TIMED_OUT，无出边）。 */
        public boolean isTerminal() {
            return this == COMPLETED || this == COMPLETED_DEGRADED || this == FAILED
                    || this == CANCELLED || this == TIMED_OUT;
        }

        /** 静态重载：Facade / Controller 反序列化时防御性判定终态（容忍 null）。 */
        public static boolean isTerminal(RunStatus s) {
            return s != null && s.isTerminal();
        }
    }

    /** F10-14 Step 六类型（step_type VARCHAR(16) 落表）。 */
    public enum StepType { TOOL, LLM, HUMAN_GATE, WAIT_TASK, SUB_RUN, AGGREGATE }

    /** F10-12 / F10-13 Plan 来源（EXPLORED 必走 human_gate 硬约束，见 {@code ExplorationGate}）。 */
    public enum PlanSource { PLAYBOOK, EXPLORED }

    /** F10-21 自动化等级 L0~L3（三重上限取 min；L3 无自动路径 → 强制 approval_token 非空）。 */
    public enum AutomationLevel { L0, L1, L2, L3 }

    /** F10-08 Readiness 定级四值（确定性规则 non-LLM；gradeIsDeterministicFromThresholdsNotLlm 测试基准）。 */
    public enum ReadinessGrade { A, B, C, D }

    /** F10-08 各 layer 状态五值（失败/未知禁止算 pass）。 */
    public enum LayerStatus { PASS, WARN, FAIL, UNKNOWN, BLOCKED }

    /** F10-20 Candidate 12 类型（附件 §6.1）。语义/口径/知识三类禁批量审批（见 {@code BulkApproveGuard}）。 */
    public enum CandidateType {
        KNOWLEDGE_PROFILE, METRIC_DEFINITION, METRIC_CALIBER, SEMANTIC_METRIC,
        RULE, GRAPH_EDGE, PROMPT, PLAYBOOK, SKILL, ACTION, DECISION, DATA_SNAPSHOT
    }

    /** F10-20 Candidate 状态 10 值。 */
    public enum CandidateStatus {
        DRAFT, VALIDATING, READY, PENDING_APPROVAL, APPROVED, REJECTED,
        PUBLISHED, EXPIRED, SUPERSEDED, REVOKED
    }

    /** F10-17 Tool Contract 六态生命周期。 */
    public enum ToolStatus { DRAFT, REGISTERED, VALIDATED, ACTIVE, DEPRECATED, RETIRED }

    /** F10-17 工具类别；{@code COMMIT} 类别 ⇒ {@code rollback_plan_text NOT NULL} 双校（DB CHECK + 应用）。 */
    public enum ToolCategory { QUERY, ANALYZE, COMMIT, AGGREGATE }

    /** F10-24 Claim 类型 3 值。 */
    public enum ClaimType { FACT, INFERENCE, RECOMMENDATION }

    /** F10-24 value_source 枚举（数字来源必在其中之一，否则 {@code E-WA-NARRATIVE-NUM}）。 */
    public enum ValueSource { CM_01, CM_02, CM_03, CM_04, CM_05, DATA_QUERY, DERIVED, NONE }

    /** F10-05 意图/Goal 类型 8 值（intent 与 goal_type 同源单套词表）。 */
    public enum IntentType { FORECAST, DIAGNOSE, SIMULATE, RANK, MONITOR, BUILD, DECIDE, REVIEW }

    /** F10-06 槽位来源四来源，逐槽位存 slots_source_json + inferred_count 实列。 */
    public enum SlotProvenance { USER_EXPLICIT, CONTEXT_INHERITED, TENANT_DEFAULT, AGENT_INFERRED }

    /** F10-10 预登记降级白名单 8 值（配置可关不可开新；附件 DG-*）。 */
    public enum DegradeCode {
        DG_K1,   // 画像缺失
        DG_D1,   // 样本 n<30 或 <10
        DG_C1,   // 图库不可用
        DG_C2,   // 列权限受限
        DG_L1,   // llm-gateway 不可用
        DG_T1,   // 时序引擎降级
        DG_M1,   // 模型路由抖动
        DG_M2    // 模型能力回退
    }

    /** Policy Guard 五检查点顺序（F10-22：pre-plan / pre-step / post-step / pre-commit / pre-output）。 */
    public enum PolicyCheckpoint { PRE_PLAN, PRE_STEP, POST_STEP, PRE_COMMIT, PRE_OUTPUT }

    /** Policy Guard 输出 effect 四值（fail-closed 由 {@code PolicyGuardTest#securityUnavailableDeniesForLevelOneAndAbove} 承载）。 */
    public enum PolicyEffect { ALLOW, ALLOW_WITH_MASK, REQUIRE_APPROVAL, DENY }

    /** F10-18 / F10-22 Step 记录 error_code 简短枚举（§5.3 七类 E-* 映射）。 */
    public enum ErrorCode {
        E_VALIDATION, E_AUTH, E_POLICY, E_NOT_FOUND, E_WA_STATE,
        E_IDEMPOTENT_CONFLICT, E_RATE, E_WA_BUDGET, E_WA_DEP, E_WA_NARRATIVE_NUM, E_INTERNAL
    }

    /** F10-05 意图/Goal 类型校验（fromUtterance 前置）。 */
    public static boolean isValidIntent(String raw) { return contains(IntentType.class, raw); }
    /** F10-11 Run 状态字面校验（controller 反序列化防御性用）。 */
    public static boolean isValidRunStatus(String raw)  { return contains(RunStatus.class, raw); }
    public static boolean isValidCandidateType(String raw) { return contains(CandidateType.class, raw); }
    public static boolean isValidToolCategory(String raw)  { return contains(ToolCategory.class, raw); }
    public static boolean isValidDegradeCode(String raw)   { return contains(DegradeCode.class, raw); }

    private static boolean contains(Class<? extends Enum<?>> e, String raw) {
        if (raw == null) return false;
        for (Object o : e.getEnumConstants()) {
            if (o.toString().equals(raw)) return true;
        }
        return false;
    }
}
