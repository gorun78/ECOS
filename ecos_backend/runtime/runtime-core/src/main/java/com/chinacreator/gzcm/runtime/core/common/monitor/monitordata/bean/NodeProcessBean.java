package com.chinacreator.gzcm.runtime.core.common.monitor.monitordata.bean;

import java.io.Serializable;

/**
 * 节点处理Bean
 */
public class NodeProcessBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String nodeId;
    private String nodeName;
    private String processStatus;
    private String scheduleId;
    private String scheduleName;
    
    public String getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
    
    public String getNodeName() {
        return nodeName;
    }
    
    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }
    
    public String getProcessStatus() {
        return processStatus;
    }
    
    public void setProcessStatus(String processStatus) {
        this.processStatus = processStatus;
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
}
