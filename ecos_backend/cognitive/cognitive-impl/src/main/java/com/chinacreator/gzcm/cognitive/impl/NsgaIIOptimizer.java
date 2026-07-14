package com.chinacreator.gzcm.cognitive.impl;

import com.chinacreator.gzcm.cognitive.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * NSGA-II (Non-dominated Sorting Genetic Algorithm II) 帕累托多目标优化器。
 *
 * <p>自研轻量实现，零外部依赖，纯 Java 标准库。嵌入 cognitive 模块，
 * 作为认知引擎第三推理器（Rule Engine → Causal Reasoner → Pareto Optimizer）。
 *
 * <h3>算法核心步骤</h3>
 * <ol>
 *   <li>种群初始化 — 随机生成 popSize 个满足约束的可行解</li>
 *   <li>快速非支配排序 — O(M·N²) 将种群按帕累托前沿分层</li>
 *   <li>拥挤度距离 — 每层内按目标值排序，计算密度指标</li>
 *   <li>锦标赛选择 — 先比支配层级，再比拥挤度</li>
 *   <li>SBX 交叉 + 多项式变异 — 生成子代</li>
 *   <li>精英保留 — 父子合并 → 非支配排序 → 选前 popSize 个</li>
 *   <li>收敛检测 — 连续 N 代前沿无变化则提前终止</li>
 * </ol>
 *
 * @author ECOS-Cognitive
 * @since 1.0.0
 */
@Service("nsgaIIOptimizer")
public class NsgaIIOptimizer {

    private static final Logger log = LoggerFactory.getLogger(NsgaIIOptimizer.class);

    // ──────────────── 可配置参数 ────────────────

    /** 种群大小，默认 100 */
    private int popSize = 100;
    /** 最大迭代代数，默认 100 */
    private int maxGenerations = 100;
    /** 交叉概率，默认 0.9 */
    private double crossoverProb = 0.9;
    /** 变异概率（每变量），默认 1/n */
    private double mutationProb = Double.NaN;
    /** SBX / PM 分布指数，默认 20 */
    private double distributionIndex = 20.0;
    /** 收敛检测：连续几代前沿不变则终止，默认 5 */
    private int convergenceGen = 5;

    // ──────────────── 问题定义（运行时注入） ────────────────

    private double[] lowerBounds;
    private double[] upperBounds;
    private String[] varNames;
    private int numVariables;
    private int numObjectives;
    private String[] objDirections; // "MIN" or "MAX"
    private String[] objNames;

    private final Random random = new Random();

    // ──────────────── 函数式接口 ────────────────

    /**
     * 约束检查函数 — 返回 true 表示可行。
     */
    @FunctionalInterface
    public interface ConstraintChecker {
        boolean isFeasible(double[] variables);
    }

    /**
     * 目标函数评估 — 返回 double[numObjectives]。
     */
    @FunctionalInterface
    public interface ObjectiveEvaluator {
        double[] evaluate(double[] variables);
    }

    // ──────────────── 内部类 ────────────────

    /**
     * 个体：决策变量 + 目标值 + 非支配排序信息。
     */
    public static class Individual {
        double[] variables;
        double[] objectives;
        int rank = Integer.MAX_VALUE;
        double crowdingDistance = 0.0;
        /** 被该个体支配的解计数 */
        int dominationCount = 0;
        /** 该个体支配的解集合 */
        List<Individual> dominatedSet = new ArrayList<>();

        public Individual(double[] variables, double[] objectives) {
            this.variables = variables.clone();
            this.objectives = objectives.clone();
        }

        public double[] getVariables() { return variables; }
        public double[] getObjectives() { return objectives; }
        public int getRank() { return rank; }
        public double getCrowdingDistance() { return crowdingDistance; }

        @Override
        public String toString() {
            return String.format("Ind[rank=%d, cd=%.4f, vars=%s, objs=%s]",
                    rank, crowdingDistance, Arrays.toString(variables), Arrays.toString(objectives));
        }
    }

    /**
     * 帕累托前沿结果容器。
     */
    public static class ParetoFrontResult {
        private final List<ParetoSolution> solutions;
        private final int generations;
        private final long elapsedMs;
        private final int convergedAt;

        public ParetoFrontResult(List<ParetoSolution> solutions, int generations, long elapsedMs, int convergedAt) {
            this.solutions = solutions;
            this.generations = generations;
            this.elapsedMs = elapsedMs;
            this.convergedAt = convergedAt;
        }

        public List<ParetoSolution> getSolutions() { return solutions; }
        public int getGenerations() { return generations; }
        public long getElapsedMs() { return elapsedMs; }
        public int getConvergedAt() { return convergedAt; }
    }

    // ──────────────── 公开 API ────────────────

    /**
     * 基于 {@link OptimizeRequest} 执行多目标优化。
     *
     * @param request 优化请求（含问题定义和参数选项）
     * @return 帕累托前沿解集
     */
    public ParetoFrontResult optimize(OptimizeRequest request,
                                       ConstraintChecker constraintChecker,
                                       ObjectiveEvaluator evaluator) {
        OptimizationProblem problem = request.getProblem();
        if (problem == null) {
            throw new IllegalArgumentException("OptimizationProblem must not be null");
        }

        // 解析变量
        List<Variable> vars = problem.getVariables();
        numVariables = vars.size();
        lowerBounds = new double[numVariables];
        upperBounds = new double[numVariables];
        varNames = new String[numVariables];
        for (int i = 0; i < numVariables; i++) {
            Variable v = vars.get(i);
            varNames[i] = v.getName();
            lowerBounds[i] = toDouble(v.getMin());
            upperBounds[i] = toDouble(v.getMax());
        }

        // 解析目标
        List<Objective> objs = problem.getObjectives();
        numObjectives = objs.size();
        objDirections = new String[numObjectives];
        objNames = new String[numObjectives];
        for (int i = 0; i < numObjectives; i++) {
            Objective o = objs.get(i);
            objNames[i] = o.getName();
            objDirections[i] = o.getDirection() != null ? o.getDirection().toUpperCase() : "MIN";
        }

        // 应用选项
        applyOptions(request.getOptions());

        return optimize(lowerBounds, upperBounds, varNames,
                numObjectives, objDirections, objNames,
                constraintChecker, evaluator, request.getOptions());
    }

    /**
     * 核心优化入口（可直接编程调用）。
     */
    public ParetoFrontResult optimize(
            double[] lowerBounds, double[] upperBounds, String[] varNames,
            int numObjectives, String[] objDirections, String[] objNames,
            ConstraintChecker constraintChecker, ObjectiveEvaluator evaluator,
            Map<String, Object> options) {

        this.lowerBounds = lowerBounds;
        this.upperBounds = upperBounds;
        this.varNames = varNames;
        this.numVariables = lowerBounds.length;
        this.numObjectives = numObjectives;
        this.objDirections = objDirections;
        this.objNames = objNames;

        applyOptions(options);

        // 自动计算变异概率
        if (Double.isNaN(mutationProb)) {
            mutationProb = 1.0 / numVariables;
        }

        log.info("NSGA-II start: vars={}, objs={}, popSize={}, maxGen={}",
                numVariables, numObjectives, popSize, maxGenerations);

        long startTime = System.currentTimeMillis();

        // ── Step 1: 种群初始化 ──
        List<Individual> population = initializePopulation(constraintChecker, evaluator);

        // ── Step 2 & 3: 非支配排序 + 拥挤度 ──
        List<List<Individual>> fronts = fastNonDominatedSort(population);
        for (List<Individual> front : fronts) {
            computeCrowdingDistance(front);
        }

        // ── 收敛跟踪 ──
        int convergedAt = -1;
        int noImprovementCount = 0;
        List<Individual> previousBestFront = new ArrayList<>();

        // ── Step 4-7: 进化主循环 ──
        for (int gen = 0; gen < maxGenerations; gen++) {
            // 选择 + 交叉 + 变异 → 子代
            List<Individual> offspring = createOffspring(population, evaluator);

            // 父子合并
            List<Individual> combined = new ArrayList<>(population);
            combined.addAll(offspring);

            // 非支配排序
            fronts = fastNonDominatedSort(combined);

            // 精英选择 → 下一代
            population = selectNextGeneration(fronts, popSize);

            // 重新计算拥挤度（用于下一轮选择）
            for (List<Individual> front : fronts) {
                computeCrowdingDistance(front);
            }

            // 收敛检测
            if (checkConvergence(fronts, previousBestFront)) {
                noImprovementCount++;
                if (noImprovementCount >= convergenceGen) {
                    convergedAt = gen + 1;
                    log.info("NSGA-II converged at generation {}", convergedAt);
                    break;
                }
            } else {
                noImprovementCount = 0;
                previousBestFront = deepCopyFront(fronts.isEmpty() ? Collections.emptyList() : fronts.get(0));
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;

        // ── Step 8: 构建输出 ──
        List<ParetoSolution> solutions = buildParetoSolutions(fronts);

        log.info("NSGA-II done: generations={}, frontSize={}, elapsed={}ms",
                convergedAt > 0 ? convergedAt : maxGenerations, solutions.size(), elapsed);

        return new ParetoFrontResult(solutions,
                convergedAt > 0 ? convergedAt : maxGenerations, elapsed, convergedAt);
    }

    // ──────────────── 算法核心方法 ────────────────

    /**
     * <h3>(a) 种群初始化</h3>
     * 随机生成 popSize 个可行解。对每个个体，在变量范围内随机采样，
     * 若启用约束检查则最多重试 100 次以获得可行解。
     */
    private List<Individual> initializePopulation(ConstraintChecker checker, ObjectiveEvaluator evaluator) {
        List<Individual> pop = new ArrayList<>(popSize);
        int maxAttempts = 100;

        for (int i = 0; i < popSize; i++) {
            double[] vars = new double[numVariables];
            boolean feasible = false;
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                for (int j = 0; j < numVariables; j++) {
                    vars[j] = lowerBounds[j] + random.nextDouble() * (upperBounds[j] - lowerBounds[j]);
                }
                if (checker == null || checker.isFeasible(vars)) {
                    feasible = true;
                    break;
                }
            }
            if (!feasible) {
                log.warn("Could not find feasible solution for individual {}, using random", i);
            }
            double[] objs = evaluator.evaluate(vars);
            // 目标值归一化方向：MIN 问题越大越差，MAX 问题越大越好
            // NSGA-II 假设所有目标都是最小化，因此在支配比较时统一处理
            pop.add(new Individual(vars, objs));
        }
        return pop;
    }

    /**
     * <h3>(b) 快速非支配排序 (Fast Non-Dominated Sort)</h3>
     *
     * <p>时间复杂度 O(M·N²)，其中 M = 目标数，N = 种群大小。
     * 算法（Deb et al. 2002）：
     * <ol>
     *   <li>对每个个体 p，计算支配计数 n_p（支配 p 的个体数）和支配集 S_p（p 支配的个体集合）</li>
     *   <li>n_p == 0 的个体构成第一前沿 F1</li>
     *   <li>对 F1 中每个个体 p，遍历 S_p：对每个 q 将 n_q 减 1；若 n_q 变为 0，q 进入下一前沿</li>
     *   <li>重复直至所有个体分层完毕</li>
     * </ol>
     */
    List<List<Individual>> fastNonDominatedSort(List<Individual> population) {
        List<List<Individual>> fronts = new ArrayList<>();
        List<Individual> front1 = new ArrayList<>();

        // 重置并计算支配关系
        for (Individual p : population) {
            p.dominationCount = 0;
            p.dominatedSet.clear();
            for (Individual q : population) {
                if (p == q) continue;
                int cmp = dominanceCompare(p, q);
                if (cmp < 0) {
                    // p 支配 q
                    p.dominatedSet.add(q);
                } else if (cmp > 0) {
                    // q 支配 p
                    p.dominationCount++;
                }
            }
            if (p.dominationCount == 0) {
                p.rank = 1;
                front1.add(p);
            }
        }

        fronts.add(front1);
        int frontIdx = 0;
        while (frontIdx < fronts.size()) {
            List<Individual> currentFront = fronts.get(frontIdx);
            List<Individual> nextFront = new ArrayList<>();
            for (Individual p : currentFront) {
                for (Individual q : p.dominatedSet) {
                    q.dominationCount--;
                    if (q.dominationCount == 0) {
                        q.rank = frontIdx + 2; // rank 从 1 开始
                        nextFront.add(q);
                    }
                }
            }
            if (!nextFront.isEmpty()) {
                fronts.add(nextFront);
            }
            frontIdx++;
        }

        return fronts;
    }

    /**
     * 支配比较：返回负值表示 a 支配 b，正值表示 b 支配 a，0 表示互不支配。
     *
     * <p>处理 MIN/MAX 方向：内部统一转为"越小越好"比较。
     */
    private int dominanceCompare(Individual a, Individual b) {
        boolean aBetterInAny = false;
        boolean bBetterInAny = false;

        for (int i = 0; i < numObjectives; i++) {
            double va = a.objectives[i];
            double vb = b.objectives[i];
            // MAX 方向取反，统一为"越小越好"
            if ("MAX".equalsIgnoreCase(objDirections[i])) {
                va = -va;
                vb = -vb;
            }
            if (va < vb) {
                aBetterInAny = true;
            } else if (vb < va) {
                bBetterInAny = true;
            }
        }

        if (aBetterInAny && !bBetterInAny) return -1;  // a 支配 b
        if (bBetterInAny && !aBetterInAny) return 1;   // b 支配 a
        return 0; // 互不支配
    }

    /**
     * <h3>(c) 拥挤度距离 (Crowding Distance)</h3>
     *
     * <p>在同一前沿内，对每个目标维度排序，边界个体设为大值，
     * 中间个体累加归一化后的相邻距离。
     */
    void computeCrowdingDistance(List<Individual> front) {
        int size = front.size();
        if (size <= 2) {
            for (Individual ind : front) {
                ind.crowdingDistance = Double.MAX_VALUE;
            }
            return;
        }

        // 重置所有个体拥挤度
        for (Individual ind : front) {
            ind.crowdingDistance = 0.0;
        }

        for (int m = 0; m < numObjectives; m++) {
            final int objIdx = m;
            // 按当前目标值排序
            front.sort(Comparator.comparingDouble(ind -> ind.objectives[objIdx]));

            // 边界个体赋予大值
            front.get(0).crowdingDistance = Double.MAX_VALUE;
            front.get(size - 1).crowdingDistance = Double.MAX_VALUE;

            double fmin = front.get(0).objectives[objIdx];
            double fmax = front.get(size - 1).objectives[objIdx];
            double range = fmax - fmin;
            if (range < 1e-12) continue; // 避免除零

            for (int i = 1; i < size - 1; i++) {
                double diff = front.get(i + 1).objectives[objIdx] - front.get(i - 1).objectives[objIdx];
                front.get(i).crowdingDistance += diff / range;
            }
        }
    }

    /**
     * <h3>(d) 锦标赛选择</h3>
     *
     * <p>从种群中随机选取两个个体，先比较支配层级（rank），再比较拥挤度。
     * rank 越小越好，拥挤度越大越好（保持多样性）。
     */
    private Individual tournamentSelect(List<Individual> population) {
        int size = population.size();
        Individual a = population.get(random.nextInt(size));
        Individual b = population.get(random.nextInt(size));

        if (a.rank < b.rank) return a;
        if (b.rank < a.rank) return b;
        // 同层：拥挤度大的优先
        if (a.crowdingDistance > b.crowdingDistance) return a;
        if (b.crowdingDistance > a.crowdingDistance) return b;
        // 平局随机
        return random.nextBoolean() ? a : b;
    }

    /**
     * <h3>(e) 模拟二进制交叉 SBX (Simulated Binary Crossover)</h3>
     *
     * <p>对两个父代个体进行交叉，产生两个子代。
     * SBX 模拟单点二进制交叉在实数编码中的行为。
     */
    private Individual[] sbxCrossover(Individual parent1, Individual parent2) {
        double[] child1Vars = new double[numVariables];
        double[] child2Vars = new double[numVariables];
        double eta = distributionIndex;

        for (int i = 0; i < numVariables; i++) {
            double p1 = parent1.variables[i];
            double p2 = parent2.variables[i];

            if (random.nextDouble() <= 0.5) {
                double u = random.nextDouble();
                double beta;
                if (u <= 0.5) {
                    beta = Math.pow(2.0 * u, 1.0 / (eta + 1.0));
                } else {
                    beta = Math.pow(1.0 / (2.0 * (1.0 - u)), 1.0 / (eta + 1.0));
                }
                child1Vars[i] = 0.5 * ((1.0 + beta) * p1 + (1.0 - beta) * p2);
                child2Vars[i] = 0.5 * ((1.0 - beta) * p1 + (1.0 + beta) * p2);
            } else {
                child1Vars[i] = p1;
                child2Vars[i] = p2;
            }

            // 边界裁剪
            child1Vars[i] = clamp(child1Vars[i], lowerBounds[i], upperBounds[i]);
            child2Vars[i] = clamp(child2Vars[i], lowerBounds[i], upperBounds[i]);
        }

        double[] objs1 = new double[numObjectives]; // 稍后评估
        double[] objs2 = new double[numObjectives];
        return new Individual[] {
            new Individual(child1Vars, objs1),
            new Individual(child2Vars, objs2)
        };
    }

    /**
     * <h3>(f) 多项式变异 (Polynomial Mutation)</h3>
     *
     * <p>对个体每个变量以概率 mutationProb 执行多项式变异。
     */
    private void polynomialMutate(Individual individual) {
        double eta = distributionIndex;

        for (int i = 0; i < numVariables; i++) {
            if (random.nextDouble() >= mutationProb) continue;

            double x = individual.variables[i];
            double lb = lowerBounds[i];
            double ub = upperBounds[i];
            double range = ub - lb;

            double u = random.nextDouble();
            double delta;

            if (u <= 0.5) {
                double delta_q = Math.pow(2.0 * u + (1.0 - 2.0 * u) * Math.pow(1.0 - (x - lb) / range, eta + 1.0),
                        1.0 / (eta + 1.0)) - 1.0;
                delta = delta_q;
            } else {
                double delta_q = 1.0 - Math.pow(2.0 * (1.0 - u) + 2.0 * (u - 0.5) * Math.pow(1.0 - (ub - x) / range, eta + 1.0),
                        1.0 / (eta + 1.0));
                delta = delta_q;
            }

            individual.variables[i] = clamp(x + delta * range, lb, ub);
        }
    }

    /**
     * 生成子代：选择 → 交叉 → 变异 → 评估目标值。
     */
    private List<Individual> createOffspring(List<Individual> population, ObjectiveEvaluator evaluator) {
        List<Individual> offspring = new ArrayList<>(popSize);

        while (offspring.size() < popSize) {
            Individual parent1 = tournamentSelect(population);
            Individual parent2 = tournamentSelect(population);

            Individual[] children;
            if (random.nextDouble() < crossoverProb) {
                children = sbxCrossover(parent1, parent2);
            } else {
                children = new Individual[] {
                    new Individual(parent1.variables.clone(), new double[numObjectives]),
                    new Individual(parent2.variables.clone(), new double[numObjectives])
                };
            }

            for (Individual child : children) {
                polynomialMutate(child);
                child.objectives = evaluator.evaluate(child.variables);
                offspring.add(child);
                if (offspring.size() >= popSize) break;
            }
        }
        return offspring;
    }

    /**
     * <h3>(g) 精英保留：从前沿中选取下一代种群</h3>
     *
     * <p>按前沿分层依次选取，若某层不能完整加入，则按拥挤度降序选取。
     */
    List<Individual> selectNextGeneration(List<List<Individual>> fronts, int targetSize) {
        List<Individual> nextGen = new ArrayList<>(targetSize);

        for (List<Individual> front : fronts) {
            if (nextGen.size() + front.size() <= targetSize) {
                // 整个前沿加入
                nextGen.addAll(front);
            } else {
                // 需要从当前前沿选取部分个体
                int remaining = targetSize - nextGen.size();
                // 按拥挤度降序排列
                List<Individual> sorted = new ArrayList<>(front);
                sorted.sort((a, b) -> Double.compare(b.crowdingDistance, a.crowdingDistance));
                for (int i = 0; i < remaining; i++) {
                    nextGen.add(sorted.get(i));
                }
                break;
            }
        }
        return nextGen;
    }

    /**
     * <h3>(h) 收敛检测</h3>
     *
     * <p>比较当前第一前沿与上一代第一前沿是否相同（按变量值匹对）。
     */
    private boolean checkConvergence(List<List<Individual>> fronts, List<Individual> previousBestFront) {
        if (fronts.isEmpty() || previousBestFront.isEmpty()) return false;
        List<Individual> currentBest = fronts.get(0);
        if (currentBest.size() != previousBestFront.size()) return false;

        // 排序后逐对比较目标值（近似判断前沿是否稳定）
        Comparator<Individual> comp = (a, b) -> {
            for (int i = 0; i < numObjectives; i++) {
                int cmp = Double.compare(a.objectives[i], b.objectives[i]);
                if (cmp != 0) return cmp;
            }
            return 0;
        };

        List<Individual> sortedCurrent = new ArrayList<>(currentBest);
        List<Individual> sortedPrevious = new ArrayList<>(previousBestFront);
        sortedCurrent.sort(comp);
        sortedPrevious.sort(comp);

        double epsilon = 1e-6;
        for (int i = 0; i < sortedCurrent.size(); i++) {
            for (int j = 0; j < numObjectives; j++) {
                if (Math.abs(sortedCurrent.get(i).objectives[j] - sortedPrevious.get(i).objectives[j]) > epsilon) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 深拷贝前沿个体（用于收敛比较）。
     */
    private List<Individual> deepCopyFront(List<Individual> front) {
        List<Individual> copy = new ArrayList<>(front.size());
        for (Individual ind : front) {
            Individual c = new Individual(ind.variables.clone(), ind.objectives.clone());
            c.rank = ind.rank;
            c.crowdingDistance = ind.crowdingDistance;
            copy.add(c);
        }
        return copy;
    }

    // ──────────────── 输出构建 ────────────────

    /**
     * 将排序后的前沿转换为 {@link ParetoSolution} 列表。
     * 仅返回排名为 1（帕累托前沿）的解，或全部前沿（按 rank 分组）。
     */
    private List<ParetoSolution> buildParetoSolutions(List<List<Individual>> fronts) {
        List<ParetoSolution> solutions = new ArrayList<>();

        for (int fi = 0; fi < fronts.size(); fi++) {
            List<Individual> front = fronts.get(fi);
            for (int i = 0; i < front.size(); i++) {
                Individual ind = front.get(i);
                ParetoSolution sol = new ParetoSolution();
                sol.setSolutionId(String.format("NSGA2-%d-%d", fi + 1, i + 1));
                sol.setRank(fi + 1);
                sol.setCrowdingDistance(ind.crowdingDistance);
                sol.setFeasibility("FEASIBLE");

                // 变量 Map
                Map<String, Object> varMap = new LinkedHashMap<>();
                for (int j = 0; j < numVariables; j++) {
                    varMap.put(varNames[j], ind.variables[j]);
                }
                sol.setVariables(varMap);

                // 目标值 Map
                Map<String, Object> objMap = new LinkedHashMap<>();
                for (int j = 0; j < numObjectives; j++) {
                    objMap.put(objNames[j], ind.objectives[j]);
                }
                sol.setObjectives(objMap);

                solutions.add(sol);
            }
        }
        return solutions;
    }

    // ──────────────── 工具方法 ────────────────

    private void applyOptions(Map<String, Object> options) {
        if (options == null) return;
        if (options.containsKey("popSize")) popSize = toInt(options.get("popSize"), popSize);
        if (options.containsKey("maxGenerations")) maxGenerations = toInt(options.get("maxGenerations"), maxGenerations);
        if (options.containsKey("crossoverProb")) crossoverProb = toDouble(options.get("crossoverProb"), crossoverProb);
        if (options.containsKey("mutationProb")) mutationProb = toDouble(options.get("mutationProb"), mutationProb);
        if (options.containsKey("distributionIndex")) distributionIndex = toDouble(options.get("distributionIndex"), distributionIndex);
        if (options.containsKey("convergenceGen")) convergenceGen = toInt(options.get("convergenceGen"), convergenceGen);
    }

    private static double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return Double.parseDouble(String.valueOf(val));
    }

    private static double toDouble(Object val, double defaultVal) {
        if (val == null) return defaultVal;
        return toDouble(val);
    }

    private static int toInt(Object val, int defaultVal) {
        if (val == null) return defaultVal;
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(String.valueOf(val));
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
}
