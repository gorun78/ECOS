package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.math.BigDecimal;

/**
 * V240 {@code ecos_ai.ecos_wagent_claim_evidence} 断言×证据关联 DO。
 * <p><b>复合主键 {@code PRIMARY KEY(claim_id, evidence_id)}，无独立 id 列</b>（卷 10 §6.2 V240 明载）。
 * 通用列仍随 §6.1 模板（tenant_id/org_id/domain/version_no/审计四列/is_deleted）。</p>
 * <p>{@code relation} VARCHAR(8)（SUPPORTS/CONTRADICTS/DERIVES 等，值域未定版不设 CHECK，R-15）；
 * {@code cover_ratio} NUMERIC(5,4) 覆盖率。证据主权在 {@link WAgentEvidenceDO} 表（ST09 无 FK）。</p>
 */
public class WAgentClaimEvidenceDO extends WAgentBaseDO {

    private String claimId;
    private String evidenceId;
    private String relation;
    private BigDecimal coverRatio;

    public String getClaimId() { return claimId; }
    public void setClaimId(String claimId) { this.claimId = claimId; }

    public String getEvidenceId() { return evidenceId; }
    public void setEvidenceId(String evidenceId) { this.evidenceId = evidenceId; }

    public String getRelation() { return relation; }
    public void setRelation(String relation) { this.relation = relation; }

    public BigDecimal getCoverRatio() { return coverRatio; }
    public void setCoverRatio(BigDecimal coverRatio) { this.coverRatio = coverRatio; }
}
