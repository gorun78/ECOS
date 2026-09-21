package com.chinacreator.gzcm.workspace.scenario;

import java.util.List;

/**
 * 场景心智保存 DTO — 创建/更新心智变体入参（PMO-60 v2.0 P1）。
 * <p>不使用 Lombok，手写 getter/setter（与 workspace-impl 既有风格一致）。</p>
 */
public class MindSaveDTO {

    /** 心智标签（可选，缺省 base；同一场景内唯一，由 uq_mind_scenario_label 保证） */
    private String mindLabel;

    /** 初始信念（JSONB：{variable, states, prob[]}） */
    private Object initialBelief;

    /** 证据引用 id 列表（可选） */
    private List<String> evidenceIds;

    /** 假设引用 id 列表（可选） */
    private List<String> hypothesisIds;

    /** 模型引用 id 列表（可选） */
    private List<String> modelIds;

    /** 认知端点配置（JSONB：{diagnose:{enabled,weight},forecast:{...},simulate:{...},policy:{...}}） */
    private Object cognitiveEndpoints;

    /** 初始置信度（可选，0~1，缺省 0.5） */
    private Double initialConfidence;

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
}
