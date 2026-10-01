package com.chinacreator.gzcm.engine.kb.model;

/**
 * 合规规则 — 扩展ExpertRule，增加状态管理、前件事实列表、版本追踪。
 *
 * 与 ExpertRule 的关系：
 *   ExpertRule 是通用"专家规则"基类（条件/动作/优先级）
 *   ComplianceRule 增加合规特化字段（审批人、生效日期、关联抽取规则源）
 *
 * <p>F04-18：fromExtractedRule 使用 kb 本地 {@code ExtractedSubGraph.ExtractedRule}
 * （与 ontology 侧同字段镜像，不跨引擎 import）。</p>
 */
public class ComplianceRule extends ExpertRule {

    /** 规则状态: DRAFT → IN_REVIEW → ACTIVE → DEPRECATED → SUPERSEDED */
    private String status;
    /** 需要的事实列表（从业务上下文中提取） */
    private String requiredFactList;
    /** 关联的抽取规则ID（来自 ExtractedRule） */
    private String extractedRuleId;
    /** 审批人 */
    private String approvedBy;
    /** 生效日期 */
    private long effectiveDate;
    /** 过期日期 */
    private long expiryDate;
    /** 版本号 */
    private int version;

    public ComplianceRule() {
        this.status = "DRAFT";
        this.version = 1;
    }

    /** 从提取结果创建（待审核态） */
    public static ComplianceRule fromExtractedRule(ExtractedSubGraph.ExtractedRule er) {
        ComplianceRule r = new ComplianceRule();
        r.setName(er.getName());
        r.setDomain(er.getDomain());
        r.setCondition(er.getCondition());
        r.setAction(er.getAction());
        r.setDescription(er.getSourceExcerpt());
        r.setStatus("IN_REVIEW");
        return r;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequiredFactList() { return requiredFactList; }
    public void setRequiredFactList(String requiredFactList) { this.requiredFactList = requiredFactList; }
    public String getExtractedRuleId() { return extractedRuleId; }
    public void setExtractedRuleId(String extractedRuleId) { this.extractedRuleId = extractedRuleId; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public long getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(long effectiveDate) { this.effectiveDate = effectiveDate; }
    public long getExpiryDate() { return expiryDate; }
    public void setExpiryDate(long expiryDate) { this.expiryDate = expiryDate; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
