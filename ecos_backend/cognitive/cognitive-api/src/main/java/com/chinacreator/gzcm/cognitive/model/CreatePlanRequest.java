package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;

/**
 * 创建执行计划请求。
 */
public class CreatePlanRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 计划来源 */
    private PlanSource source;
    /** 计划名称 */
    private String name;
    /** 优先级：P0/P1/P2/P3，默认 P2 */
    private String priority;
    /** 执行目标 */
    private PlanTarget target;

    public PlanSource getSource() { return source; }
    public void setSource(PlanSource source) { this.source = source; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public PlanTarget getTarget() { return target; }
    public void setTarget(PlanTarget target) { this.target = target; }
}
