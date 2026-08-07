package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.CausalReasonerService;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalEdge;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;
import com.chinacreator.gzcm.engine.kb.KnowledgeGraphService;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeEdge;
import com.chinacreator.gzcm.engine.kb.model.KnowledgeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

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
 * <p>依赖：仅依赖 kb-engine-api 接口（KnowledgeGraphService），不直接 import kb-engine-impl。
 */
@Service
public class CausalReasonerServiceImpl implements CausalReasonerService {

    private static final Logger log = LoggerFactory.getLogger(CausalReasonerServiceImpl.class);

    /** KG路径遍历的关系类型 — 因果相关 */
    private static final List<String> CAUSAL_RELATION_TYPES = List.of("CAUSES", "AFFECTS", "CORRELATES");

    /** KG路径置信度基准 */
    private static final double KG_CONFIDENCE_BASE = 0.80;
    /** 每层深度衰减系数 */
    private static final double DEPTH_DECAY = 0.05;

    /** LLM推理置信度范围 */
    private static final double LLM_CONFIDENCE_MIN = 0.50;
    private static final double LLM_CONFIDENCE_MAX = 0.70;

    /** LLM 补全端点（ai-engine） */
    private static final String LLM_COMPLETION_URL = "/api/v1/agents/completion";

    private final KnowledgeGraphService knowledgeGraphService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入。
     *
     * @param knowledgeGraphService 知识图谱服务（kb-engine-api 接口）
     */
    public CausalReasonerServiceImpl(KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.restTemplate = new RestTemplate();
        // 设置连接超时30s，读取超时120s（LLM推理可能较慢）
        this.restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(30_000);
            setReadTimeout(120_000);
        }});
        this.objectMapper = new ObjectMapper();
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
        int kgLastDepth = traverseKgChain(result, request.getMetric(), request.getDomain(),
                maxDepth, 1, visitedNodeIds);

        // ── LLM 补充推理（KG覆盖不足时） ──
        if (kgLastDepth < maxDepth) {
            log.info("KG路径深度={}，不足目标深度={}，启用LLM补充推理", kgLastDepth, maxDepth);
            try {
                llmSupplementChain(result, request, kgLastDepth, maxDepth);
            } catch (Exception e) {
                log.warn("LLM补充推理失败: {}", e.getMessage());
            }
        }

        // ── 根因定位与建议生成 ──
        identifyRootCauseAndSuggestions(result, request);

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
            // 使用 KG 获取该域下的全图数据
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
            // 通过 KG 路径获取最短路径信息作为效应估计依据
            Map<String, Object> pathResult = knowledgeGraphService.getShortestPath(source, target);

            Object lengthObj = pathResult.get("length");
            int pathLength = (lengthObj instanceof Integer) ? (Integer) lengthObj : -1;

            if (pathLength > 0) {
                // 路径越短，因果效应越强；使用衰减公式
                double effect = 1.0 / (1.0 + pathLength);
                log.debug("KG路径长度={}, 因果效应={}", pathLength, effect);
                return effect;
            }

            // KG无路径时，尝试通过诊断推理估计
            // 构建简单诊断请求，分析 source → target 关系
            DiagnosisRequest dr = new DiagnosisRequest(source, 0.0, "", 3);
            CausalChainResult chain = diagnose(dr);

            // 从因果链中提取置信度加权效应
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

    // ══════════════════════════════════════════════════════════════════
    //  私有方法：KG路径遍历
    // ══════════════════════════════════════════════════════════════════

    /**
     * 沿知识图谱逐层遍历因果链。
     *
     * @param result     累积结果（因果链节点追加到 result.causalChain）
     * @param metric     指标名，用于在KG中搜索起始节点
     * @param domain     业务域
     * @param maxDepth   最大深度
     * @param currentDepth 当前深度（起始为1，即指标自身）
     * @param visited    已访问节点ID集合（防环）
     * @return KG遍历覆盖的最大深度（≥currentDepth）
     */
    private int traverseKgChain(CausalChainResult result, String metric, String domain,
                                 int maxDepth, int currentDepth, Set<String> visited) {
        if (currentDepth >= maxDepth) {
            return currentDepth;
        }

        // 搜索KG中匹配的起始节点
        List<KnowledgeNode> startNodes = knowledgeGraphService.search(metric);
        if (startNodes.isEmpty()) {
            log.debug("KG中未搜索到匹配 '{}' 的节点", metric);
            return currentDepth;
        }

        // BFS队列：{nodeId, depth, parentNodeDesc}
        Deque<String[]> queue = new ArrayDeque<>();
        for (KnowledgeNode node : startNodes) {
            if (visited.add(node.getId())) {
                queue.offer(new String[]{node.getId(), String.valueOf(currentDepth + 1),
                        node.getLabel() != null ? node.getLabel() : metric});
            }
        }

        while (!queue.isEmpty() && currentDepth < maxDepth) {
            String[] entry = queue.poll();
            String nodeId = entry[0];
            int depth = Integer.parseInt(entry[1]);
            String parentDesc = entry[2];

            if (depth > maxDepth) continue;

            // 获取邻接节点
            Map<String, Object> neighborResult = knowledgeGraphService.getNeighbors(nodeId, 1);
            @SuppressWarnings("unchecked")
            List<KnowledgeEdge> neighbors = (List<KnowledgeEdge>) neighborResult.get("neighbors");

            if (neighbors == null || neighbors.isEmpty()) continue;

            for (KnowledgeEdge edge : neighbors) {
                // 仅处理因果相关的关系类型
                if (!CAUSAL_RELATION_TYPES.contains(edge.getRelationship().toUpperCase())) {
                    continue;
                }

                String targetId = edge.getTargetNodeId();
                if (!visited.add(targetId)) continue; // 已访问，跳过防环

                // 获取目标节点详情以获取描述
                String nodeDesc = getNodeDescription(targetId);
                double confidence = Math.max(0.35, KG_CONFIDENCE_BASE - (depth - 1) * DEPTH_DECAY);

                CausalChainNode chainNode = new CausalChainNode(depth, nodeDesc, confidence, "KG", domain);
                result.getCausalChain().add(chainNode);

                // 继续向更深层遍历
                if (depth < maxDepth) {
                    queue.offer(new String[]{targetId, String.valueOf(depth + 1), nodeDesc});
                }
            }

            // 更新 currentDepth 为队列中的最大深度
            currentDepth = Math.max(currentDepth, depth);
        }

        return currentDepth;
    }

    /**
     * 获取KG节点描述文本。
     */
    private String getNodeDescription(String nodeId) {
        try {
            Map<String, Object> detail = knowledgeGraphService.getNodeDetail(nodeId);
            if (detail != null) {
                KnowledgeNode node = (KnowledgeNode) detail.get("node");
                if (node != null) {
                    String desc = node.getDescription();
                    if (desc != null && !desc.isEmpty()) return desc;
                    String label = node.getLabel();
                    if (label != null && !label.isEmpty()) return label;
                }
            }
        } catch (Exception e) {
            log.debug("获取节点 {} 详情失败: {}", nodeId, e.getMessage());
        }
        return "节点-" + nodeId.substring(0, Math.min(8, nodeId.length()));
    }

    // ══════════════════════════════════════════════════════════════════
    //  私有方法：LLM 推理补充
    // ══════════════════════════════════════════════════════════════════

    /**
     * KG路径不足时，调用LLM生成补充因果链。
     */
    private void llmSupplementChain(CausalChainResult result, DiagnosisRequest request,
                                     int currentDepth, int maxDepth) {
        // 构建LLM提示词
        String existingChain = buildExistingChainSummary(result);
        String prompt = buildLlmPrompt(request, existingChain, currentDepth, maxDepth);

        // 调用LLM
        String llmResponse = callLlm(prompt);

        // 解析LLM响应，提取因果链节点
        parseLlmCausalChain(llmResponse, result, currentDepth, maxDepth);
    }

    /**
     * 构建已有链路的摘要文本，供LLM理解当前推理上下文。
     */
    private String buildExistingChainSummary(CausalChainResult result) {
        return result.getCausalChain().stream()
                .map(n -> String.format("  [depth=%d, source=%s] %s (confidence=%.2f)",
                        n.getDepth(), n.getSource(), n.getNode(), n.getConfidence()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 构建LLM提示词 — 要求输出JSON格式的因果链。
     */
    private String buildLlmPrompt(DiagnosisRequest request, String existingChain,
                                   int currentDepth, int maxDepth) {
        String direction = request.getDeviation() >= 0 ? "上升" : "下降";
        return String.format(
                "你是一名业务因果分析专家。请分析以下指标偏差的深层因果链。\n\n" +
                "## 当前指标\n" +
                "- 指标: %s\n" +
                "- 偏差: %s%.0f%%\n" +
                "- 业务域: %s\n\n" +
                "## 已知因果链路（来自知识图谱）\n" +
                "%s\n\n" +
                "## 任务\n" +
                "请补齐从深度%d到深度%d的因果链节点，分析导致该指标变化的根本原因链。\n" +
                "只输出JSON，格式如下（不要markdown标记）：\n" +
                "{\n" +
                "  \"chain\": [\n" +
                "    {\"depth\": 2, \"node\": \"描述\", \"confidence\": 0.65},\n" +
                "    {\"depth\": 3, \"node\": \"描述\", \"confidence\": 0.55}\n" +
                "  ],\n" +
                "  \"rootCause\": \"最终根因描述\",\n" +
                "  \"suggestions\": [\"建议1\", \"建议2\", \"建议3\"],\n" +
                "  \"affectedMetrics\": [\"指标1\", \"指标2\"]\n" +
                "}\n\n" +
                "要求：因果链至少%d层，每层节点简洁明确，根因要有业务可操作性。",
                request.getMetric(), direction, Math.abs(request.getDeviation()),
                request.getDomain(), existingChain,
                currentDepth + 1, maxDepth, maxDepth);
    }

    /**
     * 调用LLM补全端点。
     */
    private String callLlm(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prompt", prompt);
        body.put("maxTokens", 2048);
        body.put("temperature", 0.3); // 低温度保证推理一致性

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            String url = "http://localhost:8080" + LLM_COMPLETION_URL;
            // 也尝试 ai-engine 独立端口
            String response;
            try {
                response = restTemplate.postForObject(url, request, String.class);
            } catch (Exception e1) {
                log.debug("Gateway端点不可用，尝试ai-engine直接端口: {}", e1.getMessage());
                url = "http://localhost:18084" + LLM_COMPLETION_URL;
                response = restTemplate.postForObject(url, request, String.class);
            }
            return response != null ? response : "";
        } catch (Exception e) {
            log.warn("LLM调用失败: {}", e.getMessage());
            throw new RuntimeException("LLM服务不可用: " + e.getMessage());
        }
    }

    /**
     * 解析LLM响应，提取因果链节点追加到结果中。
     */
    @SuppressWarnings("unchecked")
    private void parseLlmCausalChain(String llmResponse, CausalChainResult result,
                                      int currentDepth, int maxDepth) {
        try {
            // 尝试JSON解析
            String json = extractJson(llmResponse);
            Map<String, Object> parsed = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            // 解析因果链
            List<Map<String, Object>> chainList = (List<Map<String, Object>>) parsed.get("chain");
            if (chainList != null) {
                for (Map<String, Object> item : chainList) {
                    int depth = getIntValue(item, "depth", currentDepth + 1);
                    String nodeText = (String) item.getOrDefault("node", "未知原因");
                    double conf = getDoubleValue(item, "confidence", LLM_CONFIDENCE_MIN);

                    // 限制深度范围
                    if (depth > currentDepth && depth <= maxDepth) {
                        CausalChainNode chainNode = new CausalChainNode(depth, nodeText,
                                clampConfidence(conf, LLM_CONFIDENCE_MIN, LLM_CONFIDENCE_MAX),
                                "LLM");
                        result.getCausalChain().add(chainNode);
                    }
                }
            }

            // 存储LLM输出到临时字段（最终由 identifyRootCauseAndSuggestions 统一处理）
            result.setRootCause((String) parsed.getOrDefault("rootCause", null));

            List<String> suggestions = (List<String>) parsed.get("suggestions");
            if (suggestions != null && result.getSuggestions().isEmpty()) {
                result.getSuggestions().addAll(suggestions);
            }

            List<String> affected = (List<String>) parsed.get("affectedMetrics");
            if (affected != null) {
                result.getAffectedMetrics().addAll(affected);
            }

            // 按深度排序因果链
            result.getCausalChain().sort(Comparator.comparingInt(CausalChainNode::getDepth));

        } catch (Exception e) {
            log.warn("解析LLM响应失败: {}; 原始响应前200字符: {}",
                    e.getMessage(),
                    llmResponse.length() > 200 ? llmResponse.substring(0, 200) : llmResponse);
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  私有方法：根因定位与建议
    // ══════════════════════════════════════════════════════════════════

    /**
     * 从因果链中定位根因，生成改进建议。
     */
    private void identifyRootCauseAndSuggestions(CausalChainResult result, DiagnosisRequest request) {
        List<CausalChainNode> chain = result.getCausalChain();

        // ── 根因定位：取最深层的KG节点，其次LLM节点 ──
        if (result.getRootCause() == null || result.getRootCause().isEmpty()) {
            // 找到最深层的节点作为根因候选
            Optional<CausalChainNode> deepestKg = chain.stream()
                    .filter(n -> "KG".equals(n.getSource()))
                    .max(Comparator.comparingInt(CausalChainNode::getDepth));

            Optional<CausalChainNode> deepestAny = chain.stream()
                    .max(Comparator.comparingInt(CausalChainNode::getDepth));

            if (deepestKg.isPresent()) {
                result.setRootCause(deepestKg.get().getNode());
            } else if (deepestAny.isPresent() && !"metric".equals(deepestAny.get().getSource())) {
                result.setRootCause(deepestAny.get().getNode());
            } else {
                // 因果链不足时，用LLM生成根因
                tryLlGenerateRootCause(result, request);
            }
        }

        // ── 建议生成：优先KG叶子节点推导，否则LLM生成 ──
        if (result.getSuggestions().isEmpty()) {
            tryLlGenerateSuggestions(result, request);
        }

        // ── 受影响指标收集 ──
        if (result.getAffectedMetrics().isEmpty()) {
            // 从因果链中提取涉及的业务指标
            Set<String> metrics = new LinkedHashSet<>();
            metrics.add(request.getMetric().replaceAll("[^a-zA-Z_]", "").toLowerCase());

            for (CausalChainNode node : chain) {
                if (!"metric".equals(node.getSource())) {
                    // 简单启发式：提取节点描述中的指标关键词
                    String nodeText = node.getNode();
                    if (nodeText.contains("营收") || nodeText.contains("收入")) metrics.add("revenue");
                    if (nodeText.contains("成本")) metrics.add("cost");
                    if (nodeText.contains("利润") || nodeText.contains("毛利")) metrics.add("gross_margin");
                    if (nodeText.contains("客户")) metrics.add("customer_concentration");
                    if (nodeText.contains("订单")) metrics.add("order_volume");
                    if (nodeText.contains("供应") || nodeText.contains("库存")) metrics.add("supply_chain");
                }
            }

            result.getAffectedMetrics().addAll(metrics);
        }
    }

    /**
     * 通过LLM生成根因描述。
     */
    private void tryLlGenerateRootCause(CausalChainResult result, DiagnosisRequest request) {
        String chainSummary = result.getCausalChain().stream()
                .map(n -> "  层" + n.getDepth() + ": " + n.getNode())
                .collect(Collectors.joining("\n"));

        String prompt = String.format(
                "基于以下因果链，用一句话概括根本原因：\n\n%s\n\n" +
                "只输出根因描述（一句话，不要标记）：",
                chainSummary);

        try {
            String response = callLlm(prompt);
            if (response != null && !response.isEmpty()) {
                result.setRootCause(response.trim());
            }
        } catch (Exception e) {
            // 回退：取最深节点
            result.getCausalChain().stream()
                    .max(Comparator.comparingInt(CausalChainNode::getDepth))
                    .ifPresent(n -> result.setRootCause(n.getNode()));
        }
    }

    /**
     * 通过LLM生成改进建议列表。
     */
    private void tryLlGenerateSuggestions(CausalChainResult result, DiagnosisRequest request) {
        String chainSummary = result.getCausalChain().stream()
                .map(n -> "  层" + n.getDepth() + ": " + n.getNode())
                .collect(Collectors.joining("\n"));

        String rootCause = result.getRootCause() != null ? result.getRootCause() : "未知";

        String prompt = String.format(
                "针对指标「%s」的偏差和根因「%s」，给出3-5条具体改进建议。\n\n" +
                "因果链:\n%s\n\n" +
                "只输出JSON数组（不要markdown标记）：\n" +
                "[\"建议1\", \"建议2\", \"建议3\"]",
                request.getMetric(), rootCause, chainSummary);

        try {
            String response = callLlm(prompt);
            String json = extractJson(response);
            List<String> suggestions = objectMapper.readValue(json,
                    new TypeReference<List<String>>() {});
            if (suggestions != null) {
                result.getSuggestions().addAll(suggestions);
            }
        } catch (Exception e) {
            log.warn("LLM生成建议失败: {}", e.getMessage());
            // 默认建议
            result.getSuggestions().add("深入分析" + request.getMetric() + "的波动原因");
            result.getSuggestions().add("监控关键相关指标变化趋势");
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  工具方法
    // ══════════════════════════════════════════════════════════════════

    /**
     * 从字符串中提取JSON内容（去除markdown标记等）。
     */
    private String extractJson(String text) {
        if (text == null || text.isEmpty()) return "{}";
        String trimmed = text.trim();
        // 去除 markdown ```json ... ``` 包裹
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n");
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                trimmed = trimmed.substring(start, end).trim();
            }
        }
        // 查找第一个 { 或 [
        int braceIdx = trimmed.indexOf('{');
        int bracketIdx = trimmed.indexOf('[');
        int startIdx = (braceIdx >= 0 && (bracketIdx < 0 || braceIdx < bracketIdx))
                ? braceIdx : bracketIdx;
        if (startIdx >= 0) {
            return trimmed.substring(startIdx);
        }
        return trimmed;
    }

    private double clampConfidence(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultValue;
    }

    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultValue;
    }
}
