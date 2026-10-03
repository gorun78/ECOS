package com.chinacreator.gzcm.engine.ontology.dto;

import com.chinacreator.gzcm.engine.ontology.model.CaliberVersion;

/** 口径版本视图（对外只读契约）。 */
public class CaliberVersionVO {
    private String id;
    private String caliberId;
    private String caliberCode;
    private String versionNo;
    private String formula;
    private Integer additive;
    private String periodGranularity;
    private String status;
    private String approvedBy;
    private String approvedAt;
    private String gitRef;
    private String createTime;

    public static CaliberVersionVO from(CaliberVersion v, String caliberCode) {
        CaliberVersionVO vo = new CaliberVersionVO();
        vo.id = v.getId(); vo.caliberId = v.getCaliberId(); vo.caliberCode = caliberCode;
        vo.versionNo = v.getVersionNo(); vo.formula = v.getFormula(); vo.additive = v.getAdditive();
        vo.periodGranularity = v.getPeriodGranularity(); vo.status = v.getStatus();
        vo.approvedBy = v.getApprovedBy();
        vo.approvedAt = v.getApprovedAt() != null ? v.getApprovedAt().toString() : null;
        vo.gitRef = v.getGitRef();
        vo.createTime = v.getCreateTime() != null ? v.getCreateTime().toString() : null;
        return vo;
    }

    public String getId() { return id; }
    public String getCaliberId() { return caliberId; }
    public String getCaliberCode() { return caliberCode; }
    public String getVersionNo() { return versionNo; }
    public String getFormula() { return formula; }
    public Integer getAdditive() { return additive; }
    public String getPeriodGranularity() { return periodGranularity; }
    public String getStatus() { return status; }
    public String getApprovedBy() { return approvedBy; }
    public String getApprovedAt() { return approvedAt; }
    public String getGitRef() { return gitRef; }
    public String getCreateTime() { return createTime; }
}
