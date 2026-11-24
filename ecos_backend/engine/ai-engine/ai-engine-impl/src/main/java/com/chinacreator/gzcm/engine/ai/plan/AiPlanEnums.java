package com.chinacreator.gzcm.engine.ai.plan;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 逻辑编排计划词表单源（2026-11-23 从 {@code wagent.WAgentEnums} 的保留面抽取）。
 *
 * <p><b>只保留 5 个仍在用的枚举</b>：{@link GoalIntent}/{@link StepType}/{@link PlanSource}
 * 供 {@code template.*}（内置编排模板、Playbook→画布转换）与 {@code plan.*}（计划编解码）使用；
 * {@link ToolContractStatus}/{@link DegradeCode} 供工具契约源使用。
 * 其余 wagent 面枚举（RunStatus/CandidateType/…，含 Readiness 三词表）随 wagent 面
 * 一并删除，不在本类重建。</p>
 *
 * <p><b>字面值逐字不变</b>：{@code value()} 返回值与原 {@code WAgentEnums} 完全一致
 * （DDL CHECK 与库内既有行按此比对），改名不影响数据。</p>
 */
public final class AiPlanEnums {

    private AiPlanEnums() {}

    /** 词表值统一契约：{@link #value()} 即落库/上线的冻结字符串。 */
    public interface AiValue {
        /** 冻结字面量（DDL CHECK / JSON 载荷 / REST query 参数一律用它）。 */
        String value();
    }

    /** 把枚举值集合导出为不可变 {@code Set<String>}（DDL 比对与校验用）。 */
    private static Set<String> wordSet(AiValue[] entries) {
        Set<String> set = new LinkedHashSet<>();
        for (AiValue e : entries) {
            set.add(e.value());
        }
        return Collections.unmodifiableSet(set);
    }

    // ════════════════════════════════════════════════════════════════
    // Goal 意图（八类封闭枚举；大写落库）
    // ════════════════════════════════════════════════════════════════

    /** 意图八类封闭枚举；{@code intent_confidence < 0.70} 不得建 Run。 */
    public enum GoalIntent implements AiValue {
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
    // StepType 六类（落 V234 step_type VARCHAR(16)，最长 human_gate=10）
    // ════════════════════════════════════════════════════════════════

    /** Step 六类型（小写落库）。 */
    public enum StepType implements AiValue {
        TOOL("tool"),
        LLM("llm"),
        /** 人工闸门（EXPLORED Plan 必先经 human_gate 才可 executing） */
        HUMAN_GATE("human_gate"),
        /** 异步等待 runtime-task */
        WAIT_TASK("wait_task"),
        /** 子 Run（depth ≤ 2） */
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
    // PlanSource（V233 plan_source）
    // ════════════════════════════════════════════════════════════════

    /** Plan 来源两值；{@code EXPLORED} 的 Plan 必须先经 {@code human_gate} 才可进 executing。 */
    public enum PlanSource implements AiValue {
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
    // 工具契约状态机 6 值（V236 status，小写）
    // ════════════════════════════════════════════════════════════════

    /** 工具契约生命周期：{@code draft→registered→validated→active→deprecated→retired}（最长 registered=10）。 */
    public enum ToolContractStatus implements AiValue {
        DRAFT("draft"),
        REGISTERED("registered"),
        VALIDATED("validated"),
        /** 只有 active 版本可被选择执行 */
        ACTIVE("active"),
        DEPRECATED("deprecated"),
        /** 退役工具永不召回 */
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

    /** 工具副作用三值（决定 operationClass 折算：PRODUCTION→WRITE，其余→SUGGEST/READ）。 */
    public enum SideEffect implements AiValue {
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

    // ════════════════════════════════════════════════════════════════
    // 预登记降级项八值白名单
    // ════════════════════════════════════════════════════════════════

    /**
     * 预登记降级项八值白名单（DG-K1/DG-D1/DG-C1/DG-L1/DG-C2/DG-T1/DG-M1/DG-M2）。
     * 配置单源可<b>关</b>不可<b>开新</b>；未登记降级项一律拒绝。
     */
    public enum DegradeCode implements AiValue {
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
        /** 进度通道降级：SSE 不可用改轮询 */
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
}
