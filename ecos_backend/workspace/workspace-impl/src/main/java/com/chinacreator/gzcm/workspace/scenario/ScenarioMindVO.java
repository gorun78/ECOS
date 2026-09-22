package com.chinacreator.gzcm.workspace.scenario;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 场景心智变体视图 VO — 列表/详情出参（PMO-60 v2.0 P1）。
 * <p>对应表 {@code ecos_scenario_mind}。</p>
 */
public class ScenarioMindVO {

    /** 主键（BIGINT identity） */
    private Long id;
    /** 所属场景 id */
    private String scenarioId;
    /** 心智标签（base/adverse/...） */
    private String mindLabel;
    /** 是否激活心智（0/1） */
    private Integer activeMind;
    /** 初始信念 JSONB（原始 Map） */
    private Object initialBelief;
    /** 证据引用列表 */
    private Object evidenceRefs;
    /** 假设引用列表 */
    private Object hypothesisRefs;
    /** 模型引用列表 */
    private Object modelRefs;
    /** 认知端点配置 JSONB（原始 Map） */
    private Object cognitiveEndpoints;
    /** 初始置信度 */
    private Double initialConfidence;
    /** 创建时间 */
    private String createTime;
    /** 更新时间 */
    private String updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
    public String getMindLabel() { return mindLabel; }
    public void setMindLabel(String mindLabel) { this.mindLabel = mindLabel; }
    public Integer getActiveMind() { return activeMind; }
    public void setActiveMind(Integer activeMind) { this.activeMind = activeMind; }
    public Object getInitialBelief() { return initialBelief; }
    public void setInitialBelief(Object initialBelief) { this.initialBelief = initialBelief; }
    public Object getEvidenceRefs() { return evidenceRefs; }
    public void setEvidenceRefs(Object evidenceRefs) { this.evidenceRefs = evidenceRefs; }
    public Object getHypothesisRefs() { return hypothesisRefs; }
    public void setHypothesisRefs(Object hypothesisRefs) { this.hypothesisRefs = hypothesisRefs; }
    public Object getModelRefs() { return modelRefs; }
    public void setModelRefs(Object modelRefs) { this.modelRefs = modelRefs; }
    public Object getCognitiveEndpoints() { return cognitiveEndpoints; }
    public void setCognitiveEndpoints(Object cognitiveEndpoints) { this.cognitiveEndpoints = cognitiveEndpoints; }
    public Double getInitialConfidence() { return initialConfidence; }
    public void setInitialConfidence(Double initialConfidence) { this.initialConfidence = initialConfidence; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }

    /**
     * 组装认知四件套的 inline_context（用于 REST 透传到 cognitive-engine）。
     * <p>含三要素 id refs + 四件套开关 + model refs。</p>
     */
    public Map<String, Object> toInlineContext() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("mindId", id);
        ctx.put("mindLabel", mindLabel);
        ctx.put("evidence_refs", evidenceRefs);
        ctx.put("hypothesis_refs", hypothesisRefs);
        ctx.put("model_refs", modelRefs);
        ctx.put("belief", initialBelief);
        ctx.put("cognitive_endpoints", cognitiveEndpoints);
        ctx.put("confidence", initialConfidence);
        return ctx;
    }
}
