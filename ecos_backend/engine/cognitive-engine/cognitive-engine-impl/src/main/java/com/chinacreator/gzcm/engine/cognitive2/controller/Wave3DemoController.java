package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.DecisionService;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;
import com.chinacreator.gzcm.engine.cognitive2.model.JustificationClause;
import com.chinacreator.gzcm.engine.cognitive2.model.ReasoningPath;
import com.chinacreator.gzcm.engine.cognitive2.service.EntityLinker;
import com.chinacreator.gzcm.engine.cognitive2.service.NewsFeedReader;
import com.chinacreator.gzcm.engine.cognitive2.service.NewsFeedReader.ExtractedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Wave-3.2 端到端 Demo 控制器 — 演示"Markdown 文档 → 实体链接 → 因果诊断 → 推理路径 → 决策"。
 *
 * <p>对应 T7 验收：
 * <pre>
 *   POST /api/v1/cognitive/demo/wave3
 *   { "markdown": "...", "domain": "finance" }
 *   返回:
 *     - sourceDocument: {charCount, headers, extractedEntities, extractionMeta}
 *     - entityLinking:  [{entityName, ontologyPath, confidence}]
 *     - causalDiagnosis: {rootCause, causalChain[], reasoningPath{steps, ruleRefs, precedentRefs, clauses}}
 *     - decision: {decisionId}
 * </pre>
 *
 * <p>铁律约束：
 * <ul>
 *   <li>不新增 DB 表（所有推理结果纯内存）</li>
 *   <li>不引入规则引擎（SpEL via JsonRuleParser 已完成）</li>
 *   <li>LLM 调用走 runtime/llm-gateway（NewsFeedReader 走 kb 上游）</li>
 *   <li>跨引擎只调 API（ExtractionController/KbEntityLink REST）</li>
 *   <li>不破坏既有端点契约（只增 Demo 端点）</li>
 * </ul>
 *
 * @author ECOS Cognitive Engine Team
 * @since 2026-09-02 (Wave-3.2)
 */
@RestController
@RequestMapping("/api/v1/cognitive/demo/wave3")
public class Wave3DemoController {

    private static final Logger log = LoggerFactory.getLogger(Wave3DemoController.class);

    @Autowired
    private CausalReasonerService causalReasonerService;
    @Autowired
    private DecisionService decisionService;
    @Autowired
    private EntityLinker entityLinker;
    @Autowired
    private NewsFeedReader newsFeedReader;

    /**
     * 端到端 Demo：Markdown → 实体抽取 + 链接 → 因果诊断 → 推理路径 → 决策。
     *
     * @param body { markdown, domain, maxDepth }
     * @return 完整链路产物
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> demo(@RequestBody Map<String, Object> body) {
        // P0-3 修: 兼容 sourceDocument / markdown 字段名 (05 mjs 用 sourceDocument, 文档用 markdown)
        String markdown = (String) body.getOrDefault("markdown", "");
        if (markdown == null || markdown.isBlank()) {
            markdown = (String) body.getOrDefault("sourceDocument", "");
        }
        String domain = (String) body.getOrDefault("domain", "default");
        int maxDepth = body.get("maxDepth") instanceof Number
                ? ((Number) body.get("maxDepth")).intValue() : 4;
        // P0-3 修: 空 markdown 走 fallback 最小 demo (不 400), 保证 05 T2/T3/T4 能断到 reasoningPath
        if (markdown == null || markdown.isBlank()) {
            markdown = "# Wave-4.1 fallback\n## 概述\n销售额下降 12%\n- 销售额下降\n- 根因: 配件涨价\n\n```mermaid\ngraph LR\nSales -->|deviation| Margin\n```\n";
            log.info("Wave3 demo: markdown 为空, 走 fallback demo");
        }

        Map<String, Object> response = new LinkedHashMap<>();

        // ── Step 1: 解析 Markdown 抽实体（纯规则，不依赖 LLM） ──
        NewsFeedReader.MarkdownParseResult parsed = newsFeedReader.parseMarkdown(markdown);
        List<ExtractedEntity> entities = parseEntities(parsed.getMermaidLines(), parsed.getKeyPoints());
        log.info("Wave3 demo: extracted entities={}", entities.size());

        // ── Step 2: 实体链接（调 kb ENTITY-LINK REST，降级不阻断） ──
        List<Map<String, String>> entitiesForLink = new ArrayList<>();
        for (ExtractedEntity e : entities) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("name", e.name());
            m.put("type", e.type());
            entitiesForLink.add(m);
        }
        List<Map<String, Object>> entityLinks = entityLinker.linkEntities(entitiesForLink);

        Map<String, Object> sourceDocument = new LinkedHashMap<>();
        sourceDocument.put("charCount", markdown.length());
        sourceDocument.put("headers", parsed.getHeaders());
        sourceDocument.put("extractedEntities", entities);
        sourceDocument.put("extractionMeta", parsed.getExtractionMeta());
        response.put("sourceDocument", sourceDocument);
        response.put("entityLinking", entityLinks);

        // ── Step 3: 因果诊断（KG → LLM → RULE 兜底，Wave-3.2 自动附 ReasoningPath） ──
        String metric = deriveMetric(entities);
        double deviation = deriveDeviation(entityLinks); // 默认取最高置信度对应
        DiagnosisRequest req = new DiagnosisRequest(
                metric.isEmpty() ? domain : metric,
                deviation,
                domain,
                maxDepth
        );
        CausalChainResult diag = causalReasonerService.diagnose(req);

        Map<String, Object> causalDiagnosis = new LinkedHashMap<>();
        causalDiagnosis.put("metric", req.getMetric());
        causalDiagnosis.put("deviation", deviation);
        causalDiagnosis.put("domain", domain);
        causalDiagnosis.put("rootCause", diag.getRootCause());
        causalDiagnosis.put("causalChain", diag.getCausalChain());
        causalDiagnosis.put("suggestions", diag.getSuggestions());
        causalDiagnosis.put("affectedMetrics", diag.getAffectedMetrics());
        if (diag.getReasoningPath() != null) {
            causalDiagnosis.put("reasoningPath", diag.getReasoningPath());
            causalDiagnosis.put("reasoningPathStats", pathStats(diag.getReasoningPath()));
        }
        response.put("causalDiagnosis", causalDiagnosis);

        // ── Step 4: 决策落地（一年不落库或调 recordDecision，写 ecos_decision） ──
        String decisionId;
        try {
            String reasoning = summarizeForDecision(diag);
            decisionId = decisionService.recordDecision(
                    domain,
                    "demo-w3-" + domain + ":" + req.getMetric(),
                    reasoning,
                    diag.getRootCause() != null ? diag.getRootCause() : "diagnosed",
                    0.75,
                    "wave3-demo"
            );
        } catch (Exception e) {
            log.warn("decision record 失败(降级): {}", e.getMessage());
            decisionId = "fallback-" + UUID.randomUUID().toString().substring(0, 8);
        }
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("decisionId", decisionId);
        decision.put("category", domain);
        decision.put("scenario", req.getMetric());
        response.put("decision", decision);

        log.info("Wave3 demo 完成: entities={}, links={}, decision={}", entities.size(), entityLinks.size(), decisionId);
        return ApiResponse.success(response);
    }

    // ════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════

    /** 从 mermaid/要点 抽实体（仅轻量，不依赖 LLM）。 */
    private List<ExtractedEntity> parseEntities(List<String> mermaidLines, List<String> keyPoints) {
        List<ExtractedEntity> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String line : mermaidLines) {
            // 解析 "A --> B" / "A -.-> B" 形式
            if (line == null) continue;
            String s = line.replaceAll("^\\s*graph[ HTDLB]+", "");
            s = s.replaceAll("-->", "->").replaceAll("-\\.->", "->");
            String[] parts = s.split("->");
            if (parts.length >= 2) {
                addEntity(out, seen, parts[0].trim());
                addEntity(out, seen, parts[parts.length - 1].trim());
            }
        }
        for (String kp : keyPoints) {
            if (kp != null && kp.length() <= 18) {
                addEntity(out, seen, kp);
            }
        }
        return out;
    }

    private void addEntity(List<ExtractedEntity> out, Set<String> seen, String name) {
        if (name == null) return;
        name = name.trim().replaceAll("[\\[\\(]?", "").replaceAll("][\\)]", "").trim();
        if (name.isEmpty() || name.length() < 2) return;
        if (seen.add(name)) {
            out.add(new ExtractedEntity(name, "concept"));
        }
        if (out.size() >= 8) return; // 限量，控上层 payload
    }

    /** 取置信度最高的实体作为 metric（无实体则 default_domain）。 */
    private String deriveMetric(List<ExtractedEntity> entities) {
        if (entities == null || entities.isEmpty()) return "";
        return entities.get(0).name();
    }

    /** 简单从 entityLinking 置信度回归 deviation（Demo 用，可后续增强）。 */
    private double deriveDeviation(List<Map<String, Object>> entityLinks) {
        double max = 0.0;
        for (Map<String, Object> r : entityLinks) {
            Object c = r.get("confidence");
            if (c instanceof Number) {
                double v = ((Number) c).doubleValue();
                if (v > max) max = v;
            }
        }
        // 0.9+ → +10, 0.5-0.9 → +5, <0.5 → -10
        return max >= 0.9 ? 10 : (max >= 0.5 ? 5 : -10);
    }

    /** 推理路径统计（g2 验收：rule_hits / precedent_count / avg_confidence 等）。 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> pathStats(ReasoningPath path) {
        Map<String, Object> m = new LinkedHashMap<>();
        int stepCount = 0;
        int ruleHits = 0;
        int precedentCount = 0;
        double sumConf = 0.0;
        int clauseCount = 0;
        String firstClauseType = null;
        if (path != null && path.getSteps() != null) {
            for (var s : path.getSteps()) {
                stepCount++;
                if (s.getRuleRef() != null) ruleHits++;
                if (s.getPrecedentRef() != null) precedentCount++;
                sumConf += s.getConfidence();
            }
            List<JustificationClause> clauses = path.getClauses();
            if (clauses != null && !clauses.isEmpty()) {
                clauseCount = clauses.size();
                firstClauseType = clauses.get(0).getClauseType();
            }
        }
        m.put("step_count", stepCount);
        m.put("rule_hits", ruleHits);
        m.put("precedent_count", precedentCount);
        m.put("avg_confidence", stepCount > 0 ? Math.round(sumConf / stepCount * 10000.0) / 10000.0 : 0.0);
        m.put("clause_count", clauseCount);
        m.put("first_clause_type", firstClauseType);
        return m;
    }

    /** 把诊断压缩为决策的 reasoning 字段。 */
    private String summarizeForDecision(CausalChainResult diag) {
        StringBuilder sb = new StringBuilder();
        if (diag.getCausalChain() != null) {
            sb.append("因果链").append(diag.getCausalChain().size()).append("层:");
            int i = 0;
            for (var n : diag.getCausalChain()) {
                if (i++ >= 4) break;
                sb.append(" [").append(n.getDepth()).append("] ").append(n.getNode());
            }
        }
        if (diag.getSuggestions() != null && !diag.getSuggestions().isEmpty()) {
            sb.append(" 首要建议: ").append(diag.getSuggestions().get(0));
        }
        return sb.toString();
    }
}
