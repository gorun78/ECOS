package com.chinacreator.gzcm.runtime.core.monitor.monitordata.bean;

import java.io.Serializable;

public class NodeProcessBean implements Serializable {
    private static final long serialVersionUID = 1L;

    private String nodeId;
    private String nodeInnerIp;
    private String scheduleId;
    private String scheduleName;
    private String processName;
    private boolean outTrans;
    private String processStatus;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeInnerIp() {
        return nodeInnerIp;
    }

    public void setNodeInnerIp(String nodeInnerIp) {
        this.nodeInnerIp = nodeInnerIp;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }

    public String getScheduleName() {
        return scheduleName;
    }

    public void setScheduleName(String scheduleName) {
        this.scheduleName = scheduleName;
    }

    public String getProcessName() {
        return processName;
    }

    public void setProcessName(String processName) {
        this.processName = processName;
    }

    public boolean isOutTrans() {
        return outTrans;
    }

    public void setOutTrans(boolean outTrans) {
        this.outTrans = outTrans;
    }

    public String getProcessStatus() {
        return processStatus;
    }

    public void setProcessStatus(String processStatus) {
        this.processStatus = processStatus;
    }
}
