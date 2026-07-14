package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 六层蓝图单层健康度。
 */
public class BlueprintLayer implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 层次标识：L1~L6 */
    private String layerId;
    /** 层次名称 */
    private String name;
    /** 健康评分 0~100 */
    private Double score;
    /** 状态：HEALTHY / WARNING / CRITICAL / DOWN */
    private String status;
    /** 关键指标键值对 */
    private Map<String, Object> metrics;
    /** 活跃告警列表 */
    private List<Alert> alerts;

    public String getLayerId() { return layerId; }
    public void setLayerId(String layerId) { this.layerId = layerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Map<String, Object> getMetrics() { return metrics; }
    public void setMetrics(Map<String, Object> metrics) { this.metrics = metrics; }
    public List<Alert> getAlerts() { return alerts; }
    public void setAlerts(List<Alert> alerts) { this.alerts = alerts; }
}
