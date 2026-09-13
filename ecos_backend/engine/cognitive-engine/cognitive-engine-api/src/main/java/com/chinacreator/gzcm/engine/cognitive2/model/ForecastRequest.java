package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.List;
import java.util.Map;

/**
 * 预测请求 — 时序指标预测（PMO-51 T1，只增契约）。
 *
 * <p>历史序列由调用方传入（场景工作台/datanet 提供），认知引擎做"统计基线预测 + KG 因子修正"，
 * 不自行拉取业务数据源（保持推理无状态、不新增 DB 表）。</p>
 */
public class ForecastRequest {

    /** 被预测指标名（必填，用于 KG 因子修正） */
    private String metric;
    /** 业务域（可选，默认 default） */
    private String domain;
    /** 模型标识（可选，默认 baseline：移动平均 + 线性外推） */
    private String modelId;
    /** 预测步数（可选，默认 3，最大 60） */
    private int horizon;
    /** 历史序列（必填，≥2 点，chronological 顺序） */
    private List<SeriesPoint> series;
    /** 上下文因子（可选，KG 因子修正的输入键值，如 environment/season） */
    private Map<String, Object> context;

    /** 时序点：时间戳（epoch millis 或任意递增序数）+ 数值 */
    public static class SeriesPoint {
        /** 时间标识（数值序数亦可，只需递增） */
        private long t;
        /** 指标值 */
        private double value;

        public SeriesPoint() {
        }

        public SeriesPoint(long t, double value) {
            this.t = t;
            this.value = value;
        }

        public long getT() { return t; }
        public void setT(long t) { this.t = t; }
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
    }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    public int getHorizon() { return horizon; }
    public void setHorizon(int horizon) { this.horizon = horizon; }
    public List<SeriesPoint> getSeries() { return series; }
    public void setSeries(List<SeriesPoint> series) { this.series = series; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }
}
