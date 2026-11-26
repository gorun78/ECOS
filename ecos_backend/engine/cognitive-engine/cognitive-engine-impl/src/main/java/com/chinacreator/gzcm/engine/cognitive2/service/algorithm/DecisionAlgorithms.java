package com.chinacreator.gzcm.engine.cognitive2.service.algorithm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.cognitive2.service.Nsga2Optimizer;

import static com.chinacreator.gzcm.engine.cognitive2.service.algorithm.IAlgorithmExecutor.metrics;
import static com.chinacreator.gzcm.engine.cognitive2.service.algorithm.AlgorithmSupport.*;

/** 第五类：决策、优化与方案排序（Java）。 */
@Component
public class DecisionAlgorithms implements IAlgorithmProvider {

    private static final double[] RI = {0, 0, 0.58, 0.90, 1.12, 1.24, 1.32, 1.41, 1.45, 1.49};

    private final Map<String, IAlgorithmExecutor> executors;
    private final Nsga2Optimizer nsga2;

    public DecisionAlgorithms(Nsga2Optimizer nsga2) {
        this.nsga2 = nsga2;
        Map<String, IAlgorithmExecutor> m = new LinkedHashMap<>();
        m.put("linear-programming", this::linearProgramming);
        m.put("multi-objective-pareto", this::multiObjectivePareto);
        m.put("ahp", this::ahp);
        m.put("topsis", this::topsis);
        m.put("dynamic-programming", this::dynamicProgramming);
        m.put("reinforcement-learning", this::reinforcementLearning);
        this.executors = Map.copyOf(m);
    }

    @Override
    public Map<String, IAlgorithmExecutor> executors() {
        return executors;
    }

    IAlgorithmExecutor.AlgorithmResult linearProgramming(Map<String, Object> in) {
        double[] c = doubles(in.get("c"));
        double[][] a = matrix(in.get("A"));
        double[] b = doubles(in.get("b"));
        boolean maximize = b(in, "maximize", true);
        if (c.length == 0 || a.length != b.length || b.length == 0) {
            throw new IllegalArgumentException("c/A/b 维度不一致");
        }
        int n = c.length;
        int m = b.length;
        for (double[] row : a) if (row.length != n) throw new IllegalArgumentException("A 每行列数必须等于 c 长度");

        double[][] t = new double[m + 1][n + m + 1];
        for (int i = 0; i < m; i++) {
            if (b[i] < 0) {
                return IAlgorithmExecutor.AlgorithmResult.degraded(
                        "存在负右端项，本执行器只处理 b>=0 的标准形", metrics());
            }
            System.arraycopy(a[i], 0, t[i], 0, n);
            t[i][n + i] = 1;
            t[i][n + m] = b[i];
        }
        for (int j = 0; j < n; j++) t[m][j] = maximize ? -c[j] : c[j];
        int[] basis = new int[m];
        for (int i = 0; i < m; i++) basis[i] = n + i;

        int iterations = 0;
        while (iterations < 2000) {
            int enter = -1;
            for (int j = 0; j <= n + m; j++) if (t[m][j] < -1e-10) { enter = j; break; }
            if (enter < 0) break;
            int leave = -1;
            double bestRatio = Double.POSITIVE_INFINITY;
            for (int i = 0; i < m; i++) {
                if (t[i][enter] > 1e-10) {
                    double ratio = t[i][n + m] / t[i][enter];
                    if (ratio < bestRatio - 1e-12) { bestRatio = ratio; leave = i; }
                }
            }
            if (leave < 0) {
                return IAlgorithmExecutor.AlgorithmResult.degraded(
                        "线性规划无界", metrics("status", "UNBOUNDED", "iterations", iterations));
            }
            pivot(t, leave, enter);
            basis[leave] = enter;
            iterations++;
        }
        double[] x = new double[n];
        for (int i = 0; i < m; i++) if (basis[i] < n) x[basis[i]] = t[i][n + m];
        double objective = maximize ? t[m][n + m] : -t[m][n + m];
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("x", roundList(x, 6), "objective", round(objective, 6),
                        "iterations", iterations, "status", "OPTIMAL"),
                "标准形 max cᵀx s.t. Ax<=b, x>=0；规模建议 <=50；不处理等式约束与整数变量");
    }

    private static void pivot(double[][] t, int row, int col) {
        double p = t[row][col];
        for (int j = 0; j < t[row].length; j++) t[row][j] /= p;
        for (int i = 0; i < t.length; i++) {
            if (i == row) continue;
            double factor = t[i][col];
            if (Math.abs(factor) < 1e-15) continue;
            for (int j = 0; j < t[i].length; j++) t[i][j] -= factor * t[row][j];
        }
    }

    IAlgorithmExecutor.AlgorithmResult multiObjectivePareto(Map<String, Object> in) {
        int m = (int) d(in, "numObjectives", 2);
        int n = (int) d(in, "numVariables", 2);
        int pop = (int) d(in, "populationSize", 40);
        int gens = (int) d(in, "generations", 40);
        long seed = (long) d(in, "seed", 42L);
        if (m < 2 || m > 3 || n < 2 || n > 5 || pop < 4 || pop > 2000 || gens < 1 || gens > 500) {
            throw new IllegalArgumentException("numObjectives 2~3 / numVariables 2~5 / populationSize 4~2000 / generations 1~500");
        }
        List<Nsga2Optimizer.Individual> front = nsga2.run(m, n, pop, gens, seed);
        List<Map<String, Object>> solutions = new ArrayList<>();
        for (Nsga2Optimizer.Individual ind : front) {
            Map<String, Object> vars = new LinkedHashMap<>();
            for (int i = 0; i < ind.variables().length; i++) vars.put("x" + (i + 1), round(ind.variables()[i], 6));
            Map<String, Object> objs = new LinkedHashMap<>();
            for (int i = 0; i < ind.objectives().length; i++) objs.put("f" + (i + 1), round(ind.objectives()[i], 6));
            solutions.add(metrics("variables", vars, "objectives", objs));
        }
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("frontSize", front.size(), "solutions", solutions),
                "NSGA-II 在 DTLZ-1 型基准目标函数上求前沿；不是用户业务目标，接业务目标需替换 evaluate");
    }

    IAlgorithmExecutor.AlgorithmResult ahp(Map<String, Object> in) {
        double[][] a = matrix(in.get("matrix"));
        int n = a.length;
        if (n < 2) throw new IllegalArgumentException("判断矩阵至少 2x2");
        for (double[] row : a) if (row.length != n) throw new IllegalArgumentException("判断矩阵必须为方阵");
        double[] w = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double prod = 1;
            for (int j = 0; j < n; j++) prod *= Math.max(1e-12, a[i][j]);
            w[i] = Math.pow(prod, 1.0 / n);
            sum += w[i];
        }
        for (int i = 0; i < n; i++) w[i] /= sum;
        double lambda = 0;
        for (int i = 0; i < n; i++) {
            double aw = 0;
            for (int j = 0; j < n; j++) aw += a[i][j] * w[j];
            lambda += aw / w[i];
        }
        lambda /= n;
        double ci = (lambda - n) / (n - 1);
        double cr = n - 1 < RI.length && RI[n - 1] > 0 ? ci / RI[n - 1] : 0;
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("weights", roundList(w, 6), "lambdaMax", round(lambda, 6),
                        "ci", round(ci, 6), "cr", round(cr, 6), "consistent", cr < 0.1),
                "几何平均法求权重；CR<0.1 视为通过一致性；RI 采用 1~9 阶标准表");
    }

    IAlgorithmExecutor.AlgorithmResult topsis(Map<String, Object> in) {
        double[][] x = matrix(in.get("matrix"));
        double[] weights = doubles(in.get("weights"));
        List<?> benefitIn = in.get("benefit") instanceof List<?> l ? l : List.of();
        int m = x.length;
        if (m == 0) throw new IllegalArgumentException("matrix 不能为空");
        int n = x[0].length;
        if (weights.length != n) throw new IllegalArgumentException("weights 长度必须等于准则数");
        double[] norm = new double[n];
        for (int j = 0; j < n; j++) {
            double s = 0;
            for (int i = 0; i < m; i++) s += x[i][j] * x[i][j];
            norm[j] = Math.sqrt(s);
        }
        double[][] v = new double[m][n];
        double[] best = new double[n];
        double[] worst = new double[n];
        for (int j = 0; j < n; j++) {
            boolean benefit = j >= benefitIn.size() || Boolean.parseBoolean(String.valueOf(benefitIn.get(j)));
            best[j] = benefit ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            worst[j] = benefit ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
            for (int i = 0; i < m; i++) {
                v[i][j] = norm[j] == 0 ? 0 : weights[j] * x[i][j] / norm[j];
                best[j] = benefit ? Math.max(best[j], v[i][j]) : Math.min(best[j], v[i][j]);
                worst[j] = benefit ? Math.min(worst[j], v[i][j]) : Math.max(worst[j], v[i][j]);
            }
        }
        double[] closeness = new double[m];
        for (int i = 0; i < m; i++) {
            double dp = 0, dn = 0;
            for (int j = 0; j < n; j++) {
                dp += (v[i][j] - best[j]) * (v[i][j] - best[j]);
                dn += (v[i][j] - worst[j]) * (v[i][j] - worst[j]);
            }
            dp = Math.sqrt(dp);
            dn = Math.sqrt(dn);
            closeness[i] = dp + dn == 0 ? 0 : dn / (dp + dn);
        }
        Map<String, Object> ranking = sortedRank(List.of(), closeness);
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("closeness", roundList(closeness, 6), "ranking", ranking.get("ranking")),
                "向量归一 + 欧氏距离；权重与效益型/成本型方向由调用方给定");
    }

    IAlgorithmExecutor.AlgorithmResult dynamicProgramming(Map<String, Object> in) {
        List<Map<String, Object>> items = maps(in.get("items"));
        int capacity = (int) d(in, "capacity", 0);
        if (items.isEmpty() || capacity <= 0) throw new IllegalArgumentException("items 非空且 capacity>0");
        int n = items.size();
        int[] wt = new int[n];
        double[] val = new double[n];
        for (int i = 0; i < n; i++) {
            wt[i] = (int) num(items.get(i).get("weight"), 0);
            val[i] = num(items.get(i).get("value"), 0);
            if (wt[i] < 0) throw new IllegalArgumentException("weight 不能为负");
        }
        double[][] dp = new double[n + 1][capacity + 1];
        for (int i = 1; i <= n; i++) {
            for (int w = 0; w <= capacity; w++) {
                dp[i][w] = dp[i - 1][w];
                if (wt[i - 1] <= w) dp[i][w] = Math.max(dp[i][w], dp[i - 1][w - wt[i - 1]] + val[i - 1]);
            }
        }
        List<Integer> chosen = new ArrayList<>();
        int w = capacity;
        for (int i = n; i > 0; i--) {
            if (dp[i][w] > dp[i - 1][w] + 1e-12) { chosen.add(i - 1); w -= wt[i - 1]; }
        }
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("maxValue", round(dp[n][capacity], 6), "selectedIndices", chosen,
                        "capacity", capacity, "itemCount", n),
                "0/1 背包 DP；容量与重量需为整数；未建模多维约束");
    }

    IAlgorithmExecutor.AlgorithmResult reinforcementLearning(Map<String, Object> in) {
        List<String> actions = new ArrayList<>();
        if (in.get("actions") instanceof List<?> l) for (Object o : l) actions.add(String.valueOf(o));
        List<String> states = new ArrayList<>();
        if (in.get("states") instanceof List<?> l) for (Object o : l) states.add(String.valueOf(o));
        Map<String, Object> rewards = map(in.get("reward"));
        String start = s(in, "start");
        int episodes = (int) Math.max(1, Math.min(5000, d(in, "episodes", 200)));
        int maxSteps = (int) Math.max(1, Math.min(200, d(in, "maxStepsPerEpisode", 20)));
        double alpha = d(in, "alpha", 0.1);
        double gamma = d(in, "gamma", 0.9);
        double epsilon = d(in, "epsilon", 0.1);
        long seed = (long) d(in, "seed", 42);
        if (actions.isEmpty() || states.isEmpty()) throw new IllegalArgumentException("states/actions 不能为空");
        Random rnd = new Random(seed);
        Map<String, double[]> q = new LinkedHashMap<>();
        for (String st : states) q.put(st, new double[actions.size()]);
        double lastReturn = 0;
        for (int ep = 0; ep < episodes; ep++) {
            String st = start == null ? states.get(0) : start;
            double total = 0;
            for (int step = 0; step < maxSteps; step++) {
                int ai = rnd.nextDouble() < epsilon ? rnd.nextInt(actions.size()) : argmax(q.get(st));
                double reward = num(rewards.get(st + ":" + actions.get(ai)), 0);
                String next = nextState(in, st, ai, states);
                double maxNext = 0;
                for (double v : q.get(next)) maxNext = Math.max(maxNext, v);
                q.get(st)[ai] += alpha * (reward + gamma * maxNext - q.get(st)[ai]);
                total += reward;
                st = next;
            }
            lastReturn = total;
        }
        Map<String, Object> policy = new LinkedHashMap<>();
        Map<String, Object> qTable = new LinkedHashMap<>();
        for (String st : states) {
            policy.put(st, actions.get(argmax(q.get(st))));
            qTable.put(st, roundList(q.get(st), 4));
        }
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("policy", policy, "qTable", qTable, "lastEpisodeReturn", round(lastReturn, 6),
                        "episodes", episodes),
                "确定性转移的表格 Q-learning（epsilon-greedy）；状态/动作需由调用方枚举；未做策略评估");
    }

    private static String nextState(Map<String, Object> in, String state, int action, List<String> states) {
        if (in.get("next") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> row = map(o);
                if (state.equals(s(row, "state")) && action == (int) num(row.get("action"), -1)) {
                    String next = s(row, "next");
                    if (next != null) return next;
                }
            }
        }
        int idx = states.indexOf(state);
        return states.get(Math.min(states.size() - 1, idx + 1));
    }

    private static int argmax(double[] a) {
        int idx = 0;
        for (int i = 1; i < a.length; i++) if (a[i] > a[idx]) idx = i;
        return idx;
    }
}
