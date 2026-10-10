package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Goal/Question/Step 状态词表单源（详细设计-10 §二 F10-05/06/11/14、§4.2 控制流派生）。
 *
 * <p><b>为什么不在 {@code WAgentEnums}（#127 冻结契约单源包）内：</b>该类按 §二 冻结的 19 个
 * 嵌套枚举未含 Goal/Question/Step 三张状态词表（DDL V227/V229/V234 的 status 列均无 CHECK，
 * 词表由应用层单源负责）。R2-A 不修改冻结类（只增不改），在本编排核心包补第三处单源，
 * 纪律与 {@code WAgentEnums} 一致：<b>任何 Service/DAO/Controller 取状态字面量必须引本类，
 * 禁自造第二套词表</b>。R4 终验（#134）评估是否并回 {@code WAgentEnums}。</p>
 *
 * <p>值域与列宽核对：V227/V229 {@code status VARCHAR(20)}（最长 {@code awaiting_input}=14 ✓）、
 * V234 {@code status VARCHAR(16)}（最长 {@code succeeded}=9 ✓）。</p>
 */
public final class WAgentStatus {

    private WAgentStatus() {}

    private static Set<String> wordSet(String... values) {
        Set<String> set = new LinkedHashSet<>();
        Collections.addAll(set, values);
        return Collections.unmodifiableSet(set);
    }

    /**
     * Goal 状态四值（F10-05 创建即 {@code draft}；F10-07 修订链上旧版本被新版本取代置
     * {@code superseded}；口径确认后 {@code active}；归档 {@code closed}）。
     */
    public enum GoalStatus implements WAgentEnums.WAgentValue {
        DRAFT("draft"),
        ACTIVE("active"),
        SUPERSEDED("superseded"),
        CLOSED("closed");

        private final String value;

        GoalStatus(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(
                java.util.Arrays.stream(values()).map(GoalStatus::value).toArray(String[]::new));

        public static boolean isValid(String raw) {
            return raw != null && ALL.contains(raw.trim().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Question 状态六值（§4.2 {@code Question(open)} 起点；F10-05 置信不足/分类器不可用 ⇒
     * {@code clarifying}（不建 Run）；F10-06 连续两轮无新增信息 ⇒ {@code awaiting_input}；
     * Run 建立后 {@code running}；出结论 {@code answered}；取消 {@code cancelled}）。
     */
    public enum QuestionStatus implements WAgentEnums.WAgentValue {
        OPEN("open"),
        CLARIFYING("clarifying"),
        AWAITING_INPUT("awaiting_input"),
        RUNNING("running"),
        ANSWERED("answered"),
        CANCELLED("cancelled");

        private final String value;

        QuestionStatus(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(
                java.util.Arrays.stream(values()).map(QuestionStatus::value).toArray(String[]::new));

        public static boolean isValid(String raw) {
            return raw != null && ALL.contains(raw.trim().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Step 状态六值（F10-14：UNIQUE(run_id, step_key, attempt)；恢复算法读 run_id 下最大
     * {@code succeeded} Step 继续，§4.2/F10-11）。
     */
    public enum StepStatus implements WAgentEnums.WAgentValue {
        PENDING("pending"),
        RUNNING("running"),
        SUCCEEDED("succeeded"),
        FAILED("failed"),
        SKIPPED("skipped"),
        CANCELLED("cancelled");

        private final String value;

        StepStatus(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        public static final Set<String> ALL = wordSet(
                java.util.Arrays.stream(values()).map(StepStatus::value).toArray(String[]::new));

        public static boolean isValid(String raw) {
            return raw != null && ALL.contains(raw.trim().toLowerCase(Locale.ROOT));
        }

        public boolean isTerminal() {
            return this == SUCCEEDED || this == FAILED || this == SKIPPED || this == CANCELLED;
        }
    }
}
