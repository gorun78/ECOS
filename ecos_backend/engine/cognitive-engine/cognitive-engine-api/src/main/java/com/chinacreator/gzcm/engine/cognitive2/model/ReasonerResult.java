package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KAG Reasoner 推理结果。
 * 包含答案、子问题分解、使用的检索策略、置信度、来源标注和耗时。
 */
public class ReasonerResult {

    /** 推理答案（自然语言） */
    private String answer;

    /** 分解的子问题列表 */
    private List<String> subQueries;

    /** 使用的检索策略及其结果摘要 */
    private Map<String, Object> retrievalStrategies;

    /** 置信度 [0.0, 1.0] */
    private double confidence;

    /** 推理链（可选）：ruleId → condition → facts → conclusion → source */
    private List<Map<String, String>> reasoningChain;

    /** PMO-35: 结构化推理路径（逐步可解释） */
    private ReasoningPath reasoningPath;

    /** 来源标注列表 */
    private List<Map<String, Object>> sources;

    /** 总耗时（毫秒） */
    private long elapsedMs;

    public ReasonerResult() {
        this.subQueries = new ArrayList<>();
        this.retrievalStrategies = new LinkedHashMap<>();
        this.reasoningChain = new ArrayList<>();
        this.sources = new ArrayList<>();
    }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<String> getSubQueries() { return subQueries; }
    public void setSubQueries(List<String> subQueries) { this.subQueries = subQueries; }

    public Map<String, Object> getRetrievalStrategies() { return retrievalStrategies; }
    public void setRetrievalStrategies(Map<String, Object> retrievalStrategies) { this.retrievalStrategies = retrievalStrategies; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public List<Map<String, String>> getReasoningChain() { return reasoningChain; }
    public void setReasoningChain(List<Map<String, String>> reasoningChain) { this.reasoningChain = reasoningChain; }

    public ReasoningPath getReasoningPath() { return reasoningPath; }
    public void setReasoningPath(ReasoningPath reasoningPath) { this.reasoningPath = reasoningPath; }

    public List<Map<String, Object>> getSources() { return sources; }
    public void setSources(List<Map<String, Object>> sources) { this.sources = sources; }

    public long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(long elapsedMs) { this.elapsedMs = elapsedMs; }

    /** 便捷构造：成功结果 */
    public static ReasonerResult success(String answer, List<String> subQueries,
                                          Map<String, Object> strategies, double confidence) {
        ReasonerResult r = new ReasonerResult();
        r.setAnswer(answer);
        r.setSubQueries(subQueries);
        r.setRetrievalStrategies(strategies);
        r.setConfidence(confidence);
        return r;
    }
}
