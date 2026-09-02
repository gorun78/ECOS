package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.JustificationClause;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningStep;
import com.chinacreator.gzcm.engine.cognitive2.model.PrecedentRef;
import com.chinacreator.gzcm.engine.cognitive2.model.RuleRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 因果链 → ReasoningPath 构建器（04 文档 §4.1 + §四 拆解目标）。
 *
 * <p>承接 Wave-2C ReasoningPathBuilder 中 buildStepsFromCausal/buildPathFromCausal 的语义，
 * 但砍开成独立类，单一职责：把 CausalChainResult 的因果链展开为 ReasoningPath。
 *
 * <p>Wave-3.2 增量：
 * <ul>
 *   <li>support maxDepth 裁剪（多余层丢弃）</li>
 *   <li>support precedentRef 注入（PRECEDENT 步骤携带）</li>
 *   <li>每条因果节点 → 1 个 step；RULE 节点必须带 ruleRef（sourceRank 来自 rule priority）</li>
 *   <li>生成结构化 JustificationClause 列表（rule_hits + precedent_count）</li>
 * </ul>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-3.2)
 */
@Component
public class ReasoningPathFromCausalBuilder {

    private static final Logger log = LoggerFactory.getLogger(ReasoningPathFromCausalBuilder.class);

    /**
     * 上下文容器：传入上游已构造好的 RuleRef / PrecedentRef 引用。
     */
    public static final class Context {
        /** ruleId → RuleRef（来自 KB 规则查询） */
        public final Map<String, RuleRef> ruleRefs;
        /** precedentId → PrecedentRef（来自历史决策） */
        public final Map<String, PrecedentRef> precedentRefs;

        public Context(Map<String, RuleRef> ruleRefs, Map<String, PrecedentRef> precedentRefs) {
            this.ruleRefs = ruleRefs != null ? ruleRefs : Collections.emptyMap();
            this.precedentRefs = precedentRefs != null ? precedentRefs : Collections.emptyMap();
        }
    }

    /**
     * 因果链节点列表 → ReasoningStep 列表（04 文档 §4.1 算法）。
     *
     * <p>每个 CausalChainNode 展开为 1 个 ReasoningStep：
     * <ul>
     *   <li>stepId = "depth-{depth}"</li>
     *   <li>stepIndex = depth</li>
     *   <li>sourceType = source 大写（KG / LLM / RULE / RULE_ENGINE / PRECEDENT）</li>
     *   <li>source=RULE 且 ruleId 非空 → 从 ctx.ruleRefs 查；查不到则构造空 RuleRef</li>
     *   <li>node 携带 precedentId（来自 CausalChainNode.getRuleId 的扩展语义这里不用，
     *        保留为可选字段读取）→ 设 precedentRef</li>
     * </ul>
     *
     * @param nodes 因果链节点列表（按 depth 排序）
     * @param ctx   引用上下文（ruleRefs / precedentRefs）
     * @return 推理步骤列表
     */
    public List<ReasoningStep> buildSteps(List<CausalChainNode> nodes, Context ctx) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        return nodes.stream().map(node -> {
            ReasoningStep s = new ReasoningStep();
            s.setStepId("depth-" + node.getDepth());
            s.setStepIndex(node.getDepth());
            s.setDescription(node.getDescription());
            s.setInputFacts(Collections.emptyMap());
            s.setOutputFact(node.getNode());
            s.setConfidence(node.getConfidence());

            String src = node.getSource() != null ? node.getSource().toUpperCase() : "KG";
            s.setSourceType(src);

            // RULE / RULE_ENGINE 节点必须带 ruleRef
            if (src.startsWith("RULE") && node.getRuleId() != null) {
                RuleRef ref = ctx.ruleRefs.get(node.getRuleId());
                if (ref == null) {
                    ref = new RuleRef(node.getRuleId(),
                            node.getRuleName() != null ? node.getRuleName() : "rule-" + node.getRuleId(),
                            null, null);
                    ref.setCategory(node.getDomain() != null ? node.getDomain() : "compliance");
                }
                s.setRuleRef(ref);
            }

            // PRECEDENT 节点（保留扩展：用 description 包含 "precedent:" 前缀表达）
            String precedentId = extractPrecedentId(node.getDescription());
            if (precedentId != null) {
                PrecedentRef prefd = ctx.precedentRefs.get(precedentId);
                if (prefd != null) {
                    s.setPrecedentRef(prefd);
                    s.setSourceType("PRECEDENT");
                }
            }
            return s;
        }).collect(Collectors.toList());
    }

    /**
     * 因果链 → 完整推理路径（含结构化 JustificationClause）。
     *
     * @param nodes      因果链节点
     * @param conclusion 最终诊断结论
     * @param ctx        引用上下文
     * @param maxDepth   裁剪到 maxDepth 层（&lt;= 0 表示不裁剪）
     * @return ReasoningPath（含 steps / ruleRefs / precedentRefs / clauses）
     */
    public ReasoningPath buildPath(List<CausalChainNode> nodes, String conclusion, Context ctx, int maxDepth) {
        if (nodes == null) {
            nodes = Collections.emptyList();
        }
        List<CausalChainNode> trimmed = nodes;
        if (maxDepth > 0 && nodes.size() > maxDepth) {
            trimmed = new ArrayList<>(nodes.subList(0, maxDepth));
            log.debug("因果链被裁剪到 maxDepth={}, 原长度={}", maxDepth, nodes.size());
        }

        List<ReasoningStep> steps = buildSteps(trimmed, ctx);
        ReasoningPath path = new ReasoningPath();
        path.setSteps(steps);
        path.setConclusion(conclusion);

        // 字符串摘要（兼容既有 justification 字符串字段）
        StringBuilder just = new StringBuilder();
        just.append("因果链 ").append(trimmed.size()).append(" 层: ");
        for (CausalChainNode node : trimmed) {
            just.append("[depth=")
                .append(node.getDepth())
                .append(" source=")
                .append(node.getSource() != null ? node.getSource() : "KG")
                .append(" conf=")
                .append(String.format("%.2f", node.getConfidence()))
                .append("] ");
        }
        just.append("Conclusion: ").append(conclusion);
        path.setJustification(just.toString());

        // 聚合 ruleRefs
        path.setRuleRefs(aggregateRuleRefs(steps));

        // 聚合 precedentRefs（Wave-3.2 增量）
        path.setPrecedentRefs(aggregatePrecedentRefs(steps));

        // 生成结构化 JustificationClause 列表（Wave-3.2 增量，G3）
        path.setClauses(buildClauses(trimmed, steps));

        return path;
    }

    /** 聚合 steps 中的 ruleRef。 */
    private List<RuleRef> aggregateRuleRefs(List<ReasoningStep> steps) {
        return steps.stream()
                .map(ReasoningStep::getRuleRef)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** 聚合 steps 中的 precedentRef。 */
    private List<PrecedentRef> aggregatePrecedentRefs(List<ReasoningStep> steps) {
        return steps.stream()
                .map(ReasoningStep::getPrecedentRef)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 从因果节点 + steps 生成结构化 JustificationClause 列表（G3 验收：clauses.length >= 1）。
     *
     * <p>每步生成 1 条 RULE_TRIGGER 或 FACT_ACCRUAL；step 数 &lt;= 8（兜底裁剪）。</p>
     */
    private List<JustificationClause> buildClauses(List<CausalChainNode> nodes, List<ReasoningStep> steps) {
        List<JustificationClause> clauses = new ArrayList<>();
        int max = Math.min(nodes.size(), 8);
        for (int i = 0; i < max; i++) {
            CausalChainNode node = nodes.get(i);
            ReasoningStep step = steps.get(i);

            String clauseType = JustificationClause.TYPE_FACT_ACCRUAL;
            if (step.getRuleRef() != null) {
                clauseType = JustificationClause.TYPE_RULE_TRIGGER;
            } else if (step.getPrecedentRef() != null) {
                clauseType = JustificationClause.TYPE_PRECEDENT_RECALL;
            }

            JustificationClause clause = new JustificationClause(
                    "clause-" + (i + 1),
                    clauseType,
                    step.getStepId(),
                    node.getDescription(),
                    node.getConfidence()
            );
            clauses.add(clause);
        }
        return clauses;
    }

    /** 从 description 中解析 "precedent:{id} ..." 形式，返回 id；否则 null。 */
    private String extractPrecedentId(String description) {
        if (description == null) return null;
        String t = description.trim();
        if (t.startsWith("precedent:")) {
            String id = t.substring("precedent:".length()).trim();
            int end = id.indexOf(' ');
            return end > 0 ? id.substring(0, end) : id;
        }
        return null;
    }
}
