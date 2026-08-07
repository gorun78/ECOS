package com.chinacreator.gzcm.engine.cognitive2.model;

/**
 * 因果诊断请求 DTO — 接收偏差指标信息，触发多层因果链推理。
 */
public class DiagnosisRequest {

    /** 指标名称，如 "毛利率"、"营收增长率" */
    private String metric;
    /** 偏差值，正数=上升，负数=下降，如 -5 表示下降5% */
    private double deviation;
    /** 业务域，如 "finance"、"sales"、"supply_chain" */
    private String domain;
    /** 因果链最大遍历深度，默认5 */
    private int maxDepth = 5;

    public DiagnosisRequest() {}

    public DiagnosisRequest(String metric, double deviation, String domain, int maxDepth) {
        this.metric = metric;
        this.deviation = deviation;
        this.domain = domain;
        this.maxDepth = maxDepth;
    }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public double getDeviation() { return deviation; }
    public void setDeviation(double deviation) { this.deviation = deviation; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
}
