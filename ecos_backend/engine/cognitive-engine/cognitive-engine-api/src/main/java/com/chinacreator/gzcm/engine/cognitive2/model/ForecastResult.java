package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 预测结果 — 时序指标预测输出（PMO-51 T1，只增契约）。
 */
public class ForecastResult {

    /** 被预测指标名 */
    private String metric;
    /** 业务域 */
    private String domain;
    /** 实际使用的模型标识 */
    private String modelId;
    /** 整体置信度（0~1） */
    private double confidence;
    /** 是否被 KG 因子修正（true 表示叠加了 KG/context 因子修正） */
    private boolean kgAdjusted;
    /** 模型摘要（算法说明，供前端可解释展示） */
    private String summary;
    /** 预测点序列（≥horizon 目标点数，含置信区间） */
    private List<ForecastPoint> points = new ArrayList<>();
    /** 解释条款（可解释性：算法依据/因子修正说明） */
    private List<String> justifications = new ArrayList<>();

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public boolean isKgAdjusted() { return kgAdjusted; }
    public void setKgAdjusted(boolean kgAdjusted) { this.kgAdjusted = kgAdjusted; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<ForecastPoint> getPoints() { return points; }
    public void setPoints(List<ForecastPoint> points) { this.points = points; }
    public List<String> getJustifications() { return justifications; }
    public void setJustifications(List<String> justifications) { this.justifications = justifications; }
}
