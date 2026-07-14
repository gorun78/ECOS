package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.Map;

/**
 * 执行动作。
 */
public class Action implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 动作类型：PAUSE_PIPELINE / NOTIFY / THROTTLE / RUN_DQ_CHECK */
    private String type;
    /** 动作目标 */
    private String target;
    /** 动作参数 */
    private Map<String, Object> params;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
