package com.chinacreator.gzcm.common.cognitive;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 认知假设契约（PMO-59 Phase 1 / ADR-9 心智层 H 库）。
 * <p>
 * 对齐表 {@code ecos_cognitive_hypothesis}（V128）与外部方案层3.1"假设管理模块"：
 * 每条假设结构化存储（证据引用/valid/失效时间），核心能力为证据触发"假设失效"
 * 后自动下游推演作废 + 告警（Phase 2 实现）。
 */
public class HypothesisVO {

    /** 假设主键（对齐表 id） */
    private String id;
    /** 业务唯一键（幂等登记） */
    private String hypothesisCode;
    /** 租户/域隔离（可选） */
    private String tenantScope;
    /** 假设陈述（业务语言） */
    private String statement;
    /** 业务域（如 supply-chain / pricing） */
    private String domain;
    /** 关联经营变量（对齐 {@code ecos_cognitive_belief.variable_name}） */
    private String metricRef;
    /** 支撑证据 id 列表（引用 {@code ecos_cognitive_evidence.id}） */
    private List<String> evidenceIds = new ArrayList<>();
    /** 当前是否有效（监测命中失效 → false） */
    private boolean valid;
    /** 失效时间（监测命中时写） */
    private LocalDateTime invalidAt;
    /** 失效原因（触发证据/冲突说明） */
    private String invalidReason;
    /** 状态: VALID / INVALIDATED / ARCHIVED */
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getHypothesisCode() { return hypothesisCode; }
    public void setHypothesisCode(String hypothesisCode) { this.hypothesisCode = hypothesisCode; }
    public String getTenantScope() { return tenantScope; }
    public void setTenantScope(String tenantScope) { this.tenantScope = tenantScope; }
    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getMetricRef() { return metricRef; }
    public void setMetricRef(String metricRef) { this.metricRef = metricRef; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public void setEvidenceIds(List<String> evidenceIds) { this.evidenceIds = evidenceIds; }
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public LocalDateTime getInvalidAt() { return invalidAt; }
    public void setInvalidAt(LocalDateTime invalidAt) { this.invalidAt = invalidAt; }
    public String getInvalidReason() { return invalidReason; }
    public void setInvalidReason(String invalidReason) { this.invalidReason = invalidReason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
