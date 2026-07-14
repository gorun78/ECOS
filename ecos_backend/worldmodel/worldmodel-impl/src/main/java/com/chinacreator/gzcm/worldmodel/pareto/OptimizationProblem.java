package com.chinacreator.gzcm.worldmodel.pareto;

import java.util.*;
import java.util.function.Function;

/**
 * 多目标优化问题定义。
 * <p>
 * 包含目标函数列表、决策变量列表、约束条件，以及内置的 ZDT 测试函数。
 */
public class OptimizationProblem {

    private String problemId;
    private String name;
    private List<Objective> objectives;
    private List<Variable> variables;
    private List<String> constraints;

    /** 内部评估器：个体 → 填充 objectives Map */
    private transient Function<Individual, Individual> evaluator;

    public OptimizationProblem() {
        this.problemId = UUID.randomUUID().toString().substring(0, 8);
        this.objectives = new ArrayList<>();
        this.variables = new ArrayList<>();
        this.constraints = new ArrayList<>();
    }

    /**
     * 根据目标/变量构建内置评估器。
     * 若用户在 request 中指定了 objective names，则自动匹配到注册函数；
     * 若没有匹配，则回退到 ZDT1 风格 (f1=x0², f2=(x0-2)²)。
     */
    public void buildEvaluator() {
        ObjectiveRegistry reg = ObjectiveRegistry.get();
        this.evaluator = ind -> {
            // 清空旧值
            ind.getObjectives().clear();
            for (Objective obj : objectives) {
                double val = reg.evaluate(obj.getName(), ind);
                ind.setObjective(obj.getName(), val);
            }
            return ind;
        };
    }

    /**
     * 评估一个个体，返回填充好 objectives 的同一个对象。
     */
    public Individual evaluate(Individual ind) {
        if (evaluator == null) buildEvaluator();
        return evaluator.apply(ind);
    }

    // ── getters / setters ──

    public String getProblemId() { return problemId; }
    public void setProblemId(String id) { this.problemId = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Objective> getObjectives() { return objectives; }
    public void setObjectives(List<Objective> objectives) { this.objectives = objectives; }

    public List<Variable> getVariables() { return variables; }
    public void setVariables(List<Variable> variables) { this.variables = variables; }

    public List<String> getConstraints() { return constraints; }
    public void setConstraints(List<String> constraints) { this.constraints = constraints; }

    // ── 内置子类型 ──

    public static class Objective {
        private String name;
        private Direction direction; // MIN / MAX
        private double weight;

        public enum Direction { MIN, MAX }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Direction getDirection() { return direction; }
        public void setDirection(Direction direction) { this.direction = direction; }

        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }

        public Objective() {}
        public Objective(String name, Direction direction) {
            this.name = name;
            this.direction = direction;
            this.weight = 1.0;
        }
    }

    public static class Variable {
        private String name;
        private Type type; // INTEGER / DOUBLE
        private double min;
        private double max;

        public enum Type { INTEGER, DOUBLE }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Type getType() { return type; }
        public void setType(Type type) { this.type = type; }

        public double getMin() { return min; }
        public void setMin(double min) { this.min = min; }

        public double getMax() { return max; }
        public void setMax(double max) { this.max = max; }

        public Variable() {}
        public Variable(String name, Type type, double min, double max) {
            this.name = name;
            this.type = type;
            this.min = min;
            this.max = max;
        }
    }
}
