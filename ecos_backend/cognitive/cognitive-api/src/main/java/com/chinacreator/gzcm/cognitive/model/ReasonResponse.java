package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.List;

/**
 * 推理响应 — 规则匹配或因果路径结果。
 */
public class ReasonResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 推理模式：rule / causal */
    private String mode;

    // ── 规则推理字段 ──
    /** 匹配的规则列表 */
    private List<MatchedRule> matchedRules;
    /** 推理路径描述 */
    private List<String> reasoningPath;

    // ── 因果推理字段 ──
    /** 根因列表 */
    private List<RootCause> rootCauses;
    /** 因果路径列表 */
    private List<CausalPath> causalPaths;
    /** 访问的知识图谱节点数 */
    private Integer kgNodesVisited;

    /** 推理耗时 (毫秒) */
    private Long elapsedMs;

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public List<MatchedRule> getMatchedRules() { return matchedRules; }
    public void setMatchedRules(List<MatchedRule> matchedRules) { this.matchedRules = matchedRules; }
    public List<String> getReasoningPath() { return reasoningPath; }
    public void setReasoningPath(List<String> reasoningPath) { this.reasoningPath = reasoningPath; }
    public List<RootCause> getRootCauses() { return rootCauses; }
    public void setRootCauses(List<RootCause> rootCauses) { this.rootCauses = rootCauses; }
    public List<CausalPath> getCausalPaths() { return causalPaths; }
    public void setCausalPaths(List<CausalPath> causalPaths) { this.causalPaths = causalPaths; }
    public Integer getKgNodesVisited() { return kgNodesVisited; }
    public void setKgNodesVisited(Integer kgNodesVisited) { this.kgNodesVisited = kgNodesVisited; }
    public Long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(Long elapsedMs) { this.elapsedMs = elapsedMs; }
}
