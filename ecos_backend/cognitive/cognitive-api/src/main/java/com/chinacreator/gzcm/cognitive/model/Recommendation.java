package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;

/**
 * 改进建议。
 */
public class Recommendation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 关联层次 ID */
    private String layerId;
    /** 建议类型 */
    private String type;
    /** 优先级：HIGH / MEDIUM / LOW */
    private String priority;
    /** 建议消息 */
    private String message;

    public String getLayerId() { return layerId; }
    public void setLayerId(String layerId) { this.layerId = layerId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
