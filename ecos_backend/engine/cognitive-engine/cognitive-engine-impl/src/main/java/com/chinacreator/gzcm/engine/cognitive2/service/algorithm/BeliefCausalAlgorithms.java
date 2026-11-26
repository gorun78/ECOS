package com.chinacreator.gzcm.engine.cognitive2.service.algorithm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import static com.chinacreator.gzcm.engine.cognitive2.service.algorithm.IAlgorithmExecutor.metrics;
import static com.chinacreator.gzcm.engine.cognitive2.service.algorithm.AlgorithmSupport.*;

/**
 * 第一类（信念与证据）+ 第二类（因果推断）Java 执行器。
 *
 * <p>输入输出均为 JSON 化 Map；所有数值可复现。限制写进 assumptions，禁把近似当精确。</p>
 */
@Component
public class BeliefCausalAlgorithms implements IAlgorithmProvider {

    private final Map<String, IAlgorithmExecutor> executors;

    public BeliefCausalAlgorithms() {
        Map<String, IAlgorithmExecutor> m = new LinkedHashMap<>();
        m.put("bayes-update", this::bayesUpdate);
        m.put("pgm-inference", this::pgmInference);
        m.put("evidence-fusion", this::evidenceFusion);
        m.put("confidence-calibration", this::confidenceCalibration);
        m.put("dempster-shafer", this::dempsterShafer);
        m.put("scm-linear", this::scmLinear);
        m.put("do-calculus-adjustment", this::doCalculusAdjustment);
        this.executors = Map.copyOf(m);
    }

    @Override
    public Map<String, IAlgorithmExecutor> executors() {
        return executors;
    }

    // ── 1.1 贝叶斯推理 ─────────────────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult bayesUpdate(Map<String, Object> in) {
        double[] prior = doubles(in.get("prior"));
        if (prior.length == 0) throw new IllegalArgumentException("prior 不能为空");
        double sum = 0;
        for (double p : prior) sum += p;
        if (sum <= 0) throw new IllegalArgumentException("prior 之和必须 > 0");
        double[] post = new double[prior.length];
        for (int i = 0; i < prior.length; i++) post[i] = prior[i] / sum;

        List<double[]> observations = new ArrayList<>();
        Object single = in.get("observation");
        if (single instanceof List<?> || single instanceof Object[] || single instanceof double[]) {
            observations.add(doubles(single));
        }
        if (in.get("observations") instanceof List<?> list) {
            for (Object o : list) observations.add(doubles(o));
        }
        double evidenceProb = 1.0;
        for (double[] lik : observations) {
            if (lik.length != post.length) throw new IllegalArgumentException("likelihood 长度必须与 prior 一致");
            double marginal = 0;
            for (int i = 0; i < post.length; i++) {
                post[i] = post[i] * lik[i];
                marginal += post[i];
            }
            if (marginal <= 0) throw new IllegalArgumentException("似然与先验乘积全为 0（证据与假设互斥）");
            evidenceProb *= marginal;
            for (int i = 0; i < post.length; i++) post[i] /= marginal;
        }

        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("posterior", roundList(post, 8),
                        "evidenceProbability", round(evidenceProb, 8),
                        "observations", observations.size()),
                "离散互斥假设，似然由调用方给定；未做先验敏感性分析");
    }

    // ── 1.2 概率图模型推断（二值变量全枚举）────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult pgmInference(Map<String, Object> in) {
        List<Map<String, Object>> vars = maps(in.get("variables"));
        if (vars.isEmpty()) throw new IllegalArgumentException("variables 不能为空");
        Map<String, Object> evidence = map(in.get("evidence"));
        int n = vars.size();
        String[] names = new String[n];
        List<String>[] parents = new List[n];
        List<Map<String, Object>> cpts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Map<String, Object> v = vars.get(i);
            names[i] = s(v, "name");
            if (names[i] == null) throw new IllegalArgumentException("变量缺少 name");
            parents[i] = new ArrayList<>();
            Object ps = v.get("parents");
            if (ps instanceof List<?> pl) for (Object p : pl) parents[i].add(String.valueOf(p));
            cpts.add(map(v.get("cpt")));
        }

        double[] marginals = new double[n]; // P(var=1)
        double total = 0;
        double best = -1;
        int[] bestAssign = new int[n];
        int combos = 1 << n;
        for (int mask = 0; mask < combos; mask++) {
            int[] assign = new int[n];
            for (int i = 0; i < n; i++) assign[i] = (mask >> i) & 1;
            boolean consistent = true;
            for (Map.Entry<String, Object> e : evidence.entrySet()) {
                int idx = indexOf(names, e.getKey());
                if (idx < 0) continue;
                if (assign[idx] != (int) num(e.getValue(), 0)) { consistent = false; break; }
            }
            if (!consistent) continue;
            double p = 1.0;
            for (int i = 0; i < n; i++) {
                String key = "";
                if (!parents[i].isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < parents[i].size(); k++) {
                        int pi = indexOf(names, parents[i].get(k));
                        if (pi < 0) throw new IllegalArgumentException("父变量不存在: " + parents[i].get(k));
                        if (k > 0) sb.append(',');
                        sb.append(assign[pi]);
                    }
                    key = sb.toString();
                }
                double pOne = num(cpts.get(i).get(key), 0.5);
                p *= assign[i] == 1 ? pOne : (1 - pOne);
            }
            total += p;
            for (int i = 0; i < n; i++) if (assign[i] == 1) marginals[i] += p;
            if (p > best) { best = p; bestAssign = assign; }
        }
        if (total <= 0) throw new IllegalArgumentException("证据下总概率为 0（CPT 与证据矛盾）");

        Map<String, Object> marg = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) marg.put(names[i], round(marginals[i] / total, 8));
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) map.put(names[i], bestAssign[i]);
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("marginals", marg, "mapAssignment", map, "logProbability", round(Math.log(best), 8)),
                "二值离散变量 + 全枚举精确推断；变量规模建议 <=12；CPT 键为父变量取值逗号串，值为 P(变量=1)");
    }

    // ── 1.3 证据融合 ──────────────────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult evidenceFusion(Map<String, Object> in) {
        List<Map<String, Object>> sources = maps(in.get("sources"));
        if (sources.isEmpty()) throw new IllegalArgumentException("sources 不能为空");
        double wSum = 0, pSum = 0;
        List<Map<String, Object>> contributions = new ArrayList<>();
        for (Map<String, Object> src : sources) {
            double p = num(src.get("probability"), Double.NaN);
            double w = num(src.get("weight"), 1.0);
            if (Double.isNaN(p)) throw new IllegalArgumentException("source.probability 必填");
            wSum += w;
            pSum += w * p;
            contributions.add(metrics("name", s(src, "name"), "probability", round(p, 6),
                    "weight", round(w, 6), "weighted", round(w * p, 6)));
        }
        if (wSum <= 0) throw new IllegalArgumentException("权重之和必须 > 0");
        double fused = pSum / wSum;
        double variance = 0;
        for (Map<String, Object> src : sources) {
            double p = num(src.get("probability"), 0);
            double w = num(src.get("weight"), 1.0);
            variance += w * (p - fused) * (p - fused);
        }
        variance /= wSum;
        double maxStd = 0.5; // Bernoulli 最大标准差
        double agreement = 1 - Math.min(1, Math.sqrt(variance) / maxStd);
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("fusedProbability", round(fused, 8),
                        "agreement", round(agreement, 6),
                        "effectiveWeight", round(wSum, 6),
                        "contributions", contributions),
                "按给定权重线性加权；source 需已做独立性与可信度评估；agreement 为 Bernoulli 归一化离散度");
    }

    // ── 1.4 置信度校准 ────────────────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult confidenceCalibration(Map<String, Object> in) {
        double[] probs = doubles(in.get("probabilities"));
        double[] outcomes = doubles(in.get("outcomes"));
        int bins = (int) d(in, "bins", 10);
        if (probs.length == 0 || probs.length != outcomes.length) {
            throw new IllegalArgumentException("probabilities 与 outcomes 必须等长且非空");
        }
        int[] count = new int[bins];
        double[] confSum = new double[bins];
        double[] accSum = new double[bins];
        double brier = 0;
        for (int i = 0; i < probs.length; i++) {
            double p = Math.max(0, Math.min(1, probs[i]));
            double y = outcomes[i] == 0 ? 0 : 1;
            int b = Math.min(bins - 1, (int) Math.floor(p * bins));
            count[b]++;
            confSum[b] += p;
            accSum[b] += y;
            brier += (p - y) * (p - y);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        double ece = 0;
        for (int b = 0; b < bins; b++) {
            if (count[b] == 0) continue;
            double conf = confSum[b] / count[b];
            double acc = accSum[b] / count[b];
            ece += (double) count[b] / probs.length * Math.abs(acc - conf);
            rows.add(metrics("bin", b, "count", count[b],
                    "avgConfidence", round(conf, 6), "accuracy", round(acc, 6)));
        }
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("ece", round(ece, 6), "brier", round(brier / probs.length, 6), "bins", rows),
                "等宽分箱的经验校准；样本量小（<100）时 ECE 噪声大；校准后置信需另做映射");
    }

    // ── 1.5 Dempster-Shafer ───────────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult dempsterShafer(Map<String, Object> in) {
        List<Map<String, Object>> masses = maps(in.get("masses"));
        if (masses.isEmpty()) throw new IllegalArgumentException("masses 不能为空");
        Map<Set<String>, Double> combined = normalizeMass(masses.get(0));
        double conflict = 0;
        for (int i = 1; i < masses.size(); i++) {
            Map<Set<String>, Double> next = normalizeMass(masses.get(i));
            Map<Set<String>, Double> fused = new LinkedHashMap<>();
            conflict = 0;
            for (Map.Entry<Set<String>, Double> a : combined.entrySet()) {
                for (Map.Entry<Set<String>, Double> b : next.entrySet()) {
                    Set<String> inter = new LinkedHashSet<>(a.getKey());
                    inter.retainAll(b.getKey());
                    double product = a.getValue() * b.getValue();
                    if (inter.isEmpty()) conflict += product;
                    else fused.merge(inter, product, Double::sum);
                }
            }
            if (conflict >= 1.0) {
                return IAlgorithmExecutor.AlgorithmResult.degraded(
                        "证据完全冲突（K=1），Dempster 规则不可用", metrics("conflictK", 1.0));
            }
            double norm = 1 - conflict;
            for (Map.Entry<Set<String>, Double> e : fused.entrySet()) e.setValue(e.getValue() / norm);
            combined = fused;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<Set<String>, Double> e : combined.entrySet()) {
            out.put(String.join(",", e.getKey()), round(e.getValue(), 8));
        }
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("combinedMass", out, "conflictK", round(conflict, 8)),
                "Dempster 组合规则；要求证据独立；完全冲突时判定不可用");
    }

    private static Map<Set<String>, Double> normalizeMass(Map<String, Object> mass) {
        Map<Set<String>, Double> out = new LinkedHashMap<>();
        double sum = 0;
        for (Map.Entry<String, Object> e : mass.entrySet()) {
            double v = num(e.getValue(), 0);
            if (v < 0) throw new IllegalArgumentException("mass 不能为负");
            Set<String> set = new LinkedHashSet<>();
            if (!e.getKey().isBlank()) for (String x : e.getKey().split(",")) set.add(x.trim());
            out.merge(set, v, Double::sum);
            sum += v;
        }
        if (sum <= 0) throw new IllegalArgumentException("mass 之和必须 > 0");
        if (Math.abs(sum - 1) > 1e-6) for (Map.Entry<Set<String>, Double> e : out.entrySet()) e.setValue(e.getValue() / sum);
        return out;
    }

    // ── 2.1 线性 SCM + do 干预 ────────────────────────────────────────────

    IAlgorithmExecutor.AlgorithmResult scmLinear(Map<String, Object> in) {
        List<Map<String, Object>> eqs = maps(in.get("equations"));
        if (eqs.isEmpty()) throw new IllegalArgumentException("equations 不能为空");
        Map<String, Object> means = map(in.get("means"));
        Map<String, Object> intervention = map(in.get("intervention"));

        Map<String, Double> before = new LinkedHashMap<>();
        Map<String, Double> after = new LinkedHashMap<>();
        for (Map<String, Object> eq : eqs) {
            String target = s(eq, "target");
            if (target == null) throw new IllegalArgumentException("equation 缺少 target");
            double intercept = num(eq.get("intercept"), 0);
            Map<String, Object> coef = map(eq.get("coefficients"));
            before.put(target, evalEquation(target, intercept, coef, before, means, null));
            after.put(target, evalEquation(target, intercept, coef, after, means, intervention));
        }
        Map<String, Object> delta = new LinkedHashMap<>();
        for (String k : before.keySet()) delta.put(k, round(after.get(k) - before.get(k), 6));
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("before", roundMap(before), "after", roundMap(after), "delta", delta,
                        "intervention", intervention),
                "线性结构方程 + 给定拓扑序；外生变量取 means；未做噪声/置信区间估计");
    }

    private static double evalEquation(String target, double intercept, Map<String, Object> coef,
                                       Map<String, Double> solved, Map<String, Object> means,
                                       Map<String, Object> intervention) {
        double v = intercept;
        for (Map.Entry<String, Object> c : coef.entrySet()) {
            String var = c.getKey();
            double value;
            if (intervention != null && var.equals(String.valueOf(intervention.get("variable")))) {
                value = num(intervention.get("value"), 0);
            } else if (solved.containsKey(var)) {
                value = solved.get(var);
            } else {
                value = num(means.get(var), 0);
            }
            v += num(c.getValue(), 0) * value;
        }
        return v;
    }

    // ── 2.2 后门调整（do-calculus 的可计算子集）────────────────────────────

    IAlgorithmExecutor.AlgorithmResult doCalculusAdjustment(Map<String, Object> in) {
        List<Map<String, Object>> rows = maps(in.get("rows"));
        if (rows.isEmpty()) throw new IllegalArgumentException("rows 不能为空");
        String treatment = s(in, "treatment");
        String outcome = s(in, "outcome");
        if (treatment == null || outcome == null) throw new IllegalArgumentException("treatment/outcome 必填");
        List<String> adjust = new ArrayList<>();
        if (in.get("adjust") instanceof List<?> list) for (Object o : list) adjust.add(String.valueOf(o));

        Map<String, List<Double>> strataY1 = new LinkedHashMap<>();
        Map<String, List<Double>> strataY0 = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            StringBuilder key = new StringBuilder();
            for (String a : adjust) {
                if (key.length() > 0) key.append('|');
                key.append(String.valueOf(row.get(a)));
            }
            double t = num(row.get(treatment), Double.NaN);
            double y = num(row.get(outcome), Double.NaN);
            if (Double.isNaN(t) || Double.isNaN(y)) continue;
            Map<String, List<Double>> bucket = t >= 0.5 ? strataY1 : strataY0;
            bucket.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(y);
        }
        double ate = 0;
        int strata = 0;
        List<Map<String, Object>> detail = new ArrayList<>();
        for (String key : strataY1.keySet()) {
            if (!strataY0.containsKey(key)) continue;
            double n1 = strataY1.get(key).size();
            double n0 = strataY0.get(key).size();
            double p = (n1 + n0) / rows.size();
            double diff = avg(strataY1.get(key)) - avg(strataY0.get(key));
            ate += p * diff;
            strata++;
            detail.add(metrics("stratum", key, "weight", round(p, 6), "effect", round(diff, 6)));
        }
        if (strata == 0) {
            return IAlgorithmExecutor.AlgorithmResult.degraded(
                    "没有同时含处理组与对照组的可比层", metrics("rows", rows.size()));
        }
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("ate", round(ate, 6), "strata", strata,
                        "adjustedRows", rows.size(), "detail", detail),
                "后门准则需由调用方保证（adjust 覆盖全部混杂）；连续混杂按精确值分层，样本稀疏层会被丢弃");
    }

    private static double avg(List<Double> values) {
        double s = 0;
        for (double v : values) s += v;
        return values.isEmpty() ? 0 : s / values.size();
    }

    private static Map<String, Object> roundMap(Map<String, Double> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : in.entrySet()) out.put(e.getKey(), round(e.getValue(), 6));
        return out;
    }

    private static int indexOf(String[] names, String key) {
        for (int i = 0; i < names.length; i++) if (names[i].equals(key)) return i;
        return -1;
    }
}
