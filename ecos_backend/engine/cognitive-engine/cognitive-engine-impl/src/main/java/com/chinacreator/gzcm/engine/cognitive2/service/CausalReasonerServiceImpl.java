package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalEdge;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;
import com.chinacreator.gzcm.engine.cognitive2.model.PrecedentRef;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath;
import com.chinacreator.gzcm.engine.cognitive2.model.RuleRef;
import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.model.ComplianceRule;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.repository.ComplianceRuleMapper;
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
    /** Wave-3.2 增量：因果链 → ReasoningPath 构建器 */
    private final ReasoningPathFromCausalBuilder reasoningPathFromCausalBuilder;
    /** Wave-3.2 增量：先例召回（PMO-32 复用，不写 DB） */
    private final PrecedentRecaller precedentRecaller;
    /** Wave-3.2 增量：RuleRef 收口（从 KB 读取规则 + 去重） */
    private final RuleRefCollector ruleRefCollector;
    /** Wave-3.2 增量：KB 合规规则源（用于给 ruleRef 补充 version 信息） */
    private final ComplianceRuleMapper ruleMapper;

    /**
     * 构造器注入。
     *
     * @param knowledgeGraphService              知识图谱服务（kb-engine-api 接口）
     * @param causalDetector                     KG因果链遍历器
     * @param suggestionBuilder                  LLM推理构建器
     * @param rootCauseAnalyzer                  根因分析器
     * @param reasoningPathFromCausalBuilder     因果链 → ReasoningPath 转换器
     * @param precedentRecaller                  先例召回器（PMO-32 复用）
     * @param ruleRefCollector                   RuleRef 收口器（KB 规则 → 去重索引）
     * @param ruleMapper                         KB 合规规则源
     */
    public CausalReasonerServiceImpl(KnowledgeGraphService knowledgeGraphService,
                                      CausalDetector causalDetector,
                                      SuggestionBuilder suggestionBuilder,
                                      RootCauseAnalyzer rootCauseAnalyzer,
                                      ReasoningPathFromCausalBuilder reasoningPathFromCausalBuilder,
                                      PrecedentRecaller precedentRecaller,
                                      RuleRefCollector ruleRefCollector,
                                      ComplianceRuleMapper ruleMapper) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.causalDetector = causalDetector;
        this.suggestionBuilder = suggestionBuilder;
        this.rootCauseAnalyzer = rootCauseAnalyzer;
        this.reasoningPathFromCausalBuilder = reasoningPathFromCausalBuilder;
        this.precedentRecaller = precedentRecaller;
        this.ruleRefCollector = ruleRefCollector;
        this.ruleMapper = ruleMapper;
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

        // ── Wave-3.2 增量：把因果链 → ReasoningPath（含 RuleRef + PrecedentRef） ──
        buildAndAttachReasoningPath(result, request, maxDepth);

        log.info("== 因果诊断完成: 因果链长度={}, 根因={}, 建议数={}, reasoningPath.steps={}",
                result.getCausalChain().size(),
                result.getRootCause(),
                result.getSuggestions().size(),
                result.getReasoningPath() != null ? result.getReasoningPath().getSteps().size() : 0);

        return result;
    }

    /**
     * 把因果链 + 先例召回 + KB 规则 → ReasoningPath，附到 CausalChainResult。
     *
     * <p>Wave-3.2 增量：
     * <ol>
     *   <li>T2: 调 PrecedentRecaller.recall 拿 topK=3 先例 → precedentRefs 索引</li>
     *   <li>T4: 读 KB 规则 (ruleMapper.findByDomain) → RuleRef 去重索引</li>
     *   <li>T1: 用 ReasoningPathFromCausalBuilder 转 step（支持 RULE/PRECEDENT 两类引用）</li>
     *   <li>T5: 调 RuleRefCollector 把 rule_hits/precedent_count 写进 justification</li>
     * </ol>
     *
     * @param result    CausalChainResult
     * @param request   DiagnosisRequest
     * @param maxDepth  目标层数（>0 时裁剪）
     */
    private void buildAndAttachReasoningPath(CausalChainResult result, DiagnosisRequest request, int maxDepth) {
        // T2: 先例召回（场景文本 = 指标 + 偏差 + 域）
        String scenario = request.getMetric() + " 偏差 " + (int) ((Math.abs(request.getDeviation()) * 100) / 100) + "%"
                + " 业务域 " + request.getDomain();
        List<PrecedentRef> precedentRefs;
        try {
            precedentRefs = precedentRecaller.recall(scenario, request.getDomain(), PrecedentRecaller.DEFAULT_TOP_K);
        } catch (Exception e) {
            log.warn("先例召回失败(降级): {}", e.getMessage());
            precedentRefs = Collections.emptyList();
        }

        // T4: KB 规则 → RuleRef 去重索引
        Map<String, RuleRef> ruleRefIndex;
        try {
            List<ComplianceRule> kbRules = ruleMapper.findByDomain(request.getDomain());
            if (kbRules == null || kbRules.isEmpty()) {
                kbRules = ruleMapper.findAll();
            }
            ruleRefIndex = ruleRefCollector.toIndex(kbRules);
        } catch (Exception e) {
            log.warn("KB规则读取失败(降级): {}", e.getMessage());
            ruleRefIndex = Collections.emptyMap();
        }

        // T1 + T5: 因果链 → ReasoningPath
        ReasoningPathFromCausalBuilder.Context ctx =
                new ReasoningPathFromCausalBuilder.Context(ruleRefIndex, precedentRecaller.toIndex(precedentRefs));
        String conclusion = result.getRootCause() != null ? result.getRootCause() : "诊断完成";
        ReasoningPath path = reasoningPathFromCausalBuilder.buildPath(
                result.getCausalChain(), conclusion, ctx, maxDepth > 0 ? maxDepth : 8);
        ruleRefCollector.attachStructuralCount(path);
        result.setReasoningPath(path);
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
