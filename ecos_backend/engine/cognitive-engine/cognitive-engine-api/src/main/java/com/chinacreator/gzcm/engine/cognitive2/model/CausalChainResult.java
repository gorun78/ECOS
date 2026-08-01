package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 因果链查询结果 — 包含节点和边。
 */
public class CausalChainResult {

    private String rootRuleId;
    private List<CausalChainNode> nodes;
    private List<CausalEdge> edges;

    public CausalChainResult() {
        this.nodes = new ArrayList<>();
        this.edges = new ArrayList<>();
    }

    public CausalChainResult(String rootRuleId) {
        this();
        this.rootRuleId = rootRuleId;
    }

    public String getRootRuleId() { return rootRuleId; }
    public void setRootRuleId(String rootRuleId) { this.rootRuleId = rootRuleId; }

    public List<CausalChainNode> getNodes() { return nodes; }
    public void setNodes(List<CausalChainNode> nodes) { this.nodes = nodes; }

    public List<CausalEdge> getEdges() { return edges; }
    public void setEdges(List<CausalEdge> edges) { this.edges = edges; }
}
