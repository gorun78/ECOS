package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.List;

/**
 * 六层蓝图健康度响应。
 */
public class BlueprintResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 生成时间 (ISO 8601) */
    private String generatedAt;
    /** 全局健康评分 0~100 */
    private Double overallScore;
    /** 六层健康度明细 */
    private List<BlueprintLayer> layers;
    /** 改进建议 */
    private List<Recommendation> recommendations;

    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
    public Double getOverallScore() { return overallScore; }
    public void setOverallScore(Double overallScore) { this.overallScore = overallScore; }
    public List<BlueprintLayer> getLayers() { return layers; }
    public void setLayers(List<BlueprintLayer> layers) { this.layers = layers; }
    public List<Recommendation> getRecommendations() { return recommendations; }
    public void setRecommendations(List<Recommendation> recommendations) { this.recommendations = recommendations; }
}
