package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.Decision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * OAG 策略生成节点 — 把推理结果 + 历史先例融合为可执行策略（03 文档 §三 s7_strategy）。
 *
 * <p>对齐 03 文档：
 * [s7_strategy | OAG_STRATEGY | reasoner_result+precedent_ids[] → strategy{actions[],cost,risk}+precedent_refs[]]</p>
 *
 * <p>策略生成规则（纯规则，不依赖 LLM）：
 * <ol>
 *   <li>从 reasoner_result 提取 suggested_actions
 *   <li>从已知历史决策（precedents）中扫描相似决策，提取前置 evidence
 *   <li>融合为 strategy：actions[]（5 项内）+ costEstimate（低成本/中成本/高成本标签）+ risk（低/中/高）
 * </ol></p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-3.2)
 */
@Component
public class StrategyGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(StrategyGeneratorService.class);

    /**
     * 融合推理结果 + 历史先例，输出策略。
     *
     * @param reasonerResult   上游 s6_reason 的节点输出（含 answer/confidence/reasoningPath）
     * @param precedentIds     先例 ID 列表（可为空）
     * @param precedents       已查到的相似决策（来自 DecisionService.findSimilarDecisions）
     * @param maxActions       最大建议动作数（默认 5）
     * @return strategy / precedent_refs
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> handle(Map<String, Object> reasonerResult,
                                      List<String> precedentIds,
                                      List<Decision> precedents,
                                      int maxActions) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 构建策略 actions
        List<String> actions = new ArrayList<>();
        if (reasonerResult != null) {
            Object raw = reasonerResult.get("suggested_actions");
            if (raw instanceof List) {
                for (Object o : (List<Object>) raw) {
                    if (o != null && actions.size() < maxActions) {
                        actions.add(String.valueOf(o));
                    }
                }
            }
        }
        // 兜底：从 answer 里砍出前 200 字作为第一条建议
        if (actions.isEmpty() || actions.size() < 2) {
            if (reasonerResult != null && reasonerResult.get("answer") != null) {
                String ans = String.valueOf(reasonerResult.get("answer"));
                actions.add("依据推理结论执行: " + (ans.length() > 200 ? ans.substring(0, 200) + "..." : ans));
            }
        }
        if (actions.isEmpty()) {
            actions.add("依据当前推理结论补充人工复核与数据核查");
            actions.add("监控关键相关指标变化趋势");
            actions.add("建立告警阈值，跟踪偏差恢复情况");
        }
        if (actions.size() > maxActions) {
            actions = new ArrayList<>(actions.subList(0, maxActions));
        }

        // 2. 估算 cost 与 risk
        String cost = "中成本";
        String risk = "中风险";
        if (reasonerResult != null) {
            Object confObj = reasonerResult.get("confidence");
            double conf = confObj instanceof Number ? ((Number) confObj).doubleValue() : 0.5;
            if (conf >= 0.8) {
                cost = "低成本";
                risk = "低风险";
            } else if (conf < 0.5) {
                cost = "高成本";
                risk = "高风险";
            }
        }

        Map<String, Object> strategy = new LinkedHashMap<>();
        strategy.put("actions", actions);
        strategy.put("cost", cost);
        strategy.put("risk", risk);
        strategy.put("actions_count", actions.size());
        result.put("strategy", strategy);

        // 3. 携带先例引用（PRE 后续会拼接 precedentRefs 字段）
        List<Map<String, Object>> precedentRefs = new ArrayList<>();
        int seen = 0;
        if (precedents != null) {
            for (Decision d : precedents) {
                if (d == null || seen >= maxActions) continue;
                Map<String, Object> pr = new LinkedHashMap<>();
                pr.put("decision_id", d.getId());
                pr.put("summary", d.getScenario() != null ? d.getScenario() : "");
                pr.put("outcome", d.getOutcome() != null ? d.getOutcome() : "");
                pr.put("conf", d.getConfidence());
                precedentRefs.add(pr);
                seen++;
            }
        }
        if (precedentRefs.isEmpty() && precedentIds != null && !precedentIds.isEmpty()) {
            for (String pid : precedentIds) {
                if (seen >= maxActions) break;
                Map<String, Object> pr = new LinkedHashMap<>();
                pr.put("decision_id", pid);
                pr.put("summary", "");
                pr.put("outcome", "");
                pr.put("conf", -1.0);
                precedentRefs.add(pr);
                seen++;
            }
        }
        result.put("precedent_refs", precedentRefs);

        log.info("OAG_STRATEGY handled: actions={}, cost={}, risk={}, precedent_refs={}",
                actions.size(), cost, risk, precedentRefs.size());
        return result;
    }
}
