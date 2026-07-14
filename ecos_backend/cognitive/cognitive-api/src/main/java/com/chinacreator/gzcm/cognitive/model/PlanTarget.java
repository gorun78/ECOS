package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;

/**
 * 执行目标。
 */
public class PlanTarget implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 指定 Hermes Agent ID */
    private String agentId;
    /** 指定工作流类型 */
    private String workflow;

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getWorkflow() { return workflow; }
    public void setWorkflow(String workflow) { this.workflow = workflow; }
}
