package com.chinacreator.gzcm.engine.data.fact.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * W→D 反馈链 action-outcome 写入请求 — 详细设计-02 D.2 writeActionOutcome。
 *
 * <p><b>载体决策记录（卷09 v1.2 §十一 H-3 交付②，"加列 vs 加表"）= 加表</b>：
 * V167 五表无 action/outcome 列；若加列则回灌必须 UPDATE 事实行，与 PUBLISHED 行
 * 禁 UPDATE 守卫（ECOS-DATA-021，已实装）直接矛盾，且一个 action 可多次回灌 outcome
 * 属 1:N，只能靠追加行（append-only）承载。裁定落
 * {@code ecos_dw.ecos_application_action_outcome}（V279，业务域，data-engine 唯一写通道，
 * ADR-14/ST08）；旧载体形态（触达列 + ecos.audit 事件）保留不回改（只增不改）。
 *
 * <p>V279 未应用的环境：写入自动回退旧形态（审计事件 + 触达列）并 WARN——
 * 由 Service 层 mapper 在位性判定，非静默伪造成功。
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

    // ── H-3 交付②增块（V279 载体列；全部可空 = 兼容旧调用方只传五字段的形态）──

    /** 行动/建议业务键（可空；与 forecastRunId 至少一空值即可查侧检索） */
    private String actionId;

    /** 关联预测运行业务键（可空=非预测链回灌；V197 forecast_run_id 同形） */
    private String forecastRunId;

    /** 归属期间 YYYY-MM（检索维度，非关账键；可空） */
    private String period;

    /** 预期影响金额 NUMERIC(18,2)（V279 ST03-A 登记列；可空） */
    private BigDecimal expectedImpact;

    /** 实际影响金额 NUMERIC(18,2)（V279 ST03-A 登记列；可空；与预期成对=复盘读数） */
    private BigDecimal actualImpact;

    /** 币种（可空，缺省 CNY；禁隐式换算，值原样落列） */
    private String currency;

    /** 回灌幂等键（可空；命中 uniq_eaao_idem 重复提交返回首写行，不产生第二行） */
    private String idempotencyKey;

    /** 扩展上下文（受控 JSON 字符串，仅描述用；落 detail_json TEXT，MC02） */
    private String detailJson;

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
    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }
    public String getForecastRunId() { return forecastRunId; }
    public void setForecastRunId(String forecastRunId) { this.forecastRunId = forecastRunId; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public BigDecimal getExpectedImpact() { return expectedImpact; }
    public void setExpectedImpact(BigDecimal expectedImpact) { this.expectedImpact = expectedImpact; }
    public BigDecimal getActualImpact() { return actualImpact; }
    public void setActualImpact(BigDecimal actualImpact) { this.actualImpact = actualImpact; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
}
