package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;

/**
 * 告警信息。
 */
public class Alert implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 严重级别：WARNING / CRITICAL */
    private String severity;
    /** 告警消息 */
    private String message;
    /** 告警时间 (ISO 8601) */
    private String raisedAt;

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRaisedAt() { return raisedAt; }
    public void setRaisedAt(String raisedAt) { this.raisedAt = raisedAt; }
}
