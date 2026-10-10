package com.chinacreator.gzcm.engine.ai.wagent.dataobject;

/**
 * V231 {@code ecos_ai.ecos_wagent_readiness_item} 就绪评估检查项明细 DO（逐 check_code，REQ-WAG-08）。
 * <p>阈值快照 {@code threshold_json} 来源 sysman 配置单源（config_group='wagent.readiness'，禁硬编码，R-66 同族纪律）。</p>
 */
public class WAgentReadinessItemDO extends WAgentBaseDO {

    private String id;
    private String assessmentId;
    private String layer;
    private String checkCode;
    private String status;
    private String measuredJson;
    private String thresholdJson;
    private String evidenceIdsJson;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAssessmentId() { return assessmentId; }
    public void setAssessmentId(String assessmentId) { this.assessmentId = assessmentId; }

    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }

    public String getCheckCode() { return checkCode; }
    public void setCheckCode(String checkCode) { this.checkCode = checkCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMeasuredJson() { return measuredJson; }
    public void setMeasuredJson(String measuredJson) { this.measuredJson = measuredJson; }

    public String getThresholdJson() { return thresholdJson; }
    public void setThresholdJson(String thresholdJson) { this.thresholdJson = thresholdJson; }

    public String getEvidenceIdsJson() { return evidenceIdsJson; }
    public void setEvidenceIdsJson(String evidenceIdsJson) { this.evidenceIdsJson = evidenceIdsJson; }
}
