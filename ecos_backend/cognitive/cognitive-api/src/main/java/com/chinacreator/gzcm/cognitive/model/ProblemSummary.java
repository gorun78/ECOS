package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;

/**
 * 优化问题摘要统计。
 */
public class ProblemSummary implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 变量数 */
    private Integer variableCount;
    /** 约束数 */
    private Integer constraintCount;
    /** 目标数 */
    private Integer objectiveCount;
    /** 可行解数量 */
    private Integer feasibleSolutions;
    /** 不可行解数量 */
    private Integer infeasibleSolutions;

    public Integer getVariableCount() { return variableCount; }
    public void setVariableCount(Integer variableCount) { this.variableCount = variableCount; }
    public Integer getConstraintCount() { return constraintCount; }
    public void setConstraintCount(Integer constraintCount) { this.constraintCount = constraintCount; }
    public Integer getObjectiveCount() { return objectiveCount; }
    public void setObjectiveCount(Integer objectiveCount) { this.objectiveCount = objectiveCount; }
    public Integer getFeasibleSolutions() { return feasibleSolutions; }
    public void setFeasibleSolutions(Integer feasibleSolutions) { this.feasibleSolutions = feasibleSolutions; }
    public Integer getInfeasibleSolutions() { return infeasibleSolutions; }
    public void setInfeasibleSolutions(Integer infeasibleSolutions) { this.infeasibleSolutions = infeasibleSolutions; }
}
