package com.chinacreator.gzcm.worldmodel.pareto;

import java.util.*;

/**
 * NSGA-II 种群个体。
 * <p>
 * 每个个体携带决策变量值、目标函数值、非支配层级 (rank) 和拥挤度距离。
 */
public class Individual {

    /** 决策变量名 → 值 */
    private final Map<String, Double> variables;

    /** 目标函数名 → 值 */
    private final Map<String, Double> objectives;

    /** 非支配排序层级 (0 = Pareto front) */
    private int rank;

    /** 拥挤度距离 */
    private double crowdingDistance;

    public Individual() {
        this.variables = new LinkedHashMap<>();
        this.objectives = new LinkedHashMap<>();
        this.rank = Integer.MAX_VALUE;
        this.crowdingDistance = 0.0;
    }

    /** 浅拷贝变量和目标值 */
    public Individual(Individual other) {
        this.variables = new LinkedHashMap<>(other.variables);
        this.objectives = new LinkedHashMap<>(other.objectives);
        this.rank = other.rank;
        this.crowdingDistance = other.crowdingDistance;
    }

    // ── getters / setters ──

    public Map<String, Double> getVariables() { return variables; }
    public Map<String, Double> getObjectives() { return objectives; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public double getCrowdingDistance() { return crowdingDistance; }
    public void setCrowdingDistance(double d) { this.crowdingDistance = d; }

    public double getVariable(String name) { return variables.getOrDefault(name, 0.0); }
    public void setVariable(String name, double val) { variables.put(name, val); }

    public double getObjective(String name) { return objectives.getOrDefault(name, Double.NaN); }
    public void setObjective(String name, double val) { objectives.put(name, val); }

    @Override
    public String toString() {
        return "Individual{vars=" + variables + ", objs=" + objectives + ", rank=" + rank + "}";
    }
}
