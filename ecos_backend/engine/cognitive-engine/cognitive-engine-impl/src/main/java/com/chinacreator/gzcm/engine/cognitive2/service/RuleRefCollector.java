package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningStep;
import com.chinacreator.gzcm.engine.cognitive2.model.PrecedentRef;
import com.chinacreator.gzcm.engine.cognitive2.model.RuleRef;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 规则引用收口（Wave-3.2 T4）+ Justification 结构化计数（Wave-3.2 T5）。
 *
 * <p>职责：
 * <ol>
 *   <li>T4: 把 ComplianceRule 列表 → RuleRef 索引（ruleId → RuleRef），按 (name+domain) 去重</li>
 *   <li>T5: 对 ReasoningPath 算结构化计数：rule_hits / precedent_count / avg_confidence</li>
 * </ol>
 *
 * <p>对齐 04 文档 §六 PMO-34/35 映射 + PMO-32 先例复用。</p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-3.2)
 */
@Component
public class RuleRefCollector {

    /**
     * 把 ComplianceRule 列表 → RuleRef 映射（去重 by name+domain）。
     *
     * @param rules KB 规则列表（可空）
     * @return ruleId → RuleRef（已去重，priority 越小 sourceRank 越小）
     */
    public Map<String, RuleRef> toIndex(List<ComplianceRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptyMap();
        }

        // 临时按 (name+domain) 去重，保留 priority 最小的版本
        Map<String, ComplianceRule> dedup = new LinkedHashMap<>();
        for (ComplianceRule r : rules) {
            if (r == null || r.getId() == null) continue;
            if (!r.isEnabled()) continue; // 跳过被禁用的规则
            String key = (r.getName() != null ? r.getName() : "")
                    + "::" + (r.getDomain() != null ? r.getDomain() : "");
            ComplianceRule exist = dedup.get(key);
            if (exist != null && exist.getPriority() <= r.getPriority()) {
                continue;
            }
            dedup.put(key, r);
        }

        Map<String, RuleRef> index = new LinkedHashMap<>();
        int rank = 0;
        // 按 priority 升序构建 sourceRank
        List<ComplianceRule> sorted = new ArrayList<>(dedup.values());
        sorted.sort(Comparator.comparingInt(ComplianceRule::getPriority));
        for (ComplianceRule r : sorted) {
            RuleRef ref = new RuleRef(r.getId(), r.getName(), r.getCondition(), r.getAction());
            ref.setCategory(r.getDomain() != null ? r.getDomain() : "compliance");
            if (r.getVersion() > 0) {
                ref.setVersion(String.valueOf(r.getVersion()));
            }
            ref.setSourceRank(rank++);
            index.put(r.getId(), ref);
        }
        return index;
    }

    /**
     * 对 ReasoningPath 计算结构化计数（T5）。
     *
     * @param path 推理路径（可空）
     * @return counts Map（rule_hits / precedent_count / step_count / avg_confidence）；可空输入返回空 Map
     */
    public Map<String, Object> countForJustification(ReasoningPath path) {
        Map<String, Object> counts = new LinkedHashMap<>();
        if (path == null || path.getSteps() == null) {
            return counts;
        }
        int ruleHits = 0;
        int precedentCount = 0;
        double sumConf = 0.0;
        int total = path.getSteps().size();
        for (ReasoningStep s : path.getSteps()) {
            if (s.getRuleRef() != null) ruleHits++;
            if (s.getPrecedentRef() != null) precedentCount++;
            sumConf += s.getConfidence();
        }
        counts.put("step_count", total);
        counts.put("rule_hits", ruleHits);
        counts.put("precedent_count", precedentCount);
        counts.put("avg_confidence", total > 0 ? Math.round(sumConf / total * 10000.0) / 10000.0 : 0.0);
        return counts;
    }

    /**
     * 把结构化计数注入 Path（同时把既有字符串 justification 末尾追加 counts JSON 摘要）。
     *
     * @param path 推理路径（可空，原对象 in-place 修改）
     */
    public void attachStructuralCount(ReasoningPath path) {
        if (path == null) return;
        Map<String, Object> counts = countForJustification(path);
        if (counts.isEmpty()) return;
        // 不删旧：保留字符串，追加 JSON 摘要
        String jsonLike = counts.toString().replace("=", ":");
        String old = path.getJustification() != null ? path.getJustification() : "";
        path.setJustification(old + " counts=" + jsonLike);
    }
}
