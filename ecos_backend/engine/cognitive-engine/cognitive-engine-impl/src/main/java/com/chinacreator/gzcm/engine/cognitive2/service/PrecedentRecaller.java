package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.DecisionService;
import com.chinacreator.gzcm.engine.cognitive2.model.Decision;
import com.chinacreator.gzcm.engine.cognitive2.model.PrecedentRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 先例召回器 — 调用 DecisionService.findSimilarDecisions 把历史相似决策转成 PrecedentRef（Wave-3.2 T2）。
 *
 * <p>对齐 04 文档 §三 #2 + §六 PMO-32 映射：
 * <ul>
 *   <li>输入: 决策场景文本（如"销售同比下降12%"）+ 业务域 + topK</li>
 *   <li>输出: PrecedentRef 列表（每条带 similarityEvidence.vectorScore 默认 -1）</li>
 *   <li>降级: DecisionService 不可用时返回空列表，不阻断业务</li>
 * </ul>
 *
 * <p>调用方: CausalReasonerServiceImpl.diagnose() 在因果链≥3层后调用本服务，
 * 将 top-K 相似先例带回 CausalChainResult 的 reasoningPath（通过 ReasoningPathFromCausalBuilder.Context）。</p>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-3.2)
 */
@Component
public class PrecedentRecaller {

    private static final Logger log = LoggerFactory.getLogger(PrecedentRecaller.class);

    /** 默认 topK */
    public static final int DEFAULT_TOP_K = 3;

    /** 最小相似度阈值（先例<0.5 视为噪声，过滤掉） */
    private static final double MIN_SIMILARITY = 0.5;

    private final DecisionService decisionService;

    public PrecedentRecaller(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    /**
     * 召回相似先例。
     *
     * @param scenario 场景文本（诊断描述）
     * @param domain   业务域
     * @param topK     返回条数（<=0 用默认 3）
     * @return PrecedentRef 列表（按相似度降序）
     */
    public List<PrecedentRef> recall(String scenario, String domain, int topK) {
        if (scenario == null || scenario.isBlank()) {
            return Collections.emptyList();
        }
        int k = topK > 0 ? Math.max(topK, 1) : DEFAULT_TOP_K;
        try {
            // 先用 domain 检索（DecisionService.findSimilarDecisions 按 ILIKE 模式匹配）
            List<Decision> similar = decisionService.findSimilarDecisions(scenario, k);
            if (similar == null || similar.isEmpty()) {
                log.debug("no similar precedent for scenario={}", scenario);
                return Collections.emptyList();
            }

            List<PrecedentRef> refs = new ArrayList<>();
            for (Decision d : similar) {
                if (d == null || d.getId() == null) continue;
                PrecedentRef ref = PrecedentRef.fromDecision(d, -1.0 /* vectorScore 暂不可用 */);
                // 二次过滤：domain 命中时给显式标识
                if (domain != null && domain.equals(d.getCategory())) {
                    ref.getSimilarityEvidence().put("category", d.getCategory());
                    ref.getSimilarityEvidence().put("domain_match", true);
                }
                Map<String, Object> evidence = ref.getSimilarityEvidence();
                if (evidence == null) {
                    evidence = new LinkedHashMap<>();
                    ref.setSimilarityEvidence(evidence);
                }
                evidence.putIfAbsent("vector_score", -1);
                refs.add(ref);
            }

            // 过滤相似度<阈值的（无向量时 similarity=-1 全部跳过）
            List<PrecedentRef> filtered = new ArrayList<>();
            for (PrecedentRef r : refs) {
                if (r.getSimilarity() < MIN_SIMILARITY) {
                    // 无向量分数时不过滤，保留 domain_match 命中的
                    if (r.getSimilarityEvidence() != null
                            && Boolean.TRUE.equals(r.getSimilarityEvidence().get("domain_match"))) {
                        filtered.add(r);
                    }
                } else {
                    filtered.add(r);
                }
            }
            log.debug("recall precedent for scenario=[{}] domain=[{}] k={} returned={}",
                    scenario, domain, k, filtered.size());
            return filtered;
        } catch (Exception e) {
            // 降级策略：先例召回失败不阻断主流程
            log.warn("Precedent recall failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从 PrecedentRef 列表构建索引（供 ReasoningPathFromCausalBuilder.Context 使用）。
     *
     * @param refs 先例列表（可空）
     * @return precedentId → PrecedentRef
     */
    public Map<String, PrecedentRef> toIndex(List<PrecedentRef> refs) {
        Map<String, PrecedentRef> idx = new LinkedHashMap<>();
        if (refs == null) return idx;
        for (PrecedentRef r : refs) {
            if (r != null && r.getPrecedentId() != null) {
                idx.put(r.getPrecedentId(), r);
            }
        }
        return idx;
    }
}
