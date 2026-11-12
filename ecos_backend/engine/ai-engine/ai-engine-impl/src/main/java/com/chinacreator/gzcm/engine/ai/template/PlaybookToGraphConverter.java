package com.chinacreator.gzcm.engine.ai.template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPlan;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPlan.Plan;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentPlan.Step;

/**
 * Wagent Playbook 计划 → 逻辑编排画布骨架的确定性转换器（AI 工作台模板融合，模板型）。
 *
 * <p><b>语义边界（刻意的单向下行，不往返）</b>：转换是<b>一次性快照</b>——生成的画布是独立
 * 逻辑编排定义，后续 Playbook 改版不回灌，Wagent 运行时（Goal/Run/审批/事件流）也不参与
 * 逻辑流执行。接受该阉割是本方案对「模板型」的显式取舍。</p>
 *
 * <p><b>映射表（{@code WAgentEnums.StepType} 六值；单源核对后冻结）</b>：
 * <ul>
 *   <li>{@code tool} → {@code tool} 节点（config 带 toolName/toolVersion）</li>
 *   <li>{@code llm} → {@code llm} 节点</li>
 *   <li>{@code human_gate} → {@code approval} 节点（config 带权限点）</li>
 *   <li>{@code aggregate} → {@code condition} 节点（聚合分支语义，step JSON 原样进 config）</li>
 *   <li>{@code wait_task} → {@code trigger} 节点（等价「等待外部触发」语义）</li>
 *   <li>{@code sub_run} → <b>不映射</b>（嵌套 Run 无画布等价物），记入 {@code unmapped}</li>
 * </ul>
 * 未知值同样记入 {@code unmapped}，<b>禁静默丢弃</b>。</p>
 *
 * <p><b>确定性</b>：节点顺序 = {@link WAgentPlan#topoOrder}（Kahn，声明序稳定）；
 * 坐标 = 层号 × 220 / 层内序号 × 120（固定步长，无随机、无时钟）；
 * 边 = {@code depends_on} 前驱列表原序。同输入两次调用必得逐字符相同的 JSON。</p>
 */
@Component
public class PlaybookToGraphConverter {

    private static final int LAYER_X_STEP = 220;
    private static final int ROW_Y_STEP = 120;
    private static final int BASE_X = 100;
    private static final int BASE_Y = 80;

    /** step_type → 画布节点类型（不可映射者不在表内）。 */
    private static final Map<String, String> STEP_TO_NODE_TYPE = Map.of(
            "tool", "tool",
            "llm", "llm",
            "human_gate", "approval",
            "aggregate", "condition",
            "wait_task", "trigger");

    /** 转换结果（nodes/edges 为前端画布快照形态；unmapped 给调用方出转换报告）。 */
    public record ConvertedGraph(List<Map<String, Object>> nodes,
                                 List<Map<String, Object>> edges,
                                 List<String> unmapped) {
    }

    /** Plan → 画布快照。结构非法（环/悬空依赖）时 topoOrder 返回空 ⇒ fail-loud。 */
    public ConvertedGraph convert(Plan plan) {
        List<Step> ordered = WAgentPlan.topoOrder(plan);
        if (ordered.isEmpty() && !plan.steps().isEmpty()) {
            throw new IllegalArgumentException(
                    "Playbook 计划结构非法（存在环或依赖悬空），拒绝转换为画布");
        }

        Map<String, Integer> layerOf = resolveLayers(ordered);
        Map<Integer, Integer> rowCursor = new LinkedHashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>(ordered.size());
        List<String> unmapped = new ArrayList<>();
        Set<String> mappedKeys = new LinkedHashSet<>();

        for (Step step : ordered) {
            String nodeType = STEP_TO_NODE_TYPE.get(step.stepType());
            if (nodeType == null) {
                unmapped.add(step.stepKey() + ":" + step.stepType());
                continue;
            }
            int layer = layerOf.getOrDefault(step.stepKey(), 0);
            int row = rowCursor.merge(layer, 1, Integer::sum) - 1;
            nodes.add(toCanvasNode(step, nodeType, layer, row));
            mappedKeys.add(step.stepKey());
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (Step step : ordered) {
            if (!mappedKeys.contains(step.stepKey())) continue;
            for (String dep : step.dependsOn()) {
                if (!mappedKeys.contains(dep)) continue; // 前驱被 unmapped 剔除 ⇒ 边同步剔除，不留悬空边
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("id", "e-" + dep + "-" + step.stepKey());
                edge.put("source", dep);
                edge.put("target", step.stepKey());
                edges.add(edge);
            }
        }
        return new ConvertedGraph(nodes, edges, unmapped);
    }

    /** 层号 = 最长前驱链深度（同层节点纵向排布，保证画布自左向右读）。 */
    private static Map<String, Integer> resolveLayers(List<Step> ordered) {
        Map<String, Integer> layerOf = new LinkedHashMap<>();
        for (Step step : ordered) {
            int layer = 0;
            for (String dep : step.dependsOn()) {
                Integer depLayer = layerOf.get(dep);
                if (depLayer != null) layer = Math.max(layer, depLayer + 1);
            }
            layerOf.put(step.stepKey(), layer);
        }
        return layerOf;
    }

    private static Map<String, Object> toCanvasNode(Step step, String nodeType, int layer, int row) {
        Map<String, Object> position = new LinkedHashMap<>();
        position.put("x", BASE_X + layer * LAYER_X_STEP);
        position.put("y", BASE_Y + row * ROW_Y_STEP);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", nodeType);
        data.put("label", step.stepKey());
        data.put("status", "idle");
        data.put("config", nodeConfig(step, nodeType));

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", step.stepKey());
        node.put("type", nodeType);
        node.put("position", position);
        node.put("data", data);
        return node;
    }

    /** 节点 config 形状对齐前端六类 DEFAULT_CONFIGS（缺键由前端默认值补齐，这里只带可确定的）。 */
    private static Map<String, Object> nodeConfig(Step step, String nodeType) {
        Map<String, Object> config = new LinkedHashMap<>();
        switch (nodeType) {
            case "tool" -> {
                config.put("toolName", step.toolName() == null ? "" : step.toolName());
                config.put("toolVersion", step.toolVersion());
                config.put("parameters", "{}");
            }
            case "llm" -> config.put("systemPrompt", "");
            case "approval" -> {
                config.put("approver", step.requiredPermission() == null ? "admin" : step.requiredPermission());
                config.put("timeout", 300);
            }
            case "condition" -> config.put("conditionExpr", "$.status == \"ok\"");
            case "trigger" -> {
                config.put("cronExpr", "");
                config.put("timezone", "Asia/Shanghai");
            }
            default -> { /* 不可达：调用点已按映射表过滤 */ }
        }
        // 溯源键（非侵入）：原 step 的写通道/候选通道标记保留在图里，供人工核对
        config.put("writeChannel", step.writeChannel());
        config.put("inputSlots", step.inputSlots());
        return config;
    }
}
