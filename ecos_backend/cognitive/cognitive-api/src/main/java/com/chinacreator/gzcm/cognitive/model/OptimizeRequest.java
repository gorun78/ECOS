package com.chinacreator.gzcm.cognitive.model;

import java.io.Serializable;
import java.util.Map;

/**
 * 帕累托优化请求。
 */
public class OptimizeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 多目标优化问题定义 */
    private OptimizationProblem problem;
    /** 算法参数（种群大小、迭代代数等） */
    private Map<String, Object> options;

    public OptimizationProblem getProblem() { return problem; }
    public void setProblem(OptimizationProblem problem) { this.problem = problem; }
    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }
}
