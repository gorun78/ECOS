package com.chinacreator.gzcm.workspace.scenario;

import java.util.List;

/**
 * 场景心智 PATCH 部分更新 DTO — 所有字段全 nullable（PATCH 语义，只更新非 null 字段）。
 * <p>替代 ScenarioMindsController.updateMind 原 Map&lt;String,Object&gt; 入参（P0-3 ClassCast 修复）。
 * 与 MindSaveDTO 字段相同但语义区分：MindSaveDTO 用于 upsert（POST），本 DTO 用于 partial update（PATCH）。
 * 不使用 Lombok，手写 getter/setter（与 workspace-impl 既有风格一致）。
 */
public class MindUpdatePartialDTO {

    /** 心智标签（可选，缺省时回退已有 mind 的 label） */
    private String mindLabel;

    /** 初始信念（JSONB，可选） */
    private Object initialBelief;

    /** 证据引用 id 列表（可选） */
    private List<String> evidenceIds;

    /** 假设引用 id 列表（可选） */
    private List<String> hypothesisIds;

    /** 模型引用 id 列表（可选） */
    private List<String> modelIds;

    /** 认知端点配置（JSONB，可选） */
    private Object cognitiveEndpoints;

    /** 初始置信度（可选，0~1） */
    private Double initialConfidence;

    /** 是否激活（可选，0/1） */
    private Integer activeMind;

    public String getMindLabel() { return mindLabel; }
    public void setMindLabel(String mindLabel) { this.mindLabel = mindLabel; }
    public Object getInitialBelief() { return initialBelief; }
    public void setInitialBelief(Object initialBelief) { this.initialBelief = initialBelief; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public void setEvidenceIds(List<String> evidenceIds) { this.evidenceIds = evidenceIds; }
    public List<String> getHypothesisIds() { return hypothesisIds; }
    public void setHypothesisIds(List<String> hypothesisIds) { this.hypothesisIds = hypothesisIds; }
    public List<String> getModelIds() { return modelIds; }
    public void setModelIds(List<String> modelIds) { this.modelIds = modelIds; }
    public Object getCognitiveEndpoints() { return cognitiveEndpoints; }
    public void setCognitiveEndpoints(Object cognitiveEndpoints) { this.cognitiveEndpoints = cognitiveEndpoints; }
    public Double getInitialConfidence() { return initialConfidence; }
    public void setInitialConfidence(Double initialConfidence) { this.initialConfidence = initialConfidence; }
    public Integer getActiveMind() { return activeMind; }
    public void setActiveMind(Integer activeMind) { this.activeMind = activeMind; }
}
