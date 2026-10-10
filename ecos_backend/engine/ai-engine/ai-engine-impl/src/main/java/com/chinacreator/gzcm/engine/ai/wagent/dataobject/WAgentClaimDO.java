package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

import java.math.BigDecimal;

/**
 * V240 {@code ecos_ai.ecos_wagent_claim} 断言 DO（数字一致性校验承载，F10-24）。
 * <p>{@code numeric_value} NUMERIC(18,6)（文档明载精度；断言数值非金额列本体，ST03-A 判定已登记 V240 头注）。
 * {@code value_source} 落实金额唯一事实源纪律——禁 AI 生成金额（卷 09 §4.2 同源）；
 * {@code evidence_grade} CHAR(1)（A/B/C/D 单字符）；{@code verify_status} 为
 * E-WA-NARRATIVE-NUM（500）门禁的校验位。</p>
 * <p>INDEX(run_id, verify_status)；与证据的绑定走 {@link WAgentClaimEvidenceDO}。</p>
 */
public class WAgentClaimDO extends WAgentBaseDO {

    private String id;
    private String runId;
    private String stepId;
    private String claimText;
    private String claimType;
    private BigDecimal numericValue;
    private String numericUnit;
    private String valueSource;
    private String evidenceGrade;
    private String verifyStatus;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getClaimText() { return claimText; }
    public void setClaimText(String claimText) { this.claimText = claimText; }

    public String getClaimType() { return claimType; }
    public void setClaimType(String claimType) { this.claimType = claimType; }

    public BigDecimal getNumericValue() { return numericValue; }
    public void setNumericValue(BigDecimal numericValue) { this.numericValue = numericValue; }

    public String getNumericUnit() { return numericUnit; }
    public void setNumericUnit(String numericUnit) { this.numericUnit = numericUnit; }

    public String getValueSource() { return valueSource; }
    public void setValueSource(String valueSource) { this.valueSource = valueSource; }

    public String getEvidenceGrade() { return evidenceGrade; }
    public void setEvidenceGrade(String evidenceGrade) { this.evidenceGrade = evidenceGrade; }

    public String getVerifyStatus() { return verifyStatus; }
    public void setVerifyStatus(String verifyStatus) { this.verifyStatus = verifyStatus; }
}
