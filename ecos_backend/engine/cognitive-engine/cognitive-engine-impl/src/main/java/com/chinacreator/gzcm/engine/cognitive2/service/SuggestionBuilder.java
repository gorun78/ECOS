package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainResult;
import com.chinacreator.gzcm.engine.cognitive2.model.DiagnosisRequest;
import com.chinacreator.gzcm.runtime.llm.LLMGatewayService;
import com.chinacreator.gzcm.runtime.llm.scheduler.AgentResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM因果推理构建器 — KG覆盖不足时经 llm-gateway 补充因果链。
 *
 * <p>从 CausalReasonerServiceImpl 拆出，职责：LLM提示词构建 + Agent调用 + 响应解析 + 工具方法。
 * <p>F05-15 X-34：LLM 调用唯一通道 = {@code runtime/llm-gateway}（铁律 §2.5-2），
 * 禁自建 HTTP 客户端直连 agent-loop。
 */
@Component
public class SuggestionBuilder {

    private static final Logger log = LoggerFactory.getLogger(SuggestionBuilder.class);

    /** LLM推理置信度范围 */
    static final double LLM_CONFIDENCE_MIN = 0.50;
    static final double LLM_CONFIDENCE_MAX = 0.70;

    /** llm-gateway 归属子系统 / Agent profile（与 kb-nav 同款 profile 语义） */
    private static final String LLM_SUBSYSTEM = "aiming";
    private static final String LLM_PROFILE = "cognitive-causal-supplement";

    private static final String SYSTEM_PREAMBLE =
            "你是一个企业经营诊断专家。根据输入的因果推理提示，分析指标的深层原因，输出JSON格式的因果链。";

    private final ObjectProvider<LLMGatewayService> llmGatewayProvider;
    private final ObjectMapper objectMapper;

    public SuggestionBuilder(ObjectProvider<LLMGatewayService> llmGatewayProvider) {
        this.llmGatewayProvider = llmGatewayProvider;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * KG路径不足时，调用LLM生成补充因果链。
     */
    void llmSupplementChain(CausalChainResult result, DiagnosisRequest request,
                            int currentDepth, int maxDepth) {
        String existingChain = buildExistingChainSummary(result);
        String prompt = buildLlmPrompt(request, existingChain, currentDepth, maxDepth);
        String llmResponse = callLlm(prompt);
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
     * 经 llm-gateway 执行因果补充 Agent（铁律 §2.5-2；失败抛错，由上层 diagnose 链路降级）。
     */
    String callLlm(String prompt) {
        LLMGatewayService gateway = llmGatewayProvider.getIfAvailable();
        if (gateway == null) {
            log.warn("llm-gateway 未装配，因果补充通道不可用");
            throw new RuntimeException("llm-gateway 不可用: LLMGatewayService 未装配");
        }
        try {
            AgentResult result = gateway.execute(LLM_SUBSYSTEM, LLM_PROFILE,
                    SYSTEM_PREAMBLE + "\n\n" + prompt);
            if (result == null) {
                throw new RuntimeException("llm-gateway 返回空结果");
            }
            if (!result.isSuccess()) {
                String msg = result.getErrorMsg() != null ? result.getErrorMsg() : "Agent推理未完成";
                log.warn("llm-gateway Agent推理失败: {}", msg);
                throw new RuntimeException("Agent推理失败: " + msg);
            }
            String content = result.getContent();
            return content != null ? content : "";
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("llm-gateway Agent调用异常: {}", e.getMessage());
            throw new RuntimeException("llm-gateway Agent不可用: " + e.getMessage(), e);
        }
    }

    /**
     * 解析LLM响应，提取因果链节点追加到结果中。
     */
    @SuppressWarnings("unchecked")
    private void parseLlmCausalChain(String llmResponse, CausalChainResult result,
                                     int currentDepth, int maxDepth) {
        try {
            String json = extractJson(llmResponse);
            Map<String, Object> parsed = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            List<Map<String, Object>> chainList = (List<Map<String, Object>>) parsed.get("chain");
            if (chainList != null) {
                for (Map<String, Object> item : chainList) {
                    int depth = getIntValue(item, "depth", currentDepth + 1);
                    String nodeText = (String) item.getOrDefault("node", "未知原因");
                    double conf = getDoubleValue(item, "confidence", LLM_CONFIDENCE_MIN);

                    if (depth > currentDepth && depth <= maxDepth) {
                        CausalChainNode chainNode = new CausalChainNode(depth, nodeText,
                                clampConfidence(conf, LLM_CONFIDENCE_MIN, LLM_CONFIDENCE_MAX),
                                "LLM");
                        result.getCausalChain().add(chainNode);
                    }
                }
            }

            result.setRootCause((String) parsed.getOrDefault("rootCause", null));

            List<String> suggestions = (List<String>) parsed.get("suggestions");
            if (suggestions != null && result.getSuggestions().isEmpty()) {
                result.getSuggestions().addAll(suggestions);
            }

            List<String> affected = (List<String>) parsed.get("affectedMetrics");
            if (affected != null) {
                result.getAffectedMetrics().addAll(affected);
            }

            result.getCausalChain().sort(Comparator.comparingInt(CausalChainNode::getDepth));

        } catch (Exception e) {
            log.warn("解析LLM响应失败: {}; 原始响应前200字符: {}",
                    e.getMessage(),
                    llmResponse.length() > 200 ? llmResponse.substring(0, 200) : llmResponse);
        }
    }

    // ═══════════════════════════════════════════
    //  工具方法（供 RootCauseAnalyzer 复用）
    // ═══════════════════════════════════════════

    /** 从字符串中提取JSON内容（去除markdown标记等）。 */
    String extractJson(String text) {
        if (text == null || text.isEmpty()) return "{}";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf("\n");
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                trimmed = trimmed.substring(start, end).trim();
            }
        }
        int braceIdx = trimmed.indexOf('{');
        int bracketIdx = trimmed.indexOf('[');
        int startIdx = (braceIdx >= 0 && (bracketIdx < 0 || braceIdx < bracketIdx))
                ? braceIdx : bracketIdx;
        if (startIdx >= 0) {
            return trimmed.substring(startIdx);
        }
        return trimmed;
    }

    double clampConfidence(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** 暴露 ObjectMapper 供 RootCauseAnalyzer 复用。 */
    com.fasterxml.jackson.databind.ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultValue;
    }

    double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultValue;
    }
}
