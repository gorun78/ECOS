package com.chinacreator.gzcm.engine.cognitive2.service.algorithm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

import org.springframework.stereotype.Component;

import static com.chinacreator.gzcm.engine.cognitive2.service.algorithm.IAlgorithmExecutor.metrics;
import static com.chinacreator.gzcm.engine.cognitive2.service.algorithm.AlgorithmSupport.*;

/** 第三类（预测与状态估计）+ 第四类（情景仿真）Java 执行器。 */
@Component
public class ForecastSimulationAlgorithms implements IAlgorithmProvider {

    private final Map<String, IAlgorithmExecutor> executors;

    public ForecastSimulationAlgorithms() {
        Map<String, IAlgorithmExecutor> m = new LinkedHashMap<>();
        m.put("linear-regression", this::linearRegression);
        m.put("glm-logistic", this::glmLogistic);
        m.put("exponential-smoothing", this::exponentialSmoothing);
        m.put("kalman-filter", this::kalmanFilter);
        m.put("hmm", this::hmm);
        m.put("monte-carlo", this::monteCarlo);
        m.put("discrete-event-sim", this::discreteEventSim);
        m.put("system-dynamics", this::systemDynamics);
        m.put("sensitivity-analysis", this::sensitivityAnalysis);
        m.put("global-uncertainty-sobol", this::globalUncertaintySobol);
        this.executors = Map.copyOf(m);
    }

    @Override
    public Map<String, IAlgorithmExecutor> executors() {
        return executors;
    }

    // ── 3.1 线性回归 ──────────────────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult linearRegression(Map<String, Object> in) {
        double[] x = doubles(in.get("x"));
        double[] y = doubles(in.get("y"));
        if (x.length < 2 || x.length != y.length) throw new IllegalArgumentException("x/y 必须等长且至少 2 个点");
        int n = x.length;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) { sx += x[i]; sy += y[i]; sxx += x[i] * x[i]; sxy += x[i] * y[i]; }
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 1e-12) throw new IllegalArgumentException("x 无方差，无法拟合");
        double slope = (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        double mu = sy / n;
        double ssTot = 0, ssRes = 0;
        for (int i = 0; i < n; i++) {
            ssTot += (y[i] - mu) * (y[i] - mu);
            double pred = intercept + slope * x[i];
            ssRes += (y[i] - pred) * (y[i] - pred);
        }
        double r2 = ssTot == 0 ? 1 : 1 - ssRes / ssTot;
        double predictX = d(in, "predictX", Double.NaN);
        Double prediction = Double.isNaN(predictX) ? null : round(intercept + slope * predictX, 8);
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("slope", round(slope, 8), "intercept", round(intercept, 8),
                        "r2", round(r2, 8), "n", n, "prediction", prediction,
                        "residualStd", round(Math.sqrt(ssRes / Math.max(1, n - 2)), 8)),
                "一元最小二乘；线性/独立同方差假设；外推超出样本区间需谨慎");
    }

    // ── 3.2 Logistic GLM（IRLS）───────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult glmLogistic(Map<String, Object> in) {
        double[] x = doubles(in.get("x"));
        double[] y = doubles(in.get("y"));
        if (x.length < 3 || x.length != y.length) throw new IllegalArgumentException("x/y 必须等长且至少 3 个点");
        double b0 = 0, b1 = 0;
        int iterations = 0;
        for (int it = 0; it < 50; it++) {
            double g0 = 0, g1 = 0, h00 = 0, h01 = 0, h11 = 0;
            for (int i = 0; i < x.length; i++) {
                double p = sigmoid(b0 + b1 * x[i]);
                double w = Math.max(1e-9, p * (1 - p));
                g0 += y[i] - p;
                g1 += (y[i] - p) * x[i];
                h00 += w;
                h01 += w * x[i];
                h11 += w * x[i] * x[i];
            }
            double det = h00 * h11 - h01 * h01;
            if (Math.abs(det) < 1e-12) break;
            double d0 = (h11 * g0 - h01 * g1) / det;
            double d1 = (-h01 * g0 + h00 * g1) / det;
            b0 += d0;
            b1 += d1;
            iterations = it + 1;
            if (Math.abs(d0) + Math.abs(d1) < 1e-8) break;
        }
        double ll = 0;
        for (int i = 0; i < x.length; i++) {
            double p = Math.min(1 - 1e-12, Math.max(1e-12, sigmoid(b0 + b1 * x[i])));
            ll += y[i] * Math.log(p) + (1 - y[i]) * Math.log(1 - p);
        }
        double predictX = d(in, "predictX", Double.NaN);
        Double prob = Double.isNaN(predictX) ? null : round(sigmoid(b0 + b1 * predictX), 8);
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("intercept", round(b0, 8), "slope", round(b1, 8),
                        "iterations", iterations, "logLikelihood", round(ll, 6), "prediction", prob),
                "二项 Logistic + IRLS；未做正则化/完全分离处理；系数解释为对数几率");
    }

    private static double sigmoid(double z) {
        if (z >= 0) { double e = Math.exp(-z); return 1 / (1 + e); }
        double e = Math.exp(z);
        return e / (1 + e);
    }

    // ── 3.3 指数平滑 ──────────────────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult exponentialSmoothing(Map<String, Object> in) {
        double[] series = doubles(in.get("series"));
        if (series.length < 2) throw new IllegalArgumentException("series 至少 2 个点");
        double alpha = d(in, "alpha", 0.3);
        double beta = d(in, "beta", 0.0);
        if (alpha <= 0 || alpha > 1) throw new IllegalArgumentException("alpha 必须在 (0,1]");
        int horizon = Math.max(1, (int) d(in, "horizon", 1));
        double level = series[0];
        double trend = series.length > 1 ? series[1] - series[0] : 0;
        double ss = 0;
        int count = 0;
        for (int t = 1; t < series.length; t++) {
            double forecast = level + trend;
            ss += (series[t] - forecast) * (series[t] - forecast);
            count++;
            double newLevel;
            if (beta > 0) {
                newLevel = alpha * series[t] + (1 - alpha) * (level + trend);
                trend = beta * (newLevel - level) + (1 - beta) * trend;
            } else {
                newLevel = alpha * series[t] + (1 - alpha) * level;
            }
            level = newLevel;
        }
        double[] fc = new double[horizon];
        for (int h = 1; h <= horizon; h++) fc[h - 1] = level + h * trend;
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("level", round(level, 8), "trend", round(trend, 8),
                        "forecast", roundList(fc, 6), "residualStd", round(Math.sqrt(ss / Math.max(1, count)), 8)),
                beta > 0 ? "Holt 线性趋势平滑；无季节项" : "简单指数平滑（SES）；无趋势/季节项");
    }

    // ── 3.4 卡尔曼滤波 ────────────────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult kalmanFilter(Map<String, Object> in) {
        double[] z = doubles(in.get("observations"));
        if (z.length == 0) throw new IllegalArgumentException("observations 不能为空");
        double q = d(in, "q", 1e-4);
        double r = d(in, "r", 1e-2);
        double x = d(in, "x0", z[0]);
        double p = d(in, "p0", 1.0);
        double[] filtered = new double[z.length];
        double[] gains = new double[z.length];
        for (int i = 0; i < z.length; i++) {
            p += q;
            double k = p / (p + r);
            x = x + k * (z[i] - x);
            p = (1 - k) * p;
            filtered[i] = x;
            gains[i] = k;
        }
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("filtered", roundList(filtered, 6), "gainLast", round(gains[gains.length - 1], 8),
                        "nextForecast", round(x, 8), "nextVariance", round(p + q, 8)),
                "一维线性高斯状态空间；q/r 由调用方给定，未做参数估计");
    }

    // ── 3.5 离散 HMM ──────────────────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult hmm(Map<String, Object> in) {
        double[] pi = doubles(in.get("pi"));
        double[][] a = matrix(in.get("transition"));
        double[][] b = matrix(in.get("emission"));
        double[] seq = doubles(in.get("observations"));
        int n = pi.length;
        if (n == 0 || a.length != n || b.length != n) throw new IllegalArgumentException("pi/transition/emission 维度不一致");
        int t = seq.length;
        if (t == 0) throw new IllegalArgumentException("observations 不能为空");

        double[][] alpha = new double[t][n];
        double[] scale = new double[t];
        for (int i = 0; i < n; i++) alpha[0][i] = pi[i] * b[i][(int) seq[0]];
        scale[0] = sum(alpha[0]);
        if (scale[0] <= 0) throw new IllegalArgumentException("初始观测概率为 0");
        for (int i = 0; i < n; i++) alpha[0][i] /= scale[0];
        for (int step = 1; step < t; step++) {
            for (int j = 0; j < n; j++) {
                double s = 0;
                for (int i = 0; i < n; i++) s += alpha[step - 1][i] * a[i][j];
                alpha[step][j] = s * b[j][(int) seq[step]];
            }
            scale[step] = sum(alpha[step]);
            if (scale[step] <= 0) throw new IllegalArgumentException("观测序列在第 " + step + " 步概率为 0");
            for (int j = 0; j < n; j++) alpha[step][j] /= scale[step];
        }
        double[][] beta = new double[t][n];
        for (int i = 0; i < n; i++) beta[t - 1][i] = 1;
        for (int step = t - 2; step >= 0; step--) {
            for (int i = 0; i < n; i++) {
                double s = 0;
                for (int j = 0; j < n; j++) s += a[i][j] * b[j][(int) seq[step + 1]] * beta[step + 1][j];
                beta[step][i] = s / scale[step + 1];
            }
        }
        double[] posterior = new double[n];
        double norm = 0;
        for (int i = 0; i < n; i++) { posterior[i] = alpha[t - 1][i] * beta[t - 1][i]; norm += posterior[i]; }
        if (norm <= 0) norm = 1;
        for (int i = 0; i < n; i++) posterior[i] /= norm;

        double[] logLik = new double[t];
        for (int step = 0; step < t; step++) logLik[step] = Math.log(scale[step]);
        double logLikelihood = 0;
        for (double v : logLik) logLikelihood += v;

        int[] obs = new int[seq.length];
        for (int i = 0; i < seq.length; i++) obs[i] = (int) seq[i];
        int[] path = viterbi(pi, a, b, obs, n);
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("lastStatePosterior", roundList(posterior, 8),
                        "viterbiPath", toList(path),
                        "logLikelihood", round(logLikelihood, 6)),
                "离散 HMM 前向-后向（缩放）+ Viterbi；参数由调用方给定，未做 Baum-Welch 训练");
    }

    private static int[] viterbi(double[] pi, double[][] a, double[][] b, int[] obs, int n) {
        int t = obs.length;
        double[][] dp = new double[t][n];
        int[][] back = new int[t][n];
        for (int i = 0; i < n; i++) dp[0][i] = Math.log(Math.max(1e-300, pi[i])) + Math.log(Math.max(1e-300, b[i][obs[0]]));
        for (int step = 1; step < t; step++) {
            for (int j = 0; j < n; j++) {
                double best = Double.NEGATIVE_INFINITY;
                int arg = 0;
                for (int i = 0; i < n; i++) {
                    double v = dp[step - 1][i] + Math.log(Math.max(1e-300, a[i][j]));
                    if (v > best) { best = v; arg = i; }
                }
                dp[step][j] = best + Math.log(Math.max(1e-300, b[j][obs[step]]));
                back[step][j] = arg;
            }
        }
        int last = 0;
        for (int i = 1; i < n; i++) if (dp[t - 1][i] > dp[t - 1][last]) last = i;
        int[] path = new int[t];
        path[t - 1] = last;
        for (int step = t - 1; step > 0; step--) path[step - 1] = back[step][path[step]];
        return path;
    }

    private static List<Integer> toList(int[] a) {
        List<Integer> out = new ArrayList<>();
        for (int v : a) out.add(v);
        return out;
    }

    private static double sum(double[] a) {
        double s = 0;
        for (double v : a) s += v;
        return s;
    }

    // ── 4.1 蒙特卡洛 ──────────────────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult monteCarlo(Map<String, Object> in) {
        List<Map<String, Object>> vars = maps(in.get("variables"));
        if (vars.isEmpty()) throw new IllegalArgumentException("variables 不能为空");
        int iterations = (int) Math.max(1, Math.min(20000, d(in, "iterations", 2000)));
        long seed = (long) d(in, "seed", 42);
        double lossThreshold = d(in, "lossThreshold", Double.NEGATIVE_INFINITY);
        Random rnd = new Random(seed);
        double[] results = new double[iterations];
        for (int i = 0; i < iterations; i++) {
            double resp = 0;
            for (Map<String, Object> v : vars) {
                double w = num(v.get("weight"), 1.0);
                double sample;
                if (v.containsKey("sd")) {
                    sample = num(v.get("mean"), 0) + num(v.get("sd"), 0) * rnd.nextGaussian();
                } else {
                    double lo = num(v.get("min"), 0);
                    double hi = num(v.get("max"), 1);
                    sample = lo + (hi - lo) * rnd.nextDouble();
                }
                resp += w * sample;
            }
            results[i] = resp;
        }
        int losses = 0;
        for (double v : results) if (v < lossThreshold) losses++;
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("mean", round(mean(results), 6),
                        "p05", round(quantile(results, 0.05), 6),
                        "p50", round(quantile(results, 0.50), 6),
                        "p95", round(quantile(results, 0.95), 6),
                        "lossProbability", round((double) losses / iterations, 6),
                        "iterations", iterations, "seed", seed),
                "变量独立采样（正态或均匀）+ 线性响应；相关性未建模；seed 固定可复现");
    }

    // ── 4.2 离散事件仿真（多服务台排队）───────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult discreteEventSim(Map<String, Object> in) {
        int servers = Math.max(1, (int) d(in, "servers", 2));
        int arrivals = (int) Math.max(1, Math.min(5000, d(in, "arrivals", 200)));
        double interMean = Math.max(1e-6, d(in, "interArrivalMean", 1.0));
        double serviceMean = Math.max(1e-6, d(in, "serviceMean", 1.2));
        long seed = (long) d(in, "seed", 42);
        Random rnd = new Random(seed);
        PriorityQueue<Double> free = new PriorityQueue<>();
        for (int i = 0; i < servers; i++) free.add(0.0);
        double now = 0;
        double totalWait = 0;
        double totalQueueArea = 0;
        double lastEvent = 0;
        int waiting = 0;
        for (int i = 0; i < arrivals; i++) {
            now += -Math.log(1 - rnd.nextDouble()) * interMean;
            double start = Math.max(now, free.poll());
            double wait = start - now;
            totalWait += wait;
            waiting++;
            double end = start + -Math.log(1 - rnd.nextDouble()) * serviceMean;
            free.add(end);
            totalQueueArea += waiting * (now - lastEvent);
            lastEvent = now;
            waiting = Math.max(0, waiting - 1);
        }
        double avgWait = totalWait / arrivals;
        double utilization = Math.min(1, arrivals * serviceMean / Math.max(1e-9, now * servers));
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("avgWait", round(avgWait, 6), "avgQueueArea", round(totalQueueArea / Math.max(1e-9, now), 6),
                        "utilization", round(utilization, 6), "served", arrivals, "horizon", round(now, 6)),
                "M/M/c 式排队（指数到达/服务）；队列长度按到达事件近似；未建模优先级/放弃");
    }

    // ── 4.3 系统动力学 ────────────────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult systemDynamics(Map<String, Object> in) {
        Map<String, Object> stocksIn = map(in.get("stocks"));
        if (stocksIn.isEmpty()) throw new IllegalArgumentException("stocks 不能为空");
        List<Map<String, Object>> flows = maps(in.get("flows"));
        double dt = d(in, "dt", 1.0);
        int steps = (int) Math.max(1, Math.min(1000, d(in, "steps", 10)));
        Map<String, Double> stocks = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : stocksIn.entrySet()) stocks.put(e.getKey(), num(e.getValue(), 0));
        List<Map<String, Object>> trajectory = new ArrayList<>();
        for (int step = 0; step <= steps; step++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<String, Double> e : stocks.entrySet()) row.put(e.getKey(), round(e.getValue(), 6));
            trajectory.add(row);
            Map<String, Double> delta = new LinkedHashMap<>();
            for (Map<String, Object> f : flows) {
                String src = s(f, "source");
                String tgt = s(f, "target");
                double rate = num(f.get("rate"), 0) * dt;
                if (src != null) delta.merge(src, -rate, Double::sum);
                if (tgt != null) delta.merge(tgt, rate, Double::sum);
            }
            for (Map.Entry<String, Double> e : delta.entrySet()) stocks.merge(e.getKey(), e.getValue(), Double::sum);
        }
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("trajectory", trajectory, "dt", dt, "steps", steps),
                "存量-流量 Euler 步进；流量速率为常数；未建模反馈回路与延迟");
    }

    // ── 4.4 敏感性分析 ────────────────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult sensitivityAnalysis(Map<String, Object> in) {
        List<Map<String, Object>> factors = maps(in.get("factors"));
        if (factors.isEmpty()) throw new IllegalArgumentException("factors 不能为空");
        double perturb = d(in, "perturbation", 0.05);
        List<Map<String, Object>> rows = new ArrayList<>();
        double total = 0;
        for (Map<String, Object> f : factors) {
            double value = num(f.get("value"), 0);
            double weight = num(f.get("weight"), 1);
            double effect = Math.abs(weight * value * perturb);
            total += effect;
            rows.add(metrics("name", s(f, "name"), "effect", round(effect, 8)));
        }
        for (Map<String, Object> row : rows) {
            double effect = num(row.get("effect"), 0);
            row.put("share", round(total == 0 ? 0 : effect / total, 6));
        }
        rows.sort((x, y) -> Double.compare(num(y.get("effect"), 0), num(x.get("effect"), 0)));
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("perturbation", perturb, "sensitivity", rows),
                "单因素 ±perturbation 线性响应；交互项未建模；weight 由调用方给定");
    }

    // ── 4.5 全局不确定性（Sobol）──────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult globalUncertaintySobol(Map<String, Object> in) {
        double[] a = doubles(in.get("A"));
        double[] b = doubles(in.get("B"));
        Map<String, Object> abMap = map(in.get("AB"));
        if (a.length == 0 || a.length != b.length || abMap.isEmpty()) {
            throw new IllegalArgumentException("A/B 必须等长且 AB 非空");
        }
        double var = var(a, false);
        if (var <= 0) throw new IllegalArgumentException("A 的方差为 0，Sobol 指数无定义");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Object> e : abMap.entrySet()) {
            double[] ab = doubles(e.getValue());
            if (ab.length != a.length) throw new IllegalArgumentException("AB[" + e.getKey() + "] 长度与 A 不一致");
            double first = 0, totalEffect = 0;
            for (int i = 0; i < a.length; i++) {
                first += b[i] * (ab[i] - a[i]);
                totalEffect += 0.5 * (a[i] - ab[i]) * (a[i] - ab[i]);
            }
            rows.add(metrics("factor", e.getKey(),
                    "firstOrder", round(first / a.length / var, 6),
                    "totalEffect", round(totalEffect / a.length / var, 6)));
        }
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("variance", round(var, 8), "indices", rows, "samples", a.length),
                "Saltelli 估计量；要求 A/B/AB 为同一模型在配套采样点上的真实输出；样本小则指数波动大");
    }
}
