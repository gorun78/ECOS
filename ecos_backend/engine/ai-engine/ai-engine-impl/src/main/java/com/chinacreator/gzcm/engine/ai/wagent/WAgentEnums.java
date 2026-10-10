package com.chinacreator.gzcm.engine.ai.wagent;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * W Agent 编排域字符串/枚举单源（详细设计-10 §二 表头纪律 + F10-05/06/10/11/12/14/17/20/21/22/24）。
 *
 * <p><b>三段一致纪律</b>：PRD-10 冻结枚举 → 本类（代码单源）→ DDL {@code CHECK}（V227~V240）
 * 逐值对齐（卷 07 R-15 同类缺陷预防）。文档口径包名 {@code com.chinacreator.gzcm.ai.wagent}
 * 与本包 {@code com.chinacreator.gzcm.engine.ai.wagent} 属同义（F10-01：不新增 Maven 模块，
 * 只在既有 ai-engine 内建包，以实际模块包根为准）。</p>
 *
 * <p><b>禁散落字面量</b>：任何 Service/DAO/Controller/事件生产者取状态/类型字面量必须引本类，
 * 禁自造第二套词表。</p>
 *
 * <p>已按权威口径裁定的两处文档分歧（详见各嵌套类 Javadoc）：</p>
 * <ol>
 *   <li>StepType 采用设计文档 §4.2 的 {@code tool|llm|…}（PRD REQ-WAG-14 写 {@code tool_call|llm_reason}，
 *       两套名禁并存，以任务核定的 §4.2 集合冻结，V234 CHECK 与本类对齐）；</li>
 *   <li>Run 状态机按 PRD-10 批准迁移链（12 态）∪ F10-21 Kill Switch {@code paused} = 13 态冻结；
 *       卷二「全 11 态」计数为分册口径差异，以 PRD 批准文本为权威。</li>
 * </ol>
 */
public final class WAgentEnums {

    private WAgentEnums() {}

    /** 词表值统一契约：{@link #value()} 即落库/上线的冻结字符串。 */
    public interface WAgentValue {
        /** 冻结字面量（DDL CHECK / JSON 载荷 / REST query 参数一律用它）。 */
        String value();
    }

    /** 把枚举值集合导出为不可变 {@code Set<String>}（DDL 比对与校验用）。 */
    private static Set<String> wordSet(WAgentValue[] entries) {
        Set<String> set = new LinkedHashSet<>();
        for (WAgentValue e : entries) {
            set.add(e.value());
        }
        return Collections.unmodifiableSet(set);
    }

    // ════════════════════════════════════════════════════════════════
    // 1. Run 状态机（F10-11 / PRD-10 §三 REQ-WAG-11 批准链，13 态）
    // ════════════════════════════════════════════════════════════════

    /**
     * Run 状态机：PRD-10 批准文本（权威）——
     * {@code created → planning → plan_validated → executing ⇄ awaiting_input / awaiting_approval /
     * waiting_task → completed | completed_degraded | failed | cancelled | timed_out}，迁移表冻结；
     * F10-21 Kill Switch 增补 {@code paused}（开启即挂起）。合计 <b>13 态</b>。
     * <p>分册「全 11 态」为计数口径差异（卷二未把 paused/终态拆分计数），不构成第 14 值来源；
     * 非法迁移一律 {@code E-WA-STATE 409}（§5.3）。落 V233 {@code status VARCHAR(24)}
     * （最长 {@code completed_degraded}=18，容得下）。</p>
     */
    public enum RunStatus implements WAgentValue {
        CREATED("created"),
        PLANNING("planning"),
        PLAN_VALIDATED("plan_validated"),
        EXECUTING("executing"),
        AWAITING_INPUT("awaiting_input"),
        AWAITING_APPROVAL("awaiting_approval"),
        WAITING_TASK("waiting_task"),
        /** F10-21：Kill Switch 开启即挂起（租户/系统级）。 */
        PAUSED("paused"),
        /** 终态 */
        COMPLETED("completed"),
        /** 终态（带 DG-* 降级标注） */
        COMPLETED_DEGRADED("completed_degraded"),
        /** 终态 */
        FAILED("failed"),
        /** 终态 */
        CANCELLED("cancelled"),
        /** 终态（同步 60s / 异步 30min 预算超时，F10-11） */
        TIMED_OUT("timed_out");

        private final String value;

        RunStatus(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        /** 五终态（§4.2：terminated = completed | completed_degraded | failed | cancelled | timed_out）。 */
        public boolean isTerminal() {
            return this == COMPLETED || this == COMPLETED_DEGRADED
                    || this == FAILED || this == CANCELLED || this == TIMED_OUT;
        }

        public static final Set<String> ALL = wordSet(values());

        public static boolean isValid(String raw) {
            return raw != null && ALL.contains(raw.trim().toLowerCase(Locale.ROOT));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 2. Goal 意图 / intent（F10-05 / REQ-WAG-05，八类封闭枚举，同词表源）
    // ════════════════════════════════════════════════════════════════

    /**
     * 意图八类封闭枚举；{@code ecos_wagent_goal.goal_type}（V227）与
     * {@code ecos_wagent_question.intent}（V229）共用本单源（F10-05：禁二套词表）。
     * 大写落库（DDL CHECK 与本类对齐）。{@code intent_confidence < 0.70} 不得建 Run。
     */
    public enum GoalIntent implements WAgentValue {
        FORECAST("FORECAST"),
        DIAGNOSE("DIAGNOSE"),
        SIMULATE("SIMULATE"),
        RANK("RANK"),
        MONITOR("MONITOR"),
        BUILD("BUILD"),
        DECIDE("DECIDE"),
        REVIEW("REVIEW");

        private final String value;

        GoalIntent(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(values());

        public static boolean isValid(String raw) {
            return raw != null && ALL.contains(raw.trim().toUpperCase(Locale.ROOT));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 3. SlotProvenance 槽位来源（F10-06 / REQ-WAG-06，四值）
    // ════════════════════════════════════════════════════════════════

    /** 槽位来源四值（V227 {@code slots_source_json} 快照内逐槽位标注）。 */
    public enum SlotProvenance implements WAgentValue {
        USER_EXPLICIT("USER_EXPLICIT"),
        CONTEXT_INHERITED("CONTEXT_INHERITED"),
        TENANT_DEFAULT("TENANT_DEFAULT"),
        AGENT_INFERRED("AGENT_INFERRED");

        private final String value;

        SlotProvenance(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(values());
    }

    // ════════════════════════════════════════════════════════════════
    // 4. StepType 六类（F10-14 / §4.2，V234 step_type）
    // ════════════════════════════════════════════════════════════════

    /**
     * Step 六类型，落 V234 {@code step_type VARCHAR(16)}（最长 {@code human_gate}=10）。
     *
     * <p><b>文档分歧裁定</b>：PRD REQ-WAG-14 字面为 {@code tool_call|llm_reason|…}，
     * 设计文档 §4.2/F10-14 为 {@code tool|llm|…}；按任务核定集合以 §4.2 为冻结词表，
     * PRD 两套名禁并存（R4 终验回写时向 PRD-10 登记勘误）。DDL CHECK 与本类对齐。</p>
     */
    public enum StepType implements WAgentValue {
        TOOL("tool"),
        LLM("llm"),
        /** 人工闸门（F10-13：EXPLORED Plan 必先经 human_gate 才可 executing） */
        HUMAN_GATE("human_gate"),
        /** 异步等待 runtime-task（F10-15） */
        WAIT_TASK("wait_task"),
        /** 子 Run（F10-14：depth ≤ 2） */
        SUB_RUN("sub_run"),
        /** 聚合步骤 */
        AGGREGATE("aggregate");

        private final String value;

        StepType(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(values());

        public static boolean isValid(String raw) {
            return raw != null && ALL.contains(raw.trim().toLowerCase(Locale.ROOT));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 5. PlanSource（F10-12/13，V233 plan_source）
    // ════════════════════════════════════════════════════════════════

    /**
     * Plan 来源两值冻结；{@code EXPLORED} 的 Plan 必须先经 {@code human_gate} 才可进
     * {@code executing}（硬约束写在 RunStateMachine.canTransition，F10-13）。
     * <p>V233 脚本注释原含 {@code REUSED 等} 与文档两值口径不符，已按文档订正
     * （任务口径：不一致时以文档为权威修脚本；脚本未实跑，改注释/CHECK 安全）。</p>
     */
    public enum PlanSource implements WAgentValue {
        PLAYBOOK("PLAYBOOK"),
        EXPLORED("EXPLORED");

        private final String value;

        PlanSource(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(values());
    }

    // ════════════════════════════════════════════════════════════════
    // 6. AutomationLevel L0~L3（F10-21 / REQ-WAG-21）
    // ════════════════════════════════════════════════════════════════

    /**
     * 自动化档位四值；生效档 = min(ToolContract.min_level, Run.automation_ceiling, TenantPolicy.max_level)
     * （三重上限，AC-03）。V233 {@code automation_ceiling} / V234 {@code automation_level} /
     * V236 {@code min_level} 为 SMALLINT，落库存 {@link #level()} 数值；
     * 字符串形态（L0~L3）只用于配置与展示单源。
     * <p><b>L3 无自动路径</b>：StepWriter 对 L3 强制 {@code approval_token} 非空。</p>
     */
    public enum AutomationLevel implements WAgentValue {
        L0("L0", 0),
        L1("L1", 1),
        L2("L2", 2),
        L3("L3", 3);

        private final String value;
        private final int level;

        AutomationLevel(String value, int level) {
            this.value = value;
            this.level = level;
        }

        @Override
        public String value() {
            return value;
        }

        public int level() {
            return level;
        }

        /** 三重上限取 min（F10-21 验收 AutomationLevelTest#effectiveLevelIsMinOfThreeCaps 的契约基元）。 */
        public static AutomationLevel min(AutomationLevel... caps) {
            AutomationLevel out = L3;
            for (AutomationLevel c : caps) {
                if (c != null && c.level < out.level) {
                    out = c;
                }
            }
            return out;
        }

        public static AutomationLevel ofLevel(int level) {
            for (AutomationLevel l : values()) {
                if (l.level == level) {
                    return l;
                }
            }
            throw new IllegalArgumentException("非法自动化档位(仅 0~3): " + level);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 7. CandidateType 12 类（F10-20 / REQ-WAG-18，PRD-10 line 174 十二类冻结）
    // ════════════════════════════════════════════════════════════════

    /**
     * 候选 12 类型（PRD-10 冻结中文名录 → 本类英文码为落库形态），落 V238
     * {@code candidate_type VARCHAR(24)}（最长 {@code COGNITIVE_MODEL_DEF}=19）。
     * 与认知域 CM-01~07 的映射归卷 05，禁在此二次定义。
     */
    public enum CandidateType implements WAgentValue {
        /** 数据映射 */
        DATA_MAPPING("DATA_MAPPING"),
        /** DQ 规则 */
        DQ_RULE("DQ_RULE"),
        /** 实体 */
        ENTITY("ENTITY"),
        /** 关系 */
        RELATIONSHIP("RELATIONSHIP"),
        /** 指标 */
        METRIC("METRIC"),
        /** 知识项 */
        KNOWLEDGE_ITEM("KNOWLEDGE_ITEM"),
        /** 假设 */
        ASSUMPTION("ASSUMPTION"),
        /** 情景 */
        SCENARIO("SCENARIO"),
        /** 决策选项 */
        DECISION_OPTION("DECISION_OPTION"),
        /** 行动草稿 */
        ACTION_DRAFT("ACTION_DRAFT"),
        /** 认知模型定义 */
        COGNITIVE_MODEL_DEF("COGNITIVE_MODEL_DEF"),
        /** 模型路由 */
        MODEL_ROUTE("MODEL_ROUTE");

        private final String value;

        CandidateType(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        /** 语义/口径/知识类候选在 ReviewService 层拒绝批量接口（F10-23）。 */
        public boolean rejectsBulkApprove() {
            return this == METRIC || this == KNOWLEDGE_ITEM || this == ASSUMPTION || this == SCENARIO;
        }

        public static final Set<String> ALL = wordSet(values());
    }

    // ════════════════════════════════════════════════════════════════
    // 8. Candidate 状态机 10 值（F10-20，V238 status）
    // ════════════════════════════════════════════════════════════════

    /**
     * Candidate 状态机 10 值（冻结草案）：
     * 正向链 {@code generated → validating → validated → review_pending → approved → committing → published}，
     * 旁路终态 {@code rejected / expired / rolled_back}。
     *
     * <p><b>比对说明</b>：PRD-10 只声明"状态机 10 值"未逐值枚举（设计文档 F10-20 给出生命周期语义），
     * 本集合按 §5.2 reviewCandidate/publishCandidate 与 F10-20 验收
     * （{@code lifecycleTransitionsAreLegalOnlyForwardChain}、{@code expiredCandidateCannotBeApproved}、
     * TTL 30 天置 expired）派生冻结；V238 DDL CHECK 与本类对齐（R3-A 若发现权威名录出入，按三段一致
     * 纪律同步修订——脚本未实跑，CHECK 可改）。落 V238 {@code status VARCHAR(16)}
     * （最长 {@code review_pending}=13）。</p>
     */
    public enum CandidateStatus implements WAgentValue {
        GENERATED("generated"),
        VALIDATING("validating"),
        VALIDATED("validated"),
        REVIEW_PENDING("review_pending"),
        APPROVED("approved"),
        COMMITTING("committing"),
        PUBLISHED("published"),
        REJECTED("rejected"),
        /** TTL 30 天由对账任务置过期（F10-20） */
        EXPIRED("expired"),
        ROLLED_BACK("rolled_back");

        private final String value;

        CandidateStatus(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        /** 过期候选不得审批（expiredCandidateCannotBeApproved）。 */
        public boolean canReview() {
            return this == VALIDATED || this == REVIEW_PENDING;
        }

        public static final Set<String> ALL = wordSet(values());

        public static boolean isValid(String raw) {
            return raw != null && ALL.contains(raw.trim().toLowerCase(Locale.ROOT));
        }
    }

    // ════════════════════════════════════════════════════════════════
    // 9. ToolContract 状态机 6 值（F10-17 / REQ-WAG-17，V236 status）
    // ════════════════════════════════════════════════════════════════

    /** 工具契约生命周期：{@code draft→registered→validated→active→deprecated→retired}（小写落 V236，最长 registered=10）。 */
    public enum ToolContractStatus implements WAgentValue {
        DRAFT("draft"),
        REGISTERED("registered"),
        VALIDATED("validated"),
        /** 只有 active 版本可被选择执行（F10-17 验收） */
        ACTIVE("active"),
        DEPRECATED("deprecated"),
        /** 退役工具永不召回（F10-18 验收） */
        RETIRED("retired");

        private final String value;

        ToolContractStatus(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(values());
    }

    // ════════════════════════════════════════════════════════════════
    // 10. PolicyEffect 四值（F10-22 / REQ-WAG-22，V234 policy_effect）
    // ════════════════════════════════════════════════════════════════

    /**
     * security-engine PEP 裁决效果四值，落 V234 {@code policy_effect VARCHAR(20)}
     * （最长 require_approval=16）。security 不可用 = DENY（L1+ 拒绝，F10-22）；
     * mask/rowFilter obligations 由 security-engine 执行，Agent 侧不实现脱敏。
     */
    public enum PolicyEffect implements WAgentValue {
        ALLOW("allow"),
        ALLOW_WITH_MASK("allow_with_mask"),
        REQUIRE_APPROVAL("require_approval"),
        DENY("deny");

        private final String value;

        PolicyEffect(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(values());
    }

    // ════════════════════════════════════════════════════════════════
    // 11. 工具面辅助词表（REQ-WAG-17：category / side_effect / disclosure）
    // ════════════════════════════════════════════════════════════════

    /**
     * 工具类别五值（PRD REQ-WAG-17 冻结小写）；V236 脚本注释原写 {@code READ/WRITE/COMMIT 等}
     * 违 PRD 名录（WRITE 非词表值且大小写不一致），已按文档订正注释并与既有 CHECK
     * {@code ck_wagent_tool_rollback}（{@code category <> 'commit'}）的小写口径保持一致。
     */
    public enum ToolCategory implements WAgentValue {
        READ("read"),
        COMPUTE("compute"),
        CANDIDATE("candidate"),
        /** commit 类必须登记回滚预案（V236 CHECK + 应用双校验） */
        COMMIT("commit"),
        NOTIFY("notify");

        private final String value;

        ToolCategory(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(values());
    }

    /** 副作用三类（REQ-WAG-17）。 */
    public enum SideEffect implements WAgentValue {
        NONE("none"),
        DRAFT("draft"),
        PRODUCTION("production");

        private final String value;

        SideEffect(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(values());
    }

    /**
     * 披露形态两类（REQ-WAG-17 / F10-18）。
     * <p>注意 V236 {@code disclosure} 原列宽 VARCHAR(12) 容不下 {@code always_loaded}(13 字符)，
     * 已随 CHECK 收口一并订正为 VARCHAR(16)（脚本未实跑，改名/改列宽安全，DR04 收口同法）。</p>
     */
    public enum Disclosure implements WAgentValue {
        ALWAYS_LOADED("always_loaded"),
        SEARCHABLE("searchable");

        private final String value;

        Disclosure(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(values());
    }

    // ════════════════════════════════════════════════════════════════
    // 12. DegradeEnum 预登记降级白名单 8 值（F10-10 / REQ-WAG-10）
    // ════════════════════════════════════════════════════════════════

    /**
     * 预登记降级项八值白名单（附件 DG-K1/DG-D1/DG-C1/DG-L1/DG-C2/DG-T1/DG-M1/DG-M2 → ECOS 同名码）。
     * 配置单源（sysman {@code wagent.degrade} 组）可<b>关</b>不可<b>开新</b>（F10-10）；
     * 每值的 {@code applies_to}（可用步骤）与 {@code requires_ack}（是否需用户确认）登记于此，
     * 运行期生效集经配置收缩；Run/结论 VO 必带 {@code degradations[]}，前端降级横幅单源读该字段。
     * 未登记降级项一律拒绝（unregisteredDegradeIsRejected）。
     */
    public enum DegradeCode implements WAgentValue {
        /** 知识层缺画像 ⇒ 以通用假设替代，结论标"依据：假设" */
        DG_K1("DG-K1", "K 层：知识画像缺失", true),
        /** D 层数据陈旧，按 as-of 日期出数并标注 */
        DG_D1("DG-D1", "D 层：数据新鲜度不足", true),
        /** C 层认知模型不可用，退化为规则/统计基线 */
        DG_C1("DG-C1", "C 层：认知模型不可用", true),
        /** LLM 通道不可用，只出确定性数据段（无叙述） */
        DG_L1("DG-L1", "LLM：叙述通道不可用", false),
        /** C 层情景对比不可用，只出单情景结论 */
        DG_C2("DG-C2", "C 层：多情景对比不可用", true),
        /** 工具超时，跳过非关键步骤出部分结论 */
        DG_T1("DG-T1", "T：非关键工具超时跳过", true),
        /** 进度通道降级：SSE 不可用改轮询（F10-16） */
        DG_M1("DG-M1", "M：进度通道 SSE→轮询", false),
        /** 计量/监控面不可用，运行继续但结论页标注监控缺失 */
        DG_M2("DG-M2", "M：监控计量不可用", false);

        private final String value;
        private final String description;
        /** 是否需用户确认后才生效（requires_ack）。 */
        private final boolean requiresAck;

        DegradeCode(String value, String description, boolean requiresAck) {
            this.value = value;
            this.description = description;
            this.requiresAck = requiresAck;
        }

        @Override
        public String value() {
            return value;
        }

        public String description() {
            return description;
        }

        public boolean requiresAck() {
            return requiresAck;
        }

        public static final Set<String> ALL = wordSet(values());
    }

    // ════════════════════════════════════════════════════════════════
    // 13. 其它辅助词表（Readiness 层位 / Situation 假设来源 / 评审决定）
    // ════════════════════════════════════════════════════════════════

    /** DIKC 四层（F10-08；V231/V232 layer CHAR(1)）。 */
    public enum ReadinessLayer implements WAgentValue {
        D("D"),
        I("I"),
        K("K"),
        C("C");

        private final String value;

        ReadinessLayer(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    /** Readiness 定级四档（V230 grade CHAR(1)；规则：任一 blocker→D；≥2 fail→C；任一 warn→B；全 pass→A）。 */
    public enum ReadinessGrade implements WAgentValue {
        A("A"),
        B("B"),
        C("C"),
        D("D");

        private final String value;

        ReadinessGrade(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    /** 检查项状态（F10-08 定级输入；V231 status VARCHAR(10) 容得下 blocker=8）。 */
    public enum ReadinessCheckStatus implements WAgentValue {
        PASS("pass"),
        WARN("warn"),
        FAIL("fail"),
        BLOCKER("blocker"),
        /** 探测失败=UNKNOWN 且计入缺项，禁算 pass（probeErrorYieldsUnknownNotPass） */
        UNKNOWN("unknown");

        private final String value;

        ReadinessCheckStatus(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(values());
    }

    /**
     * Situation 假设来源三值（§9.2 契约：source ∈ {user, default, inferred}）。
     * CM-07 红线落点：{@code inferred} 假设不得直接进 {@code belief.probability}。
     */
    public enum AssumptionSource implements WAgentValue {
        USER("user"),
        DEFAULT("default"),
        INFERRED("inferred");

        private final String value;

        AssumptionSource(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(values());
    }

    /** 候选评审决定（V239 decision VARCHAR(16)；四眼校验 reviewer ≠ generator）。 */
    public enum ReviewDecision implements WAgentValue {
        APPROVE("APPROVE"),
        REJECT("REJECT"),
        REVISE("REVISE");

        private final String value;

        ReviewDecision(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(values());
    }
}
