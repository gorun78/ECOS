package com.chinacreator.gzcm.engine.data.model;

import java.math.BigDecimal;

/**
 * 行动结果回灌行（详细设计-09 v1.2 §十一 H-3 交付②；表 V279 {@code ecos_dw.ecos_application_action_outcome}）。
 *
 * <p>用途：{@code GET /api/v1/datanet/facts/action-outcomes?forecastRunId=|actionId=} 响应元素。
 * "预期影响 vs 实际影响"可查询载体（expectedImpact / actualImpact 成对，差值即复盘读数）。
 *
 * <p>金额列 ST03-A 逐列登记见 V279 脚本与本撮交付报告（NUMERIC(18,2) 明文 + RLS/CLS/mask 承接）。
 */
public class ActionOutcomeVO {

    /** 回灌行主键（MC01 UUID）。 */
    private String outcomeId;

    /** 关联预测运行业务键（可空=非预测链回灌）。 */
    private String forecastRunId;

    /** 行动/建议业务键（可空）。 */
    private String actionId;

    /** 关联事实类型（ATTRIBUTION/STAGE/RESOURCE/COST/FORECAST_SNAPSHOT）。 */
    private String factType;

    /** 关联事实行主键。 */
    private String factId;

    /** 归属期间 YYYY-MM（检索维度，非关账键）。 */
    private String period;

    /** 处置结论。 */
    private String outcome;

    /** 备注/说明。 */
    private String comment;

    /** 预期影响金额（NUMERIC(18,2)，可空）。 */
    private BigDecimal expectedImpact;

    /** 实际影响金额（NUMERIC(18,2)，可空）。 */
    private BigDecimal actualImpact;

    /** 币种。 */
    private String currency;

    /** 关联批次。 */
    private String batchId;

    /** 回灌幂等键（可空）。 */
    private String idempotencyKey;

    /** 首写操作者（create_by）。 */
    private String createBy;

    /** 首写时点（ISO_LOCAL_DATE_TIME 字符串）。 */
    private String createTime;

    public String getOutcomeId() { return outcomeId; }
    public void setOutcomeId(String outcomeId) { this.outcomeId = outcomeId; }
    public String getForecastRunId() { return forecastRunId; }
    public void setForecastRunId(String forecastRunId) { this.forecastRunId = forecastRunId; }
    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }
    public String getFactType() { return factType; }
    public void setFactType(String factType) { this.factType = factType; }
    public String getFactId() { return factId; }
    public void setFactId(String factId) { this.factId = factId; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public BigDecimal getExpectedImpact() { return expectedImpact; }
    public void setExpectedImpact(BigDecimal expectedImpact) { this.expectedImpact = expectedImpact; }
    public BigDecimal getActualImpact() { return actualImpact; }
    public void setActualImpact(BigDecimal actualImpact) { this.actualImpact = actualImpact; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}
