package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 因果链节点 — 代表因果诊断链路中的一个环节。
 * 兼容原合规因果链字段（id/ruleId/ruleName/domain/description），
 * 同时新增业务因果诊断所需字段（depth/node/confidence/source）。
 */
public class CausalChainNode {

    // ── 原有字段（合规因果链） ──
    private String id;
    private String ruleId;
    private String ruleName;
    private String domain;
    private String description;

    // ── 新增字段（业务因果诊断） ──
    /** 节点在因果链中的深度（从1开始，1=观测指标本身） */
    private int depth;
    /** 节点描述文本，等价于 description，用于 JSON 序列化输出为 "node" */
    private String node;
    /** 置信度 0.0~1.0 */
    private double confidence;
    /** 数据来源：metric（指标自身）/ KG（知识图谱）/ LLM（大模型推理） */
    private String source;

    // ── 构造器 ──
    public CausalChainNode() {}

    public CausalChainNode(String id, String ruleId, String ruleName, String domain, String description) {
        this.id = id;
        this.ruleId = ruleId;
        this.ruleName = ruleName;
        this.domain = domain;
        this.description = description;
    }

    /**
     * 业务因果诊断构造器
     */
    public CausalChainNode(int depth, String node, double confidence, String source) {
        this.depth = depth;
        this.node = node;
        this.description = node;
        this.confidence = confidence;
        this.source = source;
    }

    /**
     * 全字段构造器
     */
    public CausalChainNode(int depth, String node, double confidence, String source, String domain) {
        this.depth = depth;
        this.node = node;
        this.description = node;
        this.confidence = confidence;
        this.source = source;
        this.domain = domain;
    }

    // ── 原有 getter/setter ──
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // ── 新增 getter/setter ──
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public String getNode() { return node; }
    public void setNode(String node) { this.node = node; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
