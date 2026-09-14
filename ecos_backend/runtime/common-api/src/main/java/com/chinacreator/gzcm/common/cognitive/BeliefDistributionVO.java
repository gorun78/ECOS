package com.chinacreator.gzcm.common.cognitive;

import java.util.ArrayList;
import java.util.List;

/**
 * 不确定性判断契约（PMO-59 Phase 1 / ADR-9 心智层 P 库）。
 * <p>
 * 对齐表 {@code ecos_cognitive_belief}（V129）与外部方案层3.2：
 * 对不可观测经营变量维护**有限离散概率分布** + 新证据加权更新 + 人工覆写（专家干预）。
 * 术语说明：业务方口径统一称"不确定性判断"（原稿"信念"已改称，避心理学歧义）。
 * {@code snapshotVersion} 为时间回放（历史决策同参数重算，Phase 3）预留。
 */
public class BeliefDistributionVO {

    /** 主键（对齐表 id） */
    private String id;
    /** 不可观测经营变量名（如 competitor_price_cut_prob） */
    private String variableName;
    /** 租户/域隔离（可选） */
    private String tenantScope;
    /** 业务域（pricing / supply-chain / demand） */
    private String domain;
    /** 有限离散概率分布（prob 和 = 1 约定） */
    private List<OutcomeProb> distribution = new ArrayList<>();
    /** 分布版本（每次证据加权更新 +1） */
    private int version;
    /** 时间回放预留（Phase 3: 历史决策同参数重算引用此版本） */
    private Integer snapshotVersion;
    /** 人工覆写标记（专家干预优先于模型更新） */
    private boolean manualOverride;
    /** 人工覆写理由 */
    private String overrideReason;
    /** 触发本次版本更新的证据 id（引用 {@code ecos_cognitive_evidence.id}，可溯源） */
    private String lastEvidenceId;
    /** 状态: ACTIVE / ARCHIVED */
    private String status;

    /** 概率分布点：离散取值 + 概率（对齐 JSONB 结构 {"outcome","prob"}） */
    public static class OutcomeProb {
        /** 离散取值（如 none / mild / aggressive） */
        private String outcome;
        /** 概率 0~1（分布内各项和 = 1） */
        private double prob;

        public OutcomeProb() {
        }

        public OutcomeProb(String outcome, double prob) {
            this.outcome = outcome;
            this.prob = prob;
        }

        public String getOutcome() { return outcome; }
        public void setOutcome(String outcome) { this.outcome = outcome; }
        public double getProb() { return prob; }
        public void setProb(double prob) { this.prob = prob; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getVariableName() { return variableName; }
    public void setVariableName(String variableName) { this.variableName = variableName; }
    public String getTenantScope() { return tenantScope; }
    public void setTenantScope(String tenantScope) { this.tenantScope = tenantScope; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public List<OutcomeProb> getDistribution() { return distribution; }
    public void setDistribution(List<OutcomeProb> distribution) { this.distribution = distribution; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Integer getSnapshotVersion() { return snapshotVersion; }
    public void setSnapshotVersion(Integer snapshotVersion) { this.snapshotVersion = snapshotVersion; }
    public boolean isManualOverride() { return manualOverride; }
    public void setManualOverride(boolean manualOverride) { this.manualOverride = manualOverride; }
    public String getOverrideReason() { return overrideReason; }
    public void setOverrideReason(String overrideReason) { this.overrideReason = overrideReason; }
    public String getLastEvidenceId() { return lastEvidenceId; }
    public void setLastEvidenceId(String lastEvidenceId) { this.lastEvidenceId = lastEvidenceId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
