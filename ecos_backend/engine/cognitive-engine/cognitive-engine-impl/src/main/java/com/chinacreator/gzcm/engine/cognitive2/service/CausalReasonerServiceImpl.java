package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalEdge;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;
import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 因果推理服务实现 — KG路径遍历 + LLM推理补充，构建≥3层因果链。
 *
 * <p>核心流程：
 * <ol>
 *   <li>在知识图谱中搜索指标对应节点，沿 CAUSES/AFFECTS/CORRELATES 关系逐层遍历</li>
 *   <li>KG覆盖不足时，调用LLM生成补充因果路径</li>
 *   <li>定位根因节点，生成改进建议和受影响指标列表</li>
 * </ol>
 *
 * <p>职责拆分（PMO-C3）：
 * <ul>
 *   <li>{@link CausalDetector} — KG因果链遍历</li>
 *   <li>{@link SuggestionBuilder} — LLM推理 + 提示词构建 + 响应解析</li>
 *   <li>{@link RootCauseAnalyzer} — 根因定位 + 建议生成 + 规则引擎兜底</li>
 * </ul>
 *
 * <p>依赖：仅依赖 kb-engine-api 接口（KnowledgeGraphService），不直接 import kb-engine-impl。
 */
@Service
public class CausalReasonerServiceImpl implements CausalReasonerService {

    private static final Logger log = LoggerFactory.getLogger(CausalReasonerServiceImpl.class);

    private final KnowledgeGraphService knowledgeGraphService;
    private final CausalDetector causalDetector;
    private final SuggestionBuilder suggestionBuilder;
    private final RootCauseAnalyzer rootCauseAnalyzer;

    /**
     * 构造器注入。
     *
     * @param knowledgeGraphService 知识图谱服务（kb-engine-api 接口）
     * @param causalDetector        KG因果链遍历器
     * @param suggestionBuilder     LLM推理构建器
     * @param rootCauseAnalyzer     根因分析器
     */
    public CausalReasonerServiceImpl(KnowledgeGraphService knowledgeGraphService,
                                      CausalDetector causalDetector,
                                      SuggestionBuilder suggestionBuilder,
                                      RootCauseAnalyzer rootCauseAnalyzer) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.causalDetector = causalDetector;
        this.suggestionBuilder = suggestionBuilder;
        this.rootCauseAnalyzer = rootCauseAnalyzer;
    }

    // ══════════════════════════════════════════════════════════════════
    //  核心方法：diagnose — 深层因果诊断
    // ══════════════════════════════════════════════════════════════════

    @Override
    public CausalChainResult diagnose(DiagnosisRequest request) {
        log.info("== 因果诊断开始: metric={}, deviation={}, domain={}, maxDepth={}",
                request.getMetric(), request.getDeviation(), request.getDomain(), request.getMaxDepth());

        CausalChainResult result = new CausalChainResult();

        // ── 第1层：指标自身节点 ──
        String metricDesc = request.getMetric() + (request.getDeviation() != 0
                ? String.format(" (%.0f%%)", request.getDeviation())
                : "");
        CausalChainNode rootNode = new CausalChainNode(1, metricDesc, 1.0, "metric", request.getDomain());
        result.getCausalChain().add(rootNode);

        int maxDepth = Math.max(3, request.getMaxDepth()); // 至少遍历3层

        // ── KG 路径遍历（从第2层开始） ──
        Set<String> visitedNodeIds = new HashSet<>();
        int kgLastDepth = causalDetector.traverseKgChain(result, request.getMetric(), request.getDomain(),
                maxDepth, 1, visitedNodeIds);

        // ── LLM 补充推理（KG覆盖不足时） ──
        if (kgLastDepth < maxDepth) {
            log.info("KG路径深度={}，不足目标深度={}，启用LLM补充推理", kgLastDepth, maxDepth);
            try {
                suggestionBuilder.llmSupplementChain(result, request, kgLastDepth, maxDepth);
            } catch (Exception e) {
                log.warn("LLM补充推理失败: {}", e.getMessage());
            }
        }

        // ── 规则引擎兜底（KG+LLM均空时，用领域知识生成因果链） ──
        if (result.getCausalChain().size() < 3) {
            log.info("因果链不足3层(size={})，启用规则引擎兜底", result.getCausalChain().size());
            rootCauseAnalyzer.ruleBasedExpansion(result, request, maxDepth);
        }

        // ── 根因定位与建议生成 ──
        rootCauseAnalyzer.identifyRootCauseAndSuggestions(result, request);

        log.info("== 因果诊断完成: 因果链长度={}, 根因={}, 建议数={}",
                result.getCausalChain().size(),
                result.getRootCause(),
                result.getSuggestions().size());

        return result;
    }

    // ══════════════════════════════════════════════════════════════════
    //  保留方法：inferCausalGraph — 推断因果图
    // ══════════════════════════════════════════════════════════════════

    @Override
    public List<CausalEdge> inferCausalGraph(String domain) {
        log.info("推断因果图: domain={}", domain);
        List<CausalEdge> edges = new ArrayList<>();

        try {
            Map<String, Object> graph = knowledgeGraphService.getGraph(domain);
            @SuppressWarnings("unchecked")
            List<KnowledgeEdge> kgEdges = (List<KnowledgeEdge>) graph.get("edges");

            if (kgEdges != null) {
                for (KnowledgeEdge kgEdge : kgEdges) {
                    CausalEdge edge = new CausalEdge();
                    edge.setId(kgEdge.getId());
                    edge.setSourceNode(kgEdge.getSourceNodeId());
                    edge.setTargetNode(kgEdge.getTargetNodeId());
                    edge.setWeight(kgEdge.getWeight());
                    edge.setDescription(kgEdge.getRelationship());
                    edges.add(edge);
                }
            }
        } catch (Exception e) {
            log.warn("KG图查询失败，返回空图: {}", e.getMessage());
        }

        return edges;
    }

    // ══════════════════════════════════════════════════════════════════
    //  保留方法：estimateCausalEffect — 因果效应估计
    // ══════════════════════════════════════════════════════════════════

    @Override
    public double estimateCausalEffect(String source, String target) {
        log.info("估计因果效应: {} -> {}", source, target);

        try {
            Map<String, Object> pathResult = knowledgeGraphService.getShortestPath(source, target);

            Object lengthObj = pathResult.get("length");
            int pathLength = (lengthObj instanceof Integer) ? (Integer) lengthObj : -1;

            if (pathLength > 0) {
                double effect = 1.0 / (1.0 + pathLength);
                log.debug("KG路径长度={}, 因果效应={}", pathLength, effect);
                return effect;
            }

            // KG无路径时，尝试通过诊断推理估计
            DiagnosisRequest dr = new DiagnosisRequest(source, 0.0, "", 3);
            CausalChainResult chain = diagnose(dr);

            if (!chain.getCausalChain().isEmpty()) {
                double avgConfidence = chain.getCausalChain().stream()
                        .mapToDouble(CausalChainNode::getConfidence)
                        .average()
                        .orElse(0.5);
                return Math.round(avgConfidence * 100.0) / 100.0;
            }

        } catch (Exception e) {
            log.warn("因果效应估计失败: {}", e.getMessage());
        }

        return 0.5;
    }
}
