package com.chinacreator.gzcm.engine.cognitive2.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * OAG 任务拆解节点 — 把结构化需求拆成子任务 DAG（03 文档 §三 s2_plan）。
 *
 * <p>对齐 OAG 8 步：intake 之后产出 plan，每个子任务携带 sub_task_id + description + depends_on。
 * 当前实现是"模板化拆解"：诊断类需求固定拆 5 个子任务
 *   [建模, 抽取, 建图, 推理, 策略]，对应下游节点 s3..s7 的依赖。</p>
 *
 * <p>对齐 03 文档：
 * [s2_plan | OAG_PLAN | intent_id+structured_request → plan_id+sub_tasks]</p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-3.2)
 */
@Component
public class OagPlannerService {

    private static final Logger log = LoggerFactory.getLogger(OagPlannerService.class);

    /** 5 个子任务模板（顺序对应 OAG 8 步 s3..s7） */
    private static final List<String[]> SUB_TASK_TEMPLATES = List.of(
            new String[]{"ontology",   "本体建模：把 domain 内的指标映射为本体对象"},
            new String[]{"extract",    "知识抽取：从业务文档抽取 entity/link/rule"},
            new String[]{"build_kg",   "建图：把候选实体/关系写入知识图谱"},
            new String[]{"reason",     "混合推理：基于事实 + 规则 + 知识做诊断"},
            new String[]{"strategy",   "策略生成：基于推理结果 + 先例生成改进建议"}
    );

    /**
     * 把上游 intake 输出拆解为子任务列表。
     *
     * @param intentId      上游 intent_id
     * @param slots         上游 slot_map（含 raw_request/domain/deviation/metric）
     * @param config        节点 config（可空），可含 max_depth / max_actions
     * @return plan_id / sub_tasks（每条含 sub_task_id / description / depends_on）
     */
    public Map<String, Object> handle(String intentId, Map<String, Object> slots, Map<String, Object> config) {
        Map<String, Object> result = new LinkedHashMap<>();
        String planId = "plan-" + UUID.randomUUID().toString().substring(0, 8);
        result.put("plan_id", planId);
        result.put("intent_id", intentId == null ? "unknown" : intentId);
        if (slots != null) {
            result.put("domain", slots.getOrDefault("domain", "default"));
            result.put("metric", slots.getOrDefault("metric", ""));
            result.put("deviation", slots.getOrDefault("deviation", 0.0));
        }

        List<Map<String, Object>> subTasks = new ArrayList<>();
        String prevTaskId = null;
        for (int i = 0; i < SUB_TASK_TEMPLATES.size(); i++) {
            String[] tpl = SUB_TASK_TEMPLATES.get(i);
            String taskId = "task-" + (i + 1);
            Map<String, Object> st = new LinkedHashMap<>();
            st.put("sub_task_id", taskId);
            st.put("kind", tpl[0]);
            st.put("description", tpl[1]);
            // 串行 DAG：每个任务依赖前一个（plan 拓扑为线性）
            List<String> deps = new ArrayList<>();
            if (prevTaskId != null) {
                deps.add(prevTaskId);
            }
            st.put("depends_on", deps);
            subTasks.add(st);
            prevTaskId = taskId;
        }
        result.put("sub_tasks", subTasks);

        log.info("OAG_PLAN handled: plan_id={}, intent_id={}, sub_tasks={}",
                planId, intentId, subTasks.size());
        return result;
    }
}
