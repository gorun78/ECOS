package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.List;

/**
 * 多目标优化问题定义。
 */
public class OptimizationProblem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 决策变量列表 */
    private List<Variable> variables;
    /** 约束列表 */
    private List<Constraint> constraints;
    /** 目标函数列表 */
    private List<Objective> objectives;

    public List<Variable> getVariables() { return variables; }
    public void setVariables(List<Variable> variables) { this.variables = variables; }
    public List<Constraint> getConstraints() { return constraints; }
    public void setConstraints(List<Constraint> constraints) { this.constraints = constraints; }
    public List<Objective> getObjectives() { return objectives; }
    public void setObjectives(List<Objective> objectives) { this.objectives = objectives; }
}
