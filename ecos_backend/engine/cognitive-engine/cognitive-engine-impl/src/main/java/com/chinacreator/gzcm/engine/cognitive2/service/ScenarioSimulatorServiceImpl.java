package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.ScenarioSimulatorService;
import com.chinacreator.gzcm.engine.cognitive2.model.*;
import com.chinacreator.gzcm.engine.kb.KnowledgeRetrievalService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * What-if 场景推演服务实现。
 *
 * <p>推演流程：
 * <ol>
 *   <li>从知识库拉取当前本体对象状态作为基线</li>
 *   <li>构造 LLM prompt（基线 + 变量变更）</li>
 *   <li>调用 Agent completion 端点获取预测</li>
 *   <li>对比基线生成 Δ 值 + 趋势方向</li>
 *   <li>综合 LLM 确定性 + KG 覆盖度计算置信度</li>
 * </ol>
 */
@Service
public class ScenarioSimulatorServiceImpl implements ScenarioSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioSimulatorServiceImpl.class);

    private final KnowledgeRetrievalService retrievalService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ecos.agent.completion.url:http://localhost:8080/api/v1/agent-loop/chat}")
    private String agentCompletionUrl;

    public ScenarioSimulatorServiceImpl(KnowledgeRetrievalService retrievalService) {
        this.retrievalService = retrievalService;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // ──────────── 原有接口保持兼容 ────────────

    @Override
    public SimulationResult runSimulation(Scenario scenario) {
        log.info("runSimulation deprecated path — delegating to simulate() for: {}", scenario.getName());

        SimRequest request = new SimRequest();
        request.setName(scenario.getName());
        request.setDomain(scenario.getType() != null ? scenario.getType().name() : "default");
        // Scenario.assumptions 作为初始变量
        Map<String, String> vars = new LinkedHashMap<>();
        if (scenario.getAssumptions() != null) {
            scenario.getAssumptions().forEach((k, v) -> vars.put(k, String.valueOf(v)));
        }
        request.setVariables(vars);

        return simulate(request);
    }

    // ──────────── 核心推演方法 ────────────

    /**
     * What-if 场景推演。
     *
     * @param request 场景名称 + 变量变更集 + 业务域
     * @return 推演结果（基线 / 预测 / Δ值 / 趋势 / 置信度 / 假设）
     */
    public SimulationResult simulate(SimRequest request) {
        log.info("Starting what-if simulation: name={}, domain={}, variables={}",
                request.getName(), request.getDomain(), request.getVariables());

        SimulationResult result = new SimulationResult();
        result.setStatus(SimulationStatus.RUNNING);
        result.setSummary("Simulating: " + request.getName());

        try {
            // ── 1. 基线获取：从知识库拉取当前本体对象状态 ──
            Map<String, Object> baseline = fetchBaseline(request.getDomain());
            result.setBaseline(baseline);
            log.info("Baseline fetched: {} keys, domain={}", baseline.size(), request.getDomain());

            // ── 2. LLM 预测 ──
            Map<String, Object> llmResponse = callAgentCompletion(baseline, request);
            result.setPredicted(extractPredicted(llmResponse));
            @SuppressWarnings("unchecked")
            List<String> assumptions = (List<String>) llmResponse.getOrDefault("assumptions",
                    Collections.emptyList());
            result.setAssumptions(assumptions);

            // ── 3. 对比基线 → Δ 值 + 趋势 ──
            computeDeltasAndTrends(result);

            // ── 4. 置信度计算 ──
            result.setConfidence(computeConfidence(llmResponse, baseline));
            result.setStatus(SimulationStatus.COMPLETED);

        } catch (Exception e) {
            log.error("Simulation failed for: {}", request.getName(), e);
            result.setStatus(SimulationStatus.FAILED);
            result.setSummary("Simulation failed: " + e.getMessage());
        }

        return result;
    }

    // ──────────── 基线获取 ────────────

    /**
     * 通过 RAG 检索知识库，提取当前业务域的本体对象状态作为基线。
     */
    private Map<String, Object> fetchBaseline(String domain) {
        Map<String, Object> ragResult = retrievalService.ragQuery(domain, 3, 0.5);

        Map<String, Object> baseline = new LinkedHashMap<>();

        // 尝试从 RAG 结果中提取结构化指标
        // ragQuery 返回格式: { "documents": [...], "query": "...", ... }
        // 每篇文档可能包含结构化字段
        Object documents = ragResult.get("documents");
        if (documents instanceof List) {
            for (Object doc : (List<?>) documents) {
                if (doc instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> docMap = (Map<String, Object>) doc;
                    extractNumericFields(docMap, baseline);
                }
            }
        }

        // 也尝试从顶层直接提取数值字段
        extractNumericFields(ragResult, baseline);

        if (baseline.isEmpty()) {
            log.warn("No numeric baseline extracted from RAG for domain={}, using empty baseline", domain);
        }

        return baseline;
    }

    /**
     * 递归提取 Map 中的数值字段（整型/浮点型），以 key 路径作为指标名。
     */
    @SuppressWarnings("unchecked")
    private void extractNumericFields(Map<String, Object> source, Map<String, Object> target) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Number) {
                target.put(key, ((Number) value).doubleValue());
            } else if (value instanceof Map) {
                extractNumericFields((Map<String, Object>) value, target);
            }
        }
    }

    // ──────────── LLM Agent 调用 ────────────

    /**
     * 调用 ai-engine Agent Loop 进行 What-if 推演预测。
     */
    private Map<String, Object> callAgentCompletion(Map<String, Object> baseline,
                                                     SimRequest request) {
        String prompt = buildPrompt(baseline, request);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", prompt);
        payload.put("systemPrompt", "你是一个业务推演引擎。根据当前业务基线和变量变更，预测变化后的业务指标。只输出JSON。");
        payload.put("temperature", 0.3);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        log.debug("POST {} with prompt length={}", agentCompletionUrl, prompt.length());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    agentCompletionUrl,
                    HttpMethod.POST,
                    entity,
                    Map.class);

            if (response.getBody() == null) {
                log.warn("Agent返回空body，使用降级预测");
                return buildFallbackResponse(baseline, request);
            }

            // 解析 ApiResponse 包装，提取 data.content
            @SuppressWarnings("unchecked")
            Map<String, Object> respBody = response.getBody();
            Object data = respBody.get("data");
            if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> dataMap = (Map<String, Object>) data;
                Object content = dataMap.get("content");
                if (content != null) {
                    log.info("Agent推演完成: content长度={}", content.toString().length());
                    // 包装为统一格式返回
                    Map<String, Object> wrapped = new LinkedHashMap<>();
                    wrapped.put("predicted", content.toString());
                    wrapped.put("llmConfidence", 0.65);
                    return wrapped;
                }
            }

            log.info("Agent completion OK: keys={}", respBody.keySet());
            return respBody;
        } catch (Exception e) {
            log.warn("ai-engine Agent调用失败，使用降级预测: {}", e.getMessage());
            return buildFallbackResponse(baseline, request);
        }
    }

    /**
     * 构造 What-if 推演 prompt。
     */
    private String buildPrompt(Map<String, Object> baseline, SimRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个业务推演引擎。根据当前业务基线和变量变更，预测变化后的业务指标。\n\n");

        sb.append("## 当前基线\n");
        if (baseline.isEmpty()) {
            sb.append("（无可用基线数据）\n");
        } else {
            baseline.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }

        sb.append("\n## 变量变更\n");
        if (request.getVariables() != null && !request.getVariables().isEmpty()) {
            request.getVariables().forEach((k, v) ->
                    sb.append("- ").append(k).append(": ").append(v).append("\n"));
        } else {
            sb.append("（无变量变更）\n");
        }

        sb.append("\n## 业务域\n");
        sb.append(request.getDomain()).append("\n\n");

        sb.append("请返回JSON格式（只返回JSON，不要其他文字）：\n");
        sb.append("{\n");
        sb.append("  \"predicted\": { \"指标1\": 数值, ... },\n");
        sb.append("  \"assumptions\": [\"假设1\", \"假设2\", ...],\n");
        sb.append("  \"llmConfidence\": 0.0到1.0之间的确定性评分\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 当 Agent 端点不可用时，基于简单规则生成回退预测。
     */
    private Map<String, Object> buildFallbackResponse(Map<String, Object> baseline,
                                                       SimRequest request) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        Map<String, Object> predicted = new LinkedHashMap<>();

        // 简单规则：对基线中的每个数值应用变量变更百分比
        if (request.getVariables() != null) {
            for (Map.Entry<String, String> var : request.getVariables().entrySet()) {
                String varName = var.getKey();
                String change = var.getValue();

                // 尝试在基线中找到匹配的指标
                for (Map.Entry<String, Object> baseEntry : baseline.entrySet()) {
                    if (baseEntry.getKey().contains(varName) || varName.contains(baseEntry.getKey())) {
                        double baseVal = ((Number) baseEntry.getValue()).doubleValue();
                        double multiplier = parseChangeMultiplier(change);
                        predicted.put(baseEntry.getKey(), Math.round(baseVal * multiplier * 100.0) / 100.0);
                    }
                }
            }
        }

        fallback.put("predicted", predicted);
        fallback.put("assumptions", List.of("规则推演（Agent端点不可用）"));
        fallback.put("llmConfidence", 0.3);
        return fallback;
    }

    /**
     * 解析变更字符串（如 "+10%", "-5%", "不变"）为乘数。
     */
    private double parseChangeMultiplier(String change) {
        if (change == null || change.isEmpty()) return 1.0;
        String trimmed = change.trim().replace("%", "");
        if ("不变".equals(trimmed)) return 1.0;
        try {
            double pct = Double.parseDouble(trimmed);
            return 1.0 + pct / 100.0;
        } catch (NumberFormatException e) {
            return 1.0;
        }
    }

    // ──────────── 响应解析 ────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPredicted(Map<String, Object> llmResponse) {
        Object predicted = llmResponse.get("predicted");
        if (predicted instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) predicted);
        }
        // 尝试从 "data" 字段提取
        Object data = llmResponse.get("data");
        if (data instanceof Map) {
            Map<String, Object> dataMap = (Map<String, Object>) data;
            Object dataPredicted = dataMap.get("predicted");
            if (dataPredicted instanceof Map) {
                return new LinkedHashMap<>((Map<String, Object>) dataPredicted);
            }
        }
        log.warn("Could not extract 'predicted' from LLM response, keys: {}", llmResponse.keySet());
        return new LinkedHashMap<>();
    }

    // ──────────── Δ 值与趋势计算 ────────────

    /**
     * 对比基线与预测值，计算 Δ 绝对变化和趋势方向。
     */
    private void computeDeltasAndTrends(SimulationResult result) {
        Map<String, Object> baseline = result.getBaseline();
        Map<String, Object> predicted = result.getPredicted();

        Map<String, Object> deltas = new LinkedHashMap<>();
        Map<String, Object> trends = new LinkedHashMap<>();

        if (baseline == null) baseline = Collections.emptyMap();
        if (predicted == null) predicted = Collections.emptyMap();

        for (Map.Entry<String, Object> entry : predicted.entrySet()) {
            String key = entry.getKey();
            Object predVal = entry.getValue();
            Object baseVal = baseline.get(key);

            if (predVal instanceof Number && baseVal instanceof Number) {
                double p = ((Number) predVal).doubleValue();
                double b = ((Number) baseVal).doubleValue();
                double delta = Math.round((p - b) * 100.0) / 100.0;

                deltas.put(key, delta);

                if (Math.abs(delta) < 0.01) {
                    trends.put(key, "stable");
                } else if (delta > 0) {
                    trends.put(key, "up");
                } else {
                    trends.put(key, "down");
                }
            }
        }

        result.setDeltas(deltas);
        result.setTrends(trends);
    }

    // ──────────── 置信度计算 ────────────

    /**
     * 综合 LLM 返回的确定性 + 知识图谱覆盖度计算置信度。
     */
    private double computeConfidence(Map<String, Object> llmResponse,
                                      Map<String, Object> baseline) {
        double llmConf = 0.5; // 默认中等置信度

        Object llmConfRaw = llmResponse.get("llmConfidence");
        if (llmConfRaw instanceof Number) {
            llmConf = ((Number) llmConfRaw).doubleValue();
        }

        // KG 覆盖度：基线数据越丰富，置信度越高
        double kgCoverage = baseline.isEmpty() ? 0.3 : Math.min(1.0, baseline.size() / 10.0);

        // 综合：LLM 确定性权重 0.7，KG 覆盖度权重 0.3
        double confidence = llmConf * 0.7 + kgCoverage * 0.3;

        return Math.round(confidence * 100.0) / 100.0;
    }
}
