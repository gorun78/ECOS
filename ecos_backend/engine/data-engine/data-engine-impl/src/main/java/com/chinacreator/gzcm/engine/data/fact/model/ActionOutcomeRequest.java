package com.chinacreator.gzcm.engine.data.fact.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * W→D 反馈链 action-outcome 写入请求 — 详细设计-02 D.2 writeActionOutcome。
 *
 * <p>V167 五表**无** action/outcome 列（读实证）：本批载体 = 更新
 * {@code update_time/update_by} + 经 {@code ecos.audit} 发布 outcome 载荷。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionOutcomeRequest {

    /** 事实类型（ATTRIBUTION/STAGE/RESOURCE/COST/FORECAST_SNAPSHOT） */
    private String factType;

    /** 事实行 ID（主键 VARCHAR(36)） */
    private String factId;

    /** 处置结论（自由文本，业务口径由上游场景定义） */
    private String outcome;

    /** 备注/说明（可空） */
    private String comment;

    /** 关联批次（可空，便于按批次检索 outcome 审计） */
    private String batchId;

    public String getFactType() { return factType; }
    public void setFactType(String factType) { this.factType = factType; }
    public String getFactId() { return factId; }
    public void setFactId(String factId) { this.factId = factId; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
}
