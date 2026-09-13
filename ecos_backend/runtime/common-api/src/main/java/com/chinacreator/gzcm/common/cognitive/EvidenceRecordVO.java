package com.chinacreator.gzcm.common.cognitive;

import java.time.LocalDateTime;

/**
 * 认知证据契约（PMO-59 Phase 1 / ADR-9 心智层）。
 * <p>
 * 对齐表 {@code ecos_cognitive_evidence}（V127）与外部方案层1"感知接入层"输出物：
 * 所有内外部信息统一转化为可被引擎消费的结构化证据（来源/可信度/冲突标记）。
 * 跨模块（dccheng ↔ workspace）传递证据摘要时使用本 VO，禁止 Map 裸传。
 */
public class EvidenceRecordVO {

    /** 证据主键（对齐表 id） */
    private String id;
    /** 业务唯一键（幂等登记） */
    private String evidenceCode;
    /** 租户/域隔离（可选） */
    private String tenantScope;
    /** 来源类型: SYSTEM_DATA / NEWS / EXPERT / PIPELINE */
    private String sourceType;
    /** 来源定位（管道ID/URL/人工录入ID） */
    private String sourceRef;
    /** 结构化事实载荷（事实/指标/数值/上下文） */
    private String blob;
    /** 可信度 0~1（系统数据 0.95+ / 权威新闻 ~0.8 / 小道消息 0.3~0.5） */
    private Double confidence;
    /** 同事实多证据不一致 → 冲突待修正标记 */
    private boolean conflict;
    /** 冲突对方证据 id 列表（JSON 数组文本） */
    private String refutingEvidenceIds;
    /** 事实生效时间 */
    private LocalDateTime effectiveTime;
    /** 事实失效时间（可选） */
    private LocalDateTime expireTime;
    /** 状态: ACTIVE / CONFLICTED / ARCHIVED */
    private String status;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEvidenceCode() { return evidenceCode; }
    public void setEvidenceCode(String evidenceCode) { this.evidenceCode = evidenceCode; }
    public String getTenantScope() { return tenantScope; }
    public void setTenantScope(String tenantScope) { this.tenantScope = tenantScope; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
    public String getBlob() { return blob; }
    public void setBlob(String blob) { this.blob = blob; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public boolean isConflict() { return conflict; }
    public void setConflict(boolean conflict) { this.conflict = conflict; }
    public String getRefutingEvidenceIds() { return refutingEvidenceIds; }
    public void setRefutingEvidenceIds(String refutingEvidenceIds) { this.refutingEvidenceIds = refutingEvidenceIds; }
    public LocalDateTime getEffectiveTime() { return effectiveTime; }
    public void setEffectiveTime(LocalDateTime effectiveTime) { this.effectiveTime = effectiveTime; }
    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
