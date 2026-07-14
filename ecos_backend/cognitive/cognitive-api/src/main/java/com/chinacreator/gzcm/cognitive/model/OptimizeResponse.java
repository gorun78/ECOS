package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 帕累托优化响应。
 */
public class OptimizeResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 帕累托前沿 ID */
    private String frontId;
    /** 实际迭代代数 */
    private Integer generations;
    /** 优化耗时 (毫秒) */
    private Long elapsedMs;
    /** 收敛代数 */
    private Integer convergedAt;
    /** 帕累托前沿解集 */
    private List<ParetoSolution> paretoFront;
    /** 问题摘要 */
    private ProblemSummary problemSummary;

    public String getFrontId() { return frontId; }
    public void setFrontId(String frontId) { this.frontId = frontId; }
    public Integer getGenerations() { return generations; }
    public void setGenerations(Integer generations) { this.generations = generations; }
    public Long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(Long elapsedMs) { this.elapsedMs = elapsedMs; }
    public Integer getConvergedAt() { return convergedAt; }
    public void setConvergedAt(Integer convergedAt) { this.convergedAt = convergedAt; }
    public List<ParetoSolution> getParetoFront() { return paretoFront; }
    public void setParetoFront(List<ParetoSolution> paretoFront) { this.paretoFront = paretoFront; }
    public ProblemSummary getProblemSummary() { return problemSummary; }
    public void setProblemSummary(ProblemSummary problemSummary) { this.problemSummary = problemSummary; }
}
