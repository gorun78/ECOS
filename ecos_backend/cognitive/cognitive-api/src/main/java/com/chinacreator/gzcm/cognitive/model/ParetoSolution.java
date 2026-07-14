package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.Map;

/**
 * 帕累托最优解。
 */
public class ParetoSolution implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 解 ID */
    private String solutionId;
    /** 非支配排序等级 (1 = Pareto Front) */
    private Integer rank;
    /** 决策变量取值 */
    private Map<String, Object> variables;
    /** 目标函数值 */
    private Map<String, Object> objectives;
    /** 拥挤度距离 (NSGA-II) */
    private Double crowdingDistance;
    /** 可行性：FEASIBLE / INFEASIBLE */
    private String feasibility;

    public String getSolutionId() { return solutionId; }
    public void setSolutionId(String solutionId) { this.solutionId = solutionId; }
    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public Map<String, Object> getVariables() { return variables; }
    public void setVariables(Map<String, Object> variables) { this.variables = variables; }
    public Map<String, Object> getObjectives() { return objectives; }
    public void setObjectives(Map<String, Object> objectives) { this.objectives = objectives; }
    public Double getCrowdingDistance() { return crowdingDistance; }
    public void setCrowdingDistance(Double crowdingDistance) { this.crowdingDistance = crowdingDistance; }
    public String getFeasibility() { return feasibility; }
    public void setFeasibility(String feasibility) { this.feasibility = feasibility; }
}
