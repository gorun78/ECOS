package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;

/**
 * 计划来源追溯。
 */
public class PlanSource implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源类型：reason / optimize / manual */
    private String type;
    /** 关联的推理结果 ID（source=reason 时必填） */
    private String reasonId;
    /** 关联的优化解 ID（source=optimize 时必填） */
    private String solutionId;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getReasonId() { return reasonId; }
    public void setReasonId(String reasonId) { this.reasonId = reasonId; }
    public String getSolutionId() { return solutionId; }
    public void setSolutionId(String solutionId) { this.solutionId = solutionId; }
}
