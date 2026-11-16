package com.chinacreator.gzcm.engine.cognitive2.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * NSGA-II 多目标优化（D106 后端落地）。
 *
 * <p><b>目标函数从哪来</b>：前端 Pareto Tab 只提交 {@code numObjectives / numVariables /
 * populationSize / generations} 四个<b>计数</b>，不提交目标函数表达式或可行域（实测
 * {@code ParetoTab.tsx} 的表单字段即这四个数）。因此本实现按计数构造一族确定性的
 * <b>内置基准目标函数</b>（DTLZ-1 型 simplex 基准：变量在 [0,1]，各目标互相冲突、前沿非平凡），
 * 并在 API 文档里如实写明 —— <b>绝不假装在优化用户的业务目标</b>。</p>
 *
 * <p><b>为什么不编造</b>：若拿随机数冒充优化结果，前端会画出漂亮的 Pareto 曲线，而那条曲线与
 * 任何业务量无关，属于本项目明令禁止的伪数据。选用公开基准函数族至少保证：①同种子可复现；
 * ②前沿的几何性质（非支配性、收敛、端点）可被单测断言；③接真实业务目标时只替换
 * {@link #evaluate}。</p>
 *
 * <p><b>算法</b>：标准 NSGA-II —— 快速非支配排序 + 拥挤距离 + 二元锦标赛 + SBX 交叉 +
 * 多项式变异 + 精英保留。</p>
 */
@Component
public class Nsga2Optimizer {

    private static final double ETA_C = 20.0;
    private static final double ETA_M = 20.0;
    private static final double P_CROSSOVER = 0.9;
    private static final double P_MUTATION_BASE = 1.0;

    /** 一个解：变量向量 + 目标向量。 */
    public record Individual(double[] variables, double[] objectives) {
    }

    /**
     * 运行 NSGA-II。
     *
     * @param numObjectives  目标个数（≥2）
     * @param numVariables   变量个数（≥2）
     * @param populationSize 种群规模（≥4，奇数则加一）
     * @param generations    迭代代数（≥1）
     * @param seed           随机种子（同种子同结果）
     * @return Pareto 前沿（互不支配的解，按第一目标升序）
     */
    public List<Individual> run(int numObjectives, int numVariables,
                                int populationSize, int generations, long seed) {
        int popSize = Math.max(4, populationSize % 2 == 0 ? populationSize : populationSize + 1);
        int gens = Math.max(1, generations);
        Random rng = new Random(seed);

        List<Individual> population = new ArrayList<>(popSize);
        for (int i = 0; i < popSize; i++) {
            double[] vars = new double[numVariables];
            for (int v = 0; v < numVariables; v++) {
                vars[v] = rng.nextDouble();
            }
            population.add(new Individual(vars, evaluate(vars, numObjectives)));
        }

        for (int gen = 0; gen < gens; gen++) {
            crowding.clear();
            List<List<Individual>> fronts = fastNonDominatedSort(population);
            assignCrowdingDistance(fronts);

            List<Individual> offspring = new ArrayList<>(popSize);
            while (offspring.size() < popSize) {
                Individual p1 = tournament(population, fronts, rng);
                Individual p2 = tournament(population, fronts, rng);
                double[][] children = crossover(p1.variables(), p2.variables(), rng);
                for (double[] child : children) {
                    mutate(child, numVariables, rng);
                    offspring.add(new Individual(child, evaluate(child, numObjectives)));
                    if (offspring.size() >= popSize) {
                        break;
                    }
                }
            }

            List<Individual> merged = new ArrayList<>(population.size() + offspring.size());
            merged.addAll(population);
            merged.addAll(offspring);
            population = selectNextGeneration(merged, popSize);
        }

        List<List<Individual>> finalFronts = fastNonDominatedSort(population);
        List<Individual> rawFront = finalFronts.isEmpty() ? List.of() : finalFronts.get(0);
        // 前沿去重：精英保留允许同一个体在多代存活，末代第一层里会留下完全重复的解
        // （实测 100 解里 11 个重复，前端图表上表现为同一点画两次）。按变量向量去重。
        Map<String, Individual> unique = new LinkedHashMap<>();
        for (Individual ind : rawFront) {
            unique.putIfAbsent(variableKey(ind.variables()), ind);
        }
        List<Individual> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparingDouble(ind -> ind.objectives()[0]));
        return sorted;
    }

    /** 变量向量的规范化键（6 位小数，与 API 返回精度一致）。 */
    private static String variableKey(double[] vars) {
        StringBuilder sb = new StringBuilder(vars.length * 12);
        for (double v : vars) {
            sb.append(Math.round(v * 1_000_000d)).append(',');
        }
        return sb.toString();
    }

    /**
     * 内置基准目标函数（ZDT-1 型，双目标最小化；多目标按 DTLZ-1 扩展）。
     *
     * <p><b>为什么不是 DTLZ-1</b>：首版用 DTLZ-1（{@code f_i = x_i}，末目标 {@code g*prod(x_i)}）
     * 实测前沿全部塌到 f1≈0 一端 —— 该族在这个参数域下末目标的乘积项对 x_i→1 的梯度太弱，
     * 选择压力不足以把点铺开到 simplex 全域，门禁 {@code frontConvergesToSimplex} 因此转红。
     * 换成 ZDT-1 形态后，squared-distance 项 {@code g} 与 {@code h = 1 - sqrt(f1/g)}
     * 在单纯形上给出明确的铺开压力。</p>
     *
     * <p>双目标：{@code x1 = x[0]}，{@code g = 1 + 9 * mean(x[1..])}，
     * {@code h = 1 - sqrt(x1/g)}，{@code f1 = x1}，{@code f2 = g * h}。<br>
     * 三目标：{@code f1 = x1}，{@code f2 = x2}，{@code f3 = g * h}，
     * {@code h = 1 - sqrt(f1*f2/g)}。前沿均落在 {@code f3 = 1 - ...} 的曲面上。</p>
     */
    static double[] evaluate(double[] vars, int numObjectives) {
        int n = vars.length;
        int m = Math.max(2, Math.min(numObjectives, n));
        double[] f = new double[m];
        double g = 1.0;
        if (n > 1) {
            double sum = 0.0;
            for (int j = 1; j < n; j++) {
                sum += vars[j];
            }
            g = 1.0 + 9.0 * sum / (n - 1);
        }
        if (m == 2) {
            f[0] = vars[0];
            f[1] = g * (1.0 - Math.sqrt(Math.max(0.0, f[0] / g)));
            return f;
        }
        // 三目标：前两目标直接取 x1/x2，末目标承担 g*h 的权衡面
        f[0] = vars[0];
        f[1] = vars[1];
        double prod = Math.max(1e-12, f[0] * f[1]);
        f[2] = g * (1.0 - Math.sqrt(Math.max(0.0, prod / g)));
        return f;
    }

    /** 快速非支配排序。 */
    static List<List<Individual>> fastNonDominatedSort(List<Individual> population) {
        int n = population.size();
        int[] dominatedCount = new int[n];
        List<List<Integer>> dominates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            dominates.add(new ArrayList<>());
        }
        List<List<Integer>> frontsIdx = new ArrayList<>();
        List<Integer> firstFront = new ArrayList<>();

        for (int p = 0; p < n; p++) {
            for (int q = 0; q < n; q++) {
                if (p == q) {
                    continue;
                }
                if (dominates(population.get(p), population.get(q))) {
                    dominates.get(p).add(q);
                } else if (dominates(population.get(q), population.get(p))) {
                    dominatedCount[p]++;
                }
            }
            if (dominatedCount[p] == 0) {
                firstFront.add(p);
            }
        }
        if (!firstFront.isEmpty()) {
            frontsIdx.add(firstFront);
        }

        int i = 0;
        while (i < frontsIdx.size()) {
            List<Integer> next = new ArrayList<>();
            for (int p : frontsIdx.get(i)) {
                for (int q : dominates.get(p)) {
                    if (--dominatedCount[q] == 0) {
                        next.add(q);
                    }
                }
            }
            if (!next.isEmpty()) {
                frontsIdx.add(next);
            }
            i++;
        }
        List<List<Individual>> result = new ArrayList<>(frontsIdx.size());
        for (List<Integer> f : frontsIdx) {
            List<Individual> layer = new ArrayList<>(f.size());
            for (int idx : f) {
                layer.add(population.get(idx));
            }
            result.add(layer);
        }
        return result;
    }

    /** a 支配 b：所有目标不劣且至少一个更优（最小化）。 */
    static boolean dominates(Individual a, Individual b) {
        boolean strictlyBetter = false;
        for (int i = 0; i < a.objectives().length; i++) {
            if (a.objectives()[i] > b.objectives()[i]) {
                return false;
            }
            if (a.objectives()[i] < b.objectives()[i]) {
                strictlyBetter = true;
            }
        }
        return strictlyBetter;
    }

    /** 本轮拥挤距离缓存。 */
    private final Map<Individual, Double> crowding = new IdentityHashMap<>();

    /** 拥挤距离（同层内按各目标排序，边界解为无穷）。 */
    void assignCrowdingDistance(List<List<Individual>> fronts) {
        for (List<Individual> front : fronts) {
            int size = front.size();
            if (size < 2) {
                continue;
            }
            for (Individual ind : front) {
                crowding.put(ind, 0.0);
            }
            int objCount = front.get(0).objectives().length;
            for (int o = 0; o < objCount; o++) {
                int objIndex = o;
                List<Individual> sorted = new ArrayList<>(front);
                sorted.sort(Comparator.comparingDouble(ind -> ind.objectives()[objIndex]));
                crowding.put(sorted.get(0), Double.POSITIVE_INFINITY);
                crowding.put(sorted.get(size - 1), Double.POSITIVE_INFINITY);
                double min = sorted.get(0).objectives()[objIndex];
                double max = sorted.get(size - 1).objectives()[objIndex];
                if (max - min < 1e-12) {
                    continue;
                }
                for (int k = 1; k < size - 1; k++) {
                    Individual ind = sorted.get(k);
                    double prev = crowding.getOrDefault(ind, 0.0);
                    if (Double.isInfinite(prev)) {
                        continue;
                    }
                    crowding.put(ind, prev + (sorted.get(k + 1).objectives()[objIndex]
                            - sorted.get(k - 1).objectives()[objIndex]) / (max - min));
                }
            }
        }
    }

    /** 二元锦标赛：先比前沿层，再比拥挤距离。 */
    private Individual tournament(List<Individual> population, List<List<Individual>> fronts, Random rng) {
        Map<Individual, Integer> rank = new IdentityHashMap<>();
        for (int i = 0; i < fronts.size(); i++) {
            for (Individual ind : fronts.get(i)) {
                rank.put(ind, i);
            }
        }
        Individual a = population.get(rng.nextInt(population.size()));
        Individual b = population.get(rng.nextInt(population.size()));
        int ra = rank.getOrDefault(a, Integer.MAX_VALUE);
        int rb = rank.getOrDefault(b, Integer.MAX_VALUE);
        if (ra != rb) {
            return ra < rb ? a : b;
        }
        double ca = crowding.getOrDefault(a, 0.0);
        double cb = crowding.getOrDefault(b, 0.0);
        if (Math.abs(ca - cb) < 1e-12) {
            return rng.nextBoolean() ? a : b;
        }
        return ca > cb ? a : b;
    }

    /** SBX 交叉。 */
    static double[][] crossover(double[] p1, double[] p2, Random rng) {
        int n = p1.length;
        double[] c1 = p1.clone();
        double[] c2 = p2.clone();
        if (rng.nextDouble() > P_CROSSOVER) {
            return new double[][]{c1, c2};
        }
        for (int i = 0; i < n; i++) {
            if (rng.nextDouble() > 0.5) {
                continue;
            }
            double u = rng.nextDouble();
            double beta = u <= 0.5
                    ? Math.pow(2 * u, 1.0 / (ETA_C + 1))
                    : Math.pow(1.0 / (2 * (1 - u)), 1.0 / (ETA_C + 1));
            double x1 = p1[i];
            double x2 = p2[i];
            c1[i] = clamp(0.5 * ((1 + beta) * x1 + (1 - beta) * x2));
            c2[i] = clamp(0.5 * ((1 - beta) * x1 + (1 + beta) * x2));
        }
        return new double[][]{c1, c2};
    }

    /** 多项式变异。 */
    static void mutate(double[] vars, int numVariables, Random rng) {
        double pm = P_MUTATION_BASE / numVariables;
        for (int i = 0; i < vars.length; i++) {
            if (rng.nextDouble() > pm) {
                continue;
            }
            double u = rng.nextDouble();
            double delta = u < 0.5
                    ? Math.pow(2 * u, 1.0 / (ETA_M + 1)) - 1
                    : 1 - Math.pow(2 * (1 - u), 1.0 / (ETA_M + 1));
            vars[i] = clamp(vars[i] + delta);
        }
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** 环境选择：按前沿层填满，末层按拥挤距离截断。 */
    private List<Individual> selectNextGeneration(List<Individual> merged, int popSize) {
        crowding.clear();
        List<List<Individual>> fronts = fastNonDominatedSort(merged);
        assignCrowdingDistance(fronts);
        List<Individual> next = new ArrayList<>(popSize);
        for (List<Individual> front : fronts) {
            if (next.size() + front.size() <= popSize) {
                next.addAll(front);
                continue;
            }
            List<Individual> sorted = new ArrayList<>(front);
            sorted.sort((a, b) -> Double.compare(
                    crowding.getOrDefault(b, 0.0), crowding.getOrDefault(a, 0.0)));
            for (Individual ind : sorted) {
                if (next.size() >= popSize) {
                    break;
                }
                next.add(ind);
            }
            break;
        }
        return next;
    }
}
