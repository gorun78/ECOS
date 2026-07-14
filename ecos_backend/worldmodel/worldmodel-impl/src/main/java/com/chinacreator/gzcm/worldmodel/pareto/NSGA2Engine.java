package com.chinacreator.gzcm.worldmodel.pareto;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NSGA-II 多目标遗传算法引擎（纯 Java，零外部依赖）。
 *
 * <p>实现标准 NSGA-II 流程：
 * <ol>
 *   <li>初始化随机种群</li>
 *   <li>评估所有个体</li>
 *   <li>循环 gen 次：</li>
 *   <ol>
 *     <li>锦标赛选择 + SBX 交叉 + 多项式变异 → 子代群</li>
 *     <li>合并父子 → 非支配排序 → 拥挤度计算</li>
 *     <li>精英选择（按 rank 升序 + crowding 降序）→ 新种群</li>
 *   </ol>
 *   <li>返回 rank=0 的帕累托前沿个体</li>
 * </ol>
 *
 * <p>算法复杂度：非支配排序 O(M*N²)，拥挤度 O(M*N*logN)，每代 O(M*N²)。
 * 对于 100 个体 × 200 代 典型场景可在 5-10s 内完成。
 */
public class NSGA2Engine {

    private static final Logger log = LoggerFactory.getLogger(NSGA2Engine.class);

    private final Random rand = new Random();

    // 算法参数
    private double crossoverProb = 0.9;   // 交叉概率
    private double mutationProb;           // 变异概率（若不设置则 = 1/nVars）
    private double crossoverDistIdx = 20.0; // SBX 分布指数
    private double mutationDistIdx = 20.0;  // 多项式变异分布指数

    // ── 配置 ──

    public NSGA2Engine crossoverProb(double p) { this.crossoverProb = p; return this; }
    public NSGA2Engine mutationProb(double p) { this.mutationProb = p; return this; }
    public NSGA2Engine crossoverDistIdx(double eta) { this.crossoverDistIdx = eta; return this; }
    public NSGA2Engine mutationDistIdx(double eta) { this.mutationDistIdx = eta; return this; }

    // ── 主入口 ──

    /**
     * 执行 NSGA-II 进化。
     *
     * @param problem     优化问题定义
     * @param popSize     种群大小
     * @param generations 进化代数
     * @return rank=0 的帕累托前沿个体列表
     */
    public List<Individual> evolve(OptimizationProblem problem, int popSize, int generations) {
        if (problem.getVariables().isEmpty()) {
            return Collections.emptyList();
        }
        // 变异概率默认
        if (mutationProb <= 0) {
            mutationProb = 1.0 / problem.getVariables().size();
        }

        // 1. 初始化种群
        List<Individual> population = initialize(problem, popSize);
        // 2. 评估
        evaluateAll(problem, population);

        // 3. 进化循环
        for (int gen = 0; gen < generations; gen++) {
            // 3a. 选择 + 交叉 + 变异 → 子代
            List<Individual> offspring = makeOffspring(population, problem, popSize);
            // 3b. 评估子代
            evaluateAll(problem, offspring);
            // 3c. 合并
            List<Individual> combined = new ArrayList<>(population);
            combined.addAll(offspring);
            // 3d. 非支配排序
            List<List<Individual>> fronts = fastNonDominatedSort(combined, problem);
            // 3e. 拥挤度
            for (List<Individual> front : fronts) {
                crowdingDistanceAssignment(front, problem);
            }
            // 3f. 精英选择
            population = selectNextGeneration(fronts, popSize);

            if (gen % 50 == 0 || gen == generations - 1) {
                long front0 = population.stream().filter(i -> i.getRank() == 0).count();
                log.debug("Gen {}/{}: pop={}, front0={}", gen + 1, generations, population.size(), front0);
            }
        }

        // 最终再排一次以确保 rank 正确
        List<List<Individual>> finalFronts = fastNonDominatedSort(population, problem);
        return finalFronts.isEmpty() ? Collections.emptyList() : finalFronts.get(0);
    }

    // ── 初始化 ──

    List<Individual> initialize(OptimizationProblem problem, int popSize) {
        List<Individual> pop = new ArrayList<>(popSize);
        List<OptimizationProblem.Variable> vars = problem.getVariables();
        for (int i = 0; i < popSize; i++) {
            Individual ind = new Individual();
            for (OptimizationProblem.Variable v : vars) {
                double val = v.getMin() + rand.nextDouble() * (v.getMax() - v.getMin());
                if (v.getType() == OptimizationProblem.Variable.Type.INTEGER) {
                    val = Math.round(val);
                }
                ind.setVariable(v.getName(), val);
            }
            pop.add(ind);
        }
        return pop;
    }

    // ── 评估 ──

    void evaluateAll(OptimizationProblem problem, List<Individual> pop) {
        for (Individual ind : pop) {
            problem.evaluate(ind);
        }
    }

    // ── 繁殖 ──

    List<Individual> makeOffspring(List<Individual> population, OptimizationProblem problem, int popSize) {
        List<Individual> offspring = new ArrayList<>(popSize);
        List<OptimizationProblem.Variable> vars = problem.getVariables();

        while (offspring.size() < popSize) {
            // 锦标赛选择
            Individual p1 = binaryTournament(population);
            Individual p2 = binaryTournament(population);

            Individual c1 = new Individual(p1);
            Individual c2 = new Individual(p2);

            // SBX 交叉
            if (rand.nextDouble() < crossoverProb) {
                sbxCrossover(c1, c2, vars);
            }
            // 多项式变异
            polynomialMutate(c1, vars);
            polynomialMutate(c2, vars);

            // 边界检查
            clampVariables(c1, vars);
            clampVariables(c2, vars);

            offspring.add(c1);
            if (offspring.size() < popSize) {
                offspring.add(c2);
            }
        }
        return offspring;
    }

    // ── 锦标赛选择 ──

    Individual binaryTournament(List<Individual> pop) {
        int i = rand.nextInt(pop.size());
        int j = rand.nextInt(pop.size());
        Individual a = pop.get(i);
        Individual b = pop.get(j);

        // rank 更小者胜；同 rank 则 crowdingDistance 更大者胜
        if (a.getRank() < b.getRank()) return a;
        if (b.getRank() < a.getRank()) return b;
        return (a.getCrowdingDistance() > b.getCrowdingDistance()) ? a : b;
    }

    // ── SBX 交叉 ──

    void sbxCrossover(Individual c1, Individual c2, List<OptimizationProblem.Variable> vars) {
        for (OptimizationProblem.Variable v : vars) {
            double y1 = c1.getVariable(v.getName());
            double y2 = c2.getVariable(v.getName());
            double lb = v.getMin();
            double ub = v.getMax();

            if (Math.abs(y1 - y2) < 1e-14) continue;

            double u = rand.nextDouble();
            double beta;
            if (u <= 0.5) {
                beta = Math.pow(2.0 * u, 1.0 / (crossoverDistIdx + 1.0));
            } else {
                beta = Math.pow(1.0 / (2.0 * (1.0 - u)), 1.0 / (crossoverDistIdx + 1.0));
            }

            double yl = Math.min(y1, y2);
            double yu = Math.max(y1, y2);

            double new1 = 0.5 * ((yl + yu) - beta * (yu - yl));
            double new2 = 0.5 * ((yl + yu) + beta * (yu - yl));

            // 边界处理
            new1 = Math.max(lb, Math.min(ub, new1));
            new2 = Math.max(lb, Math.min(ub, new2));

            c1.setVariable(v.getName(), new1);
            c2.setVariable(v.getName(), new2);
        }
    }

    // ── 多项式变异 ──

    void polynomialMutate(Individual ind, List<OptimizationProblem.Variable> vars) {
        for (OptimizationProblem.Variable v : vars) {
            if (rand.nextDouble() >= mutationProb) continue;

            double y = ind.getVariable(v.getName());
            double lb = v.getMin();
            double ub = v.getMax();
            double delta;

            double u = rand.nextDouble();
            double delta_q;

            if (y < lb + 1e-14) {
                delta = (y - lb) / (ub - lb);
            } else if (y > ub - 1e-14) {
                delta = (ub - y) / (ub - lb);
            } else {
                if (u <= 0.5) {
                    delta_q = Math.pow(2.0 * u + (1.0 - 2.0 * u) * Math.pow(1.0 - (y - lb) / (ub - lb), mutationDistIdx + 1.0),
                                       1.0 / (mutationDistIdx + 1.0)) - 1.0;
                    delta = delta_q;
                } else {
                    delta_q = 1.0 - Math.pow(2.0 * (1.0 - u) + 2.0 * (u - 0.5) * Math.pow(1.0 - (ub - y) / (ub - lb), mutationDistIdx + 1.0),
                                             1.0 / (mutationDistIdx + 1.0));
                    delta = delta_q;
                }
            }

            double newVal;
            if (u <= 0.5) {
                newVal = y + delta * (ub - lb);
            } else {
                newVal = y + delta * (ub - lb);
            }
            // 简化：delta 已在 [-1, 1] 范围
            newVal = y + delta * (ub - lb);

            newVal = Math.max(lb, Math.min(ub, newVal));
            if (v.getType() == OptimizationProblem.Variable.Type.INTEGER) {
                newVal = Math.round(newVal);
            }
            ind.setVariable(v.getName(), newVal);
        }
    }

    // ── 边界 clamping ──

    void clampVariables(Individual ind, List<OptimizationProblem.Variable> vars) {
        for (OptimizationProblem.Variable v : vars) {
            double val = ind.getVariable(v.getName());
            val = Math.max(v.getMin(), Math.min(v.getMax(), val));
            if (v.getType() == OptimizationProblem.Variable.Type.INTEGER) {
                val = Math.round(val);
            }
            ind.setVariable(v.getName(), val);
        }
    }

    // ── 非支配排序 O(M*N²) ──

    List<List<Individual>> fastNonDominatedSort(List<Individual> pop, OptimizationProblem problem) {
        int n = pop.size();
        List<OptimizationProblem.Objective> objectives = problem.getObjectives();
        // dominationCount[i] = 支配 i 的个体数
        int[] dominationCount = new int[n];
        // dominatedSet[i] = 被 i 支配的个体索引集合
        List<List<Integer>> dominatedSet = new ArrayList<>(n);
        for (int i = 0; i < n; i++) dominatedSet.add(new ArrayList<>());

        // 第一层 front
        List<Integer> front0 = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int cmp = compareDominance(pop.get(i), pop.get(j), objectives);
                if (cmp == 1) { // i dominates j
                    dominatedSet.get(i).add(j);
                    dominationCount[j]++;
                } else if (cmp == -1) { // j dominates i
                    dominatedSet.get(j).add(i);
                    dominationCount[i]++;
                }
                // cmp == 0 → 互不支配
            }
            if (dominationCount[i] == 0) {
                pop.get(i).setRank(0);
                front0.add(i);
            }
        }

        List<List<Individual>> fronts = new ArrayList<>();
        List<Integer> currentFront = front0;

        while (!currentFront.isEmpty()) {
            List<Individual> frontIndividuals = new ArrayList<>();
            List<Integer> nextFront = new ArrayList<>();
            int currentRank = fronts.size();

            for (int idx : currentFront) {
                frontIndividuals.add(pop.get(idx));
                for (int dominatedIdx : dominatedSet.get(idx)) {
                    dominationCount[dominatedIdx]--;
                    if (dominationCount[dominatedIdx] == 0) {
                        pop.get(dominatedIdx).setRank(currentRank + 1);
                        nextFront.add(dominatedIdx);
                    }
                }
            }
            fronts.add(frontIndividuals);
            currentFront = nextFront;
        }

        return fronts;
    }

    /**
     * 比较两个个体的支配关系。
     * @return 1 if a dominates b, -1 if b dominates a, 0 otherwise
     */
    int compareDominance(Individual a, Individual b, List<OptimizationProblem.Objective> objectives) {
        boolean aBetterInAny = false;
        boolean bBetterInAny = false;

        for (OptimizationProblem.Objective obj : objectives) {
            String key = obj.getName();
            double va = a.getObjective(key);
            double vb = b.getObjective(key);
            if (Double.isNaN(va) || Double.isNaN(vb)) continue;

            boolean isMin = obj.getDirection() == OptimizationProblem.Objective.Direction.MIN;
            // MIN: 越小越好 (va < vb → a better); MAX: 越大越好 (va > vb → a better)
            if (isMin) {
                if (va < vb) aBetterInAny = true;
                if (vb < va) bBetterInAny = true;
            } else {
                if (va > vb) aBetterInAny = true;
                if (vb > va) bBetterInAny = true;
            }
        }

        if (aBetterInAny && !bBetterInAny) return 1;
        if (bBetterInAny && !aBetterInAny) return -1;
        return 0;
    }

    // ── 拥挤度 ──

    void crowdingDistanceAssignment(List<Individual> front, OptimizationProblem problem) {
        int size = front.size();
        for (Individual ind : front) ind.setCrowdingDistance(0.0);

        if (size <= 2) {
            for (Individual ind : front) ind.setCrowdingDistance(Double.POSITIVE_INFINITY);
            return;
        }

        for (OptimizationProblem.Objective obj : problem.getObjectives()) {
            final String objName = obj.getName();

            // 按目标值排序
            front.sort(Comparator.comparingDouble(ind -> {
                double v = ind.getObjective(objName);
                return Double.isNaN(v) ? 0.0 : v;
            }));

            double minVal = front.get(0).getObjective(objName);
            double maxVal = front.get(size - 1).getObjective(objName);
            double range = maxVal - minVal;
            if (range < 1e-14) continue;

            front.get(0).setCrowdingDistance(Double.POSITIVE_INFINITY);
            front.get(size - 1).setCrowdingDistance(Double.POSITIVE_INFINITY);

            for (int i = 1; i < size - 1; i++) {
                double prev = front.get(i - 1).getObjective(objName);
                double next = front.get(i + 1).getObjective(objName);
                double dist = (next - prev) / range;
                front.get(i).setCrowdingDistance(
                    front.get(i).getCrowdingDistance() + dist);
            }
        }
    }

    // ── 精英选择 ──

    List<Individual> selectNextGeneration(List<List<Individual>> fronts, int popSize) {
        List<Individual> next = new ArrayList<>(popSize);

        for (List<Individual> front : fronts) {
            if (next.size() + front.size() <= popSize) {
                next.addAll(front);
            } else {
                // 需要从当前 front 中选择部分个体
                int remain = popSize - next.size();
                // 按拥挤度降序排序
                List<Individual> sorted = new ArrayList<>(front);
                sorted.sort((a, b) -> Double.compare(b.getCrowdingDistance(), a.getCrowdingDistance()));
                for (int i = 0; i < remain; i++) {
                    next.add(sorted.get(i));
                }
                break;
            }
        }
        return next;
    }
}
