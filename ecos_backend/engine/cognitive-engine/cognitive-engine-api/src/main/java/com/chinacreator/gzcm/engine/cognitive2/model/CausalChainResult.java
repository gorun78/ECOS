package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 因果链诊断结果 — 包含根因、完整因果链路、改进建议和受影响指标。
 * 兼容原合规因果链字段（rootRuleId/nodes/edges），
 * 同时新增业务因果诊断所需字段（rootCause/causalChain/suggestions/affectedMetrics）。
 */
public class CausalChainResult {

    // ── 原有字段（合规因果链） ──
    private String rootRuleId;
    private List<CausalChainNode> nodes;
    private List<CausalEdge> edges;

    // ── 新增字段（业务因果诊断） ──
    /** 根因描述 */
    private String rootCause;
    /** 完整因果链路（含 depth/confidence/source），按深度排序 */
    private List<CausalChainNode> causalChain;
    /** 改进建议列表 */
    private List<String> suggestions;
    /** 受影响的业务指标列表 */
    private List<String> affectedMetrics;

    // ── 构造器 ──
    public CausalChainResult() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.causalChain = new ArrayList<>();
        this.suggestions = new ArrayList<>();
        this.affectedMetrics = new ArrayList<>();
    }

    public CausalChainResult(String rootRuleId) {
        this();
        this.rootRuleId = rootRuleId;
    }

    // ── 原有 getter/setter ──
    public String getRootRuleId() { return rootRuleId; }
    public void setRootRuleId(String rootRuleId) { this.rootRuleId = rootRuleId; }

    public List<CausalChainNode> getNodes() { return nodes; }
    public void setNodes(List<CausalChainNode> nodes) { this.nodes = nodes; }

    public List<CausalEdge> getEdges() { return edges; }
    public void setEdges(List<CausalEdge> edges) { this.edges = edges; }

    // ── 新增 getter/setter ──
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public List<CausalChainNode> getCausalChain() { return causalChain; }
    public void setCausalChain(List<CausalChainNode> causalChain) { this.causalChain = causalChain; }

    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }

    public List<String> getAffectedMetrics() { return affectedMetrics; }
    public void setAffectedMetrics(List<String> affectedMetrics) { this.affectedMetrics = affectedMetrics; }
}
