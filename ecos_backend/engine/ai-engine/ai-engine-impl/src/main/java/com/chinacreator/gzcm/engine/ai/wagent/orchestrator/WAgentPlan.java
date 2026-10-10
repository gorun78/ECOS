package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.StepType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plan DAG 值对象与编解码单源（详细设计-10 F10-12 / §4.2 / V233 {@code plan_json}）。
 *
 * <p>Plan 只存 <b>受控结构</b>（step_key/step_type/工具名+版本/依赖/等级/写通道标记），
 * 输入输出走引用（{@code input_slots} 只带槽位名与引用形态，禁业务明细正文，§6.3）。
 * {@code plan_json} 的序列化经本类 {@link PlanCodec} 固定字段序（确定性产物，
 * "数字不来自 LLM"的离散面：重放同一 plan_json 必得同一拓扑序列）。</p>
 */
public final class WAgentPlan {

    private WAgentPlan() {}

    /**
     * 单个计划步骤声明。
     *
     * @param stepKey             Run 内唯一键（UNIQUE(run_id, step_key, attempt) 的 step_key 维）
     * @param stepType            六类单源 {@link StepType}（小写 value()）
     * @param toolName            工具名（tool 步骤必填；llm/aggregate 可空）
     * @param toolVersion         工具版本（契约闭合定位用，name+version 双列）
     * @param automationLevel     步骤自动化档位（0~3，落库前对照 run.automation_ceiling）
     * @param dependsOn           前驱 step_key 列表（DAG 边）
     * @param inputSlots          输入槽位（引用形态；禁正文）
     * @param writeChannel        该步骤是否触达写通道（side_effect=production / commit 类工具 ⇒ true）
     * @param viaCandidateChannel 写步骤是否声明经 Candidate 发布唯一写通道（F10-17 咽喉，七项校验 WRITE_CHANNEL 消费）
     * @param requiredPermission  所需权限点（PERMISSION 检查透传给 Policy Guard）
     */
    public record Step(String stepKey, String stepType, String toolName, String toolVersion,
                       Integer automationLevel, List<String> dependsOn, Map<String, Object> inputSlots,
                       boolean writeChannel, boolean viaCandidateChannel, String requiredPermission) {

        public Step {
            dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
            inputSlots = inputSlots == null ? Map.of() : Map.copyOf(inputSlots);
            automationLevel = automationLevel == null ? 0 : automationLevel;
        }

        public boolean isToolStep() {
            return StepType.TOOL.value().equals(stepType);
        }

        public boolean isHumanGate() {
            return StepType.HUMAN_GATE.value().equals(stepType);
        }
    }

    /**
     * 计划（来源 + Playbook 引用 + 步骤集）。
     *
     * @param source         PLAYBOOK／EXPLORED（F10-13 硬约束的判据维）
     * @param playbookGitRef Playbook 在用版本 Git 引用（铁律 §3.1：DB 只存引用，EXPLORED 为 null）
     * @param playbookVersion 在用版本号（同上）
     */
    public record Plan(PlanSource source, String playbookGitRef, String playbookVersion, List<Step> steps) {
        public Plan {
            steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }

    /**
     * 拓扑序（Kahn，按声明顺序稳定出队；恢复算法与执行循环共用的唯一步进序）。
     * 存在环 ⇒ 返回空列表（合法性由 {@link PlanValidator} STRUCTURE 检查拒绝，本方法不抛）。
     */
    public static List<Step> topoOrder(Plan plan) {
        Map<String, Step> byKey = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        for (Step s : plan.steps()) {
            if (s.stepKey() == null || s.stepKey().isBlank() || byKey.containsKey(s.stepKey())) {
                return List.of(); // 空键/重复键 = 结构非法
            }
            byKey.put(s.stepKey(), s);
            indegree.put(s.stepKey(), 0);
        }
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (Step s : plan.steps()) {
            for (String dep : s.dependsOn()) {
                if (!byKey.containsKey(dep)) {
                    return List.of(); // 依赖悬空 = 结构非法
                }
                children.computeIfAbsent(dep, k -> new ArrayList<>()).add(s.stepKey());
                indegree.merge(s.stepKey(), 1, Integer::sum);
            }
        }
        Deque<String> ready = new ArrayDeque<>();
        for (Step s : plan.steps()) { // 声明序入队 ⇒ 确定性
            if (indegree.get(s.stepKey()) == 0) {
                ready.addLast(s.stepKey());
            }
        }
        List<Step> out = new ArrayList<>(plan.steps().size());
        Set<String> seen = new HashSet<>();
        while (!ready.isEmpty()) {
            String key = ready.removeFirst();
            if (!seen.add(key)) {
                continue;
            }
            out.add(byKey.get(key));
            for (String child : children.getOrDefault(key, List.of())) {
                if (indegree.merge(child, -1, Integer::sum) == 0) {
                    ready.addLast(child);
                }
            }
        }
        return out.size() == plan.steps().size() ? List.copyOf(out) : List.of(); // 有环 ⇒ 空
    }

    /** 编解码（plan_json 形态；字段序固定，禁散落手写 JSON）。 */
    public static final class PlanCodec {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        private PlanCodec() {}

        /** Plan → plan_json（PLAYBOOK 引用两列同时由 V233 独立列承载，本 JSON 内亦冗余一份供重放自洽）。 */
        public static String toJson(Plan plan) {
            try {
                Map<String, Object> root = new LinkedHashMap<>();
                root.put("plan_source", plan.source() == null ? null : plan.source().value());
                root.put("playbook_git_ref", plan.playbookGitRef());
                root.put("playbook_version", plan.playbookVersion());
                List<Map<String, Object>> steps = new ArrayList<>();
                for (Step s : plan.steps()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("step_key", s.stepKey());
                    m.put("step_type", s.stepType());
                    m.put("tool_name", s.toolName());
                    m.put("tool_version", s.toolVersion());
                    m.put("automation_level", s.automationLevel());
                    m.put("depends_on", s.dependsOn());
                    m.put("input_slots", s.inputSlots());
                    m.put("write_channel", s.writeChannel());
                    m.put("via_candidate_channel", s.viaCandidateChannel());
                    m.put("required_permission", s.requiredPermission());
                    steps.add(m);
                }
                root.put("steps", steps);
                return MAPPER.writeValueAsString(root);
            } catch (Exception e) {
                throw WAgentOrchestrationException.validation("plan_json 序列化失败");
            }
        }

        /** plan_json → Plan（脏值 fail-loud，禁宽容当空计划）。 */
        public static Plan fromJson(String json) {
            if (json == null || json.isBlank()) {
                throw WAgentOrchestrationException.validation("plan_json 为空（恢复路径禁静默造空计划）");
            }
            try {
                Map<String, Object> root = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
                PlanSource source = parseSource(root.get("plan_source"));
                List<Map<String, Object>> rawSteps = castList(root.get("steps"));
                List<Step> steps = new ArrayList<>();
                for (Map<String, Object> m : rawSteps) {
                    steps.add(new Step(
                            str(m.get("step_key")),
                            str(m.get("step_type")),
                            str(m.get("tool_name")),
                            str(m.get("tool_version")),
                            m.get("automation_level") instanceof Number n ? n.intValue() : 0,
                            castStringList(m.get("depends_on")),
                            castMap(m.get("input_slots")),
                            Boolean.TRUE.equals(m.get("write_channel")),
                            Boolean.TRUE.equals(m.get("via_candidate_channel")),
                            str(m.get("required_permission"))));
                }
                return new Plan(source, str(root.get("playbook_git_ref")), str(root.get("playbook_version")), steps);
            } catch (WAgentOrchestrationException e) {
                throw e;
            } catch (Exception e) {
                throw WAgentOrchestrationException.validation("plan_json 不可解析（禁宽容当空计划）");
            }
        }

        private static PlanSource parseSource(Object v) {
            String raw = str(v);
            for (PlanSource p : PlanSource.values()) {
                if (p.value().equals(raw)) {
                    return p;
                }
            }
            throw WAgentOrchestrationException.validation("plan_source 越界（PlanSource 两值单源）: " + raw);
        }

        private static String str(Object v) {
            return v == null ? null : String.valueOf(v);
        }

        @SuppressWarnings("unchecked")
        private static List<Map<String, Object>> castList(Object v) {
            if (v instanceof List<?> l) {
                return (List<Map<String, Object>>) l;
            }
            throw WAgentOrchestrationException.validation("plan_json.steps 形态非法");
        }

        @SuppressWarnings("unchecked")
        private static List<String> castStringList(Object v) {
            if (v == null) {
                return List.of();
            }
            if (v instanceof List<?> l) {
                return (List<String>) l;
            }
            throw WAgentOrchestrationException.validation("depends_on 形态非法");
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> castMap(Object v) {
            if (v == null) {
                return Map.of();
            }
            if (v instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
            throw WAgentOrchestrationException.validation("input_slots 形态非法");
        }
    }
}
