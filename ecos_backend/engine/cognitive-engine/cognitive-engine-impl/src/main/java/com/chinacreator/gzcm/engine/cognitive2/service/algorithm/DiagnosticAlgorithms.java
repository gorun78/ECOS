package com.chinacreator.gzcm.engine.cognitive2.service.algorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Component;

import static com.chinacreator.gzcm.engine.cognitive2.service.algorithm.IAlgorithmExecutor.metrics;
import static com.chinacreator.gzcm.engine.cognitive2.service.algorithm.AlgorithmSupport.*;

/** 第六类：认知结构、异常诊断与假设验证（Java）。 */
@Component
public class DiagnosticAlgorithms implements IAlgorithmProvider {

    private final Map<String, IAlgorithmExecutor> executors;

    public DiagnosticAlgorithms() {
        Map<String, IAlgorithmExecutor> m = new LinkedHashMap<>();
        m.put("graph-traversal", this::graphTraversal);
        m.put("path-analysis", this::pathAnalysis);
        m.put("graph-centrality", this::graphCentrality);
        m.put("clustering", this::clustering);
        m.put("anomaly-detection", this::anomalyDetection);
        m.put("hypothesis-testing", this::hypothesisTesting);
        m.put("drift-changepoint", this::driftChangepoint);
        this.executors = Map.copyOf(m);
    }

    @Override
    public Map<String, IAlgorithmExecutor> executors() {
        return executors;
    }

    IAlgorithmExecutor.AlgorithmResult graphTraversal(Map<String, Object> in) {
        Map<String, List<String>> adj = adjacency(in.get("edges"));
        String start = s(in, "start");
        if (start == null) throw new IllegalArgumentException("start 必填");
        Deque<String> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        Map<String, Integer> depth = new LinkedHashMap<>();
        queue.add(start);
        visited.add(start);
        depth.put(start, 0);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            for (String next : adj.getOrDefault(cur, List.of())) {
                if (visited.add(next)) { depth.put(next, depth.get(cur) + 1); queue.add(next); }
            }
        }
        int maxDepth = 0;
        for (int d : depth.values()) maxDepth = Math.max(maxDepth, d);
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("order", new ArrayList<>(visited), "reachable", visited.size(),
                        "depth", depth, "maxDepth", maxDepth),
                "有向图 BFS；重复边去重；不保证加权最短路径");
    }

    IAlgorithmExecutor.AlgorithmResult pathAnalysis(Map<String, Object> in) {
        Map<String, List<String[]>> adj = weightedAdjacency(in.get("edges"));
        String source = s(in, "source");
        String target = s(in, "target");
        if (source == null || target == null) throw new IllegalArgumentException("source/target 必填");
        Map<String, Double> dist = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        PriorityQueue<String> pq = new PriorityQueue<>(
                (x, y) -> Double.compare(dist.getOrDefault(x, Double.MAX_VALUE), dist.getOrDefault(y, Double.MAX_VALUE)));
        dist.put(source, 0.0);
        pq.add(source);
        while (!pq.isEmpty()) {
            String cur = pq.poll();
            for (String[] edge : adj.getOrDefault(cur, List.of())) {
                double nd = dist.get(cur) + Double.parseDouble(edge[1]);
                if (nd < dist.getOrDefault(edge[0], Double.MAX_VALUE)) {
                    dist.put(edge[0], nd);
                    prev.put(edge[0], cur);
                    pq.add(edge[0]);
                }
            }
        }
        if (!dist.containsKey(target)) {
            return IAlgorithmExecutor.AlgorithmResult.degraded(
                    "目标不可达", metrics("source", source, "target", target));
        }
        List<String> path = new ArrayList<>();
        for (String cur = target; cur != null; cur = prev.get(cur)) {
            path.add(0, cur);
            if (cur.equals(source)) break;
        }
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("cost", round(dist.get(target), 6), "path", path, "visitedNodes", dist.size()),
                "Dijkstra 最短路径（非负权重）；边格式 [from,to,weight]");
    }

    IAlgorithmExecutor.AlgorithmResult graphCentrality(Map<String, Object> in) {
        Map<String, List<String>> adj = adjacency(in.get("edges"));
        Set<String> nodes = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> e : adj.entrySet()) {
            nodes.add(e.getKey());
            nodes.addAll(e.getValue());
        }
        if (nodes.isEmpty()) throw new IllegalArgumentException("edges 不能为空");
        int n = nodes.size();
        double damping = d(in, "damping", 0.85);
        int iterations = (int) d(in, "iterations", 30);
        Map<String, Double> pr = new LinkedHashMap<>();
        for (String node : nodes) pr.put(node, 1.0 / n);
        for (int it = 0; it < iterations; it++) {
            Map<String, Double> next = new LinkedHashMap<>();
            double dangling = 0;
            for (String node : nodes) if (adj.getOrDefault(node, List.of()).isEmpty()) dangling += pr.get(node);
            for (String node : nodes) {
                double sum = 0;
                for (String other : nodes) {
                    List<String> outs = adj.getOrDefault(other, List.of());
                    if (!outs.isEmpty() && outs.contains(node)) sum += pr.get(other) / outs.size();
                }
                next.put(node, (1 - damping) / n + damping * (sum + dangling / n));
            }
            pr = next;
        }
        Map<String, Object> degree = new LinkedHashMap<>();
        for (String node : nodes) {
            int inDeg = 0;
            for (String other : nodes) if (adj.getOrDefault(other, List.of()).contains(node)) inDeg++;
            degree.put(node, metrics("out", adj.getOrDefault(node, List.of()).size(), "in", inDeg));
        }
        List<Map<String, Object>> prRows = new ArrayList<>();
        for (Map.Entry<String, Double> e : pr.entrySet()) {
            prRows.add(metrics("node", e.getKey(), "pagerank", round(e.getValue(), 6)));
        }
        prRows.sort((x, y) -> Double.compare(num(y.get("pagerank"), 0), num(x.get("pagerank"), 0)));
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("pagerank", prRows, "degree", degree, "iterations", iterations),
                "有向图 PageRank 幂迭代 + 出入度；介数中心性未实现（大图开销高）");
    }

    IAlgorithmExecutor.AlgorithmResult clustering(Map<String, Object> in) {
        double[][] points = matrix(in.get("points"));
        int k = (int) d(in, "k", 2);
        long seed = (long) d(in, "seed", 42);
        int iterations = (int) d(in, "iterations", 50);
        if (points.length < 2 || k < 1 || points.length < k) throw new IllegalArgumentException("points 数量必须 >= k >= 1");
        int dim = points[0].length;
        // 确定性最远点初始化：首个质心取首点，其后取离已选质心最远的点（避免两质心挤在同簇）
        double[][] centroids = new double[k][dim];
        System.arraycopy(points[0], 0, centroids[0], 0, dim);
        for (int c = 1; c < k; c++) {
            int best = 0;
            double bestDist = -1;
            for (int i = 0; i < points.length; i++) {
                double min = Double.MAX_VALUE;
                for (int j = 0; j < c; j++) min = Math.min(min, dist2(points[i], centroids[j]));
                if (min > bestDist) { bestDist = min; best = i; }
            }
            System.arraycopy(points[best], 0, centroids[c], 0, dim);
        }
        int[] labels = new int[points.length];
        for (int it = 0; it < iterations; it++) {
            boolean changed = false;
            for (int i = 0; i < points.length; i++) {
                int best = 0;
                for (int c = 1; c < k; c++) if (dist2(points[i], centroids[c]) < dist2(points[i], centroids[best])) best = c;
                if (labels[i] != best) { labels[i] = best; changed = true; }
            }
            double[][] sums = new double[k][dim];
            int[] counts = new int[k];
            for (int i = 0; i < points.length; i++) {
                counts[labels[i]]++;
                for (int d = 0; d < dim; d++) sums[labels[i]][d] += points[i][d];
            }
            for (int c = 0; c < k; c++) {
                if (counts[c] == 0) continue;
                for (int d = 0; d < dim; d++) centroids[c][d] = sums[c][d] / counts[c];
            }
            if (!changed) break;
        }
        double silhouette = 0;
        for (int i = 0; i < points.length; i++) {
            double[] nearest = new double[k];
            Arrays.fill(nearest, Double.POSITIVE_INFINITY);
            int[] cCount = new int[k];
            for (int j = 0; j < points.length; j++) {
                if (i == j) continue;
                int c = labels[j];
                cCount[c]++;
                nearest[c] += Math.sqrt(dist2(points[i], points[j]));
            }
            double a = cCount[labels[i]] == 0 ? 0 : nearest[labels[i]] / cCount[labels[i]];
            double b = Double.POSITIVE_INFINITY;
            for (int c = 0; c < k; c++) if (c != labels[i] && cCount[c] > 0) b = Math.min(b, nearest[c] / cCount[c]);
            double s = Math.max(a, b) == 0 ? 0 : (b - a) / Math.max(a, b);
            silhouette += s;
        }
        List<Map<String, Object>> centroidRows = new ArrayList<>();
        for (double[] c : centroids) centroidRows.add(metrics("centroid", roundList(c, 6)));
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("labels", toIntList(labels), "centroids", centroidRows,
                        "silhouette", round(silhouette / points.length, 6), "k", k),
                "k-means（固定种子，欧氏距离）；k 需人工给定；轮廓系数 O(n²)，样本建议 <2000");
    }

    private static double dist2(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += (a[i] - b[i]) * (a[i] - b[i]);
        return s;
    }

    private static List<Integer> toIntList(int[] a) {
        List<Integer> out = new ArrayList<>();
        for (int v : a) out.add(v);
        return out;
    }

    IAlgorithmExecutor.AlgorithmResult anomalyDetection(Map<String, Object> in) {
        double[] series = doubles(in.get("series"));
        if (series.length < 3) throw new IllegalArgumentException("series 至少 3 个点");
        String method = s(in, "method");
        method = method == null ? "zscore" : method.toLowerCase();
        double threshold = d(in, "threshold", "iqr".equals(method) ? 1.5 : 3.0);
        List<Integer> anomalies = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        if ("iqr".equals(method)) {
            double q1 = quantile(series, 0.25);
            double q3 = quantile(series, 0.75);
            double iqr = q3 - q1;
            for (int i = 0; i < series.length; i++) {
                double low = q1 - threshold * iqr;
                double high = q3 + threshold * iqr;
                double score = iqr == 0 ? 0 : Math.max(0, Math.max(low - series[i], series[i] - high) / iqr);
                scores.add(round(score, 6));
                if (score > 0) anomalies.add(i);
            }
        } else {
            double mu = mean(series);
            double sd = std(series, true);
            for (int i = 0; i < series.length; i++) {
                double score = sd == 0 ? 0 : Math.abs(series[i] - mu) / sd;
                scores.add(round(score, 6));
                if (score > threshold) anomalies.add(i);
            }
        }
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("method", method, "threshold", threshold,
                        "anomalyIndices", anomalies, "scores", scores),
                "z-score 或 IQR 阈值法；非平稳/趋势序列需先差分；score 为标准化偏离度");
    }

    IAlgorithmExecutor.AlgorithmResult hypothesisTesting(Map<String, Object> in) {
        double[] sample1 = doubles(in.get("sample1"));
        double[] sample2 = doubles(in.get("sample2"));
        double alpha = d(in, "alpha", 0.05);
        String correction = s(in, "correction");
        List<Double> pValues = new ArrayList<>();
        double statistic;
        String test;
        if (sample2.length == 0) {
            if (sample1.length < 3) throw new IllegalArgumentException("sample1 至少 3 个点");
            double mu0 = d(in, "mu0", 0);
            double se = std(sample1, true) / Math.sqrt(sample1.length);
            statistic = se == 0 ? 0 : (mean(sample1) - mu0) / se;
            test = "one-sample-z";
        } else {
            if (sample1.length < 3 || sample2.length < 3) throw new IllegalArgumentException("两组样本各至少 3 个点");
            double se = Math.sqrt(var(sample1, true) / sample1.length + var(sample2, true) / sample2.length);
            statistic = se == 0 ? 0 : (mean(sample1) - mean(sample2)) / se;
            test = "two-sample-z";
        }
        pValues.add(twoSidedP(statistic));
        if (in.get("pValues") instanceof List<?> list) {
            for (Object o : list) pValues.add(num(o, 1.0));
        }
        int m = pValues.size();
        List<Map<String, Object>> adjusted = new ArrayList<>();
        if ("holm".equalsIgnoreCase(correction)) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < m; i++) order.add(i);
            order.sort((x, y) -> Double.compare(pValues.get(x), pValues.get(y)));
            double prev = 0;
            for (int rank = 0; rank < m; rank++) {
                int idx = order.get(rank);
                double adj = Math.min(1.0, Math.max(prev, pValues.get(idx) * (m - rank)));
                prev = adj;
                adjusted.add(metrics("index", idx, "pValue", round(pValues.get(idx), 6),
                        "adjusted", round(adj, 6), "reject", adj < alpha));
            }
            adjusted.sort((x, y) -> Integer.compare((int) num(x.get("index"), 0), (int) num(y.get("index"), 0)));
        } else {
            for (int i = 0; i < m; i++) {
                double adj = Math.min(1.0, pValues.get(i) * m);
                adjusted.add(metrics("index", i, "pValue", round(pValues.get(i), 6),
                        "adjusted", round(adj, 6), "reject", adj < alpha));
            }
        }
        double primaryAdjusted = num(adjusted.get(0).get("adjusted"), 1.0);
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("test", test, "statistic", round(statistic, 6),
                        "pValue", round(pValues.get(0), 6), "adjustedPValue", round(primaryAdjusted, 6),
                        "alpha", alpha, "correction", correction == null ? "bonferroni" : correction,
                        "reject", primaryAdjusted < alpha, "all", adjusted),
                "正态近似 z 检验（未做小样本 t 校正）；多重校正默认 Bonferroni，可传 pValues 一并校正");
    }

    IAlgorithmExecutor.AlgorithmResult driftChangepoint(Map<String, Object> in) {
        double[] series = doubles(in.get("series"));
        if (series.length < 5) throw new IllegalArgumentException("series 至少 5 个点");
        int baseline = (int) d(in, "baseline", Math.min(10, Math.max(3, series.length / 4)));
        baseline = Math.min(baseline, series.length - 1);
        double[] base = Arrays.copyOf(series, baseline);
        double mu = mean(base);
        double sigma = Math.max(1e-9, std(base, true));
        double k = d(in, "allowance", 0.5);
        double h = d(in, "threshold", 5.0);
        double pos = 0, neg = 0;
        List<Integer> changepoints = new ArrayList<>();
        List<Map<String, Object>> stats = new ArrayList<>();
        for (int i = baseline; i < series.length; i++) {
            pos = Math.max(0, pos + (series[i] - mu) / sigma - k);
            neg = Math.max(0, neg + (mu - series[i]) / sigma - k);
            stats.add(metrics("index", i, "cusumPos", round(pos, 4), "cusumNeg", round(neg, 4)));
            if (pos > h || neg > h) {
                changepoints.add(i);
                pos = 0;
                neg = 0;
            }
        }
        return IAlgorithmExecutor.AlgorithmResult.ok(
                metrics("baselineMean", round(mu, 6), "baselineStd", round(sigma, 6),
                        "changepoints", changepoints, "allowance", k, "threshold", h, "stats", stats),
                "双侧 CUSUM；基线取前 baseline 个点；k/h 需按业务噪声调参；未做统计显著性检验");
    }

    // ── 图结构解析：边格式 [[from,to]] 或 [[from,to,weight]] ───────────────

    private static Map<String, List<String>> adjacency(Object edges) {
        Map<String, List<String>> adj = new LinkedHashMap<>();
        if (edges instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof List<?> row) || row.size() < 2) continue;
                String from = String.valueOf(row.get(0));
                String to = String.valueOf(row.get(1));
                adj.computeIfAbsent(from, x -> new ArrayList<>()).add(to);
            }
        }
        return adj;
    }

    private static Map<String, List<String[]>> weightedAdjacency(Object edges) {
        Map<String, List<String[]>> adj = new LinkedHashMap<>();
        if (edges instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof List<?> row) || row.size() < 2) continue;
                String from = String.valueOf(row.get(0));
                String to = String.valueOf(row.get(1));
                String weight = row.size() >= 3 ? String.valueOf(row.get(2)) : "1";
                adj.computeIfAbsent(from, x -> new ArrayList<>()).add(new String[]{to, weight});
            }
        }
        return adj;
    }
}
