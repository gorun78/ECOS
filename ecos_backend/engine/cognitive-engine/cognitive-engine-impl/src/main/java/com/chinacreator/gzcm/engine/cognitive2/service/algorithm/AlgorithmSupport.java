package com.chinacreator.gzcm.engine.cognitive2.service.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 算法执行器的纯函数工具（无状态、无 Spring、无 DB）。 */
public final class AlgorithmSupport {

    private AlgorithmSupport() {}

    public static double num(Object o, double def) {
        if (o instanceof Number n) return n.doubleValue();
        if (o instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (NumberFormatException ignore) { return def; }
        }
        return def;
    }

    public static double d(Map<String, Object> m, String key, double def) {
        return m == null ? def : num(m.get(key), def);
    }

    public static String s(Map<String, Object> m, String key) {
        if (m == null || m.get(key) == null) return null;
        String v = String.valueOf(m.get(key));
        return v.isBlank() ? null : v;
    }

    public static boolean b(Map<String, Object> m, String key, boolean def) {
        Object v = m == null ? null : m.get(key);
        if (v instanceof Boolean bo) return bo;
        if (v instanceof String str) return Boolean.parseBoolean(str);
        return def;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> maps(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> list) {
            for (Object x : list) {
                if (x instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    public static double[] doubles(Object o) {
        if (o instanceof double[] d) return d;
        if (o instanceof Object[] arr) {
            double[] out = new double[arr.length];
            for (int i = 0; i < arr.length; i++) out[i] = num(arr[i], Double.NaN);
            return out;
        }
        if (o instanceof List<?> list) {
            double[] out = new double[list.size()];
            for (int i = 0; i < list.size(); i++) out[i] = num(list.get(i), Double.NaN);
            return out;
        }
        return new double[0];
    }

    public static double[][] matrix(Object o) {
        if (o instanceof List<?> rows) {
            double[][] out = new double[rows.size()][];
            for (int i = 0; i < rows.size(); i++) out[i] = doubles(rows.get(i));
            return out;
        }
        return new double[0][];
    }

    public static double mean(double[] a) {
        if (a.length == 0) return 0;
        double s = 0;
        for (double v : a) s += v;
        return s / a.length;
    }

    public static double var(double[] a, boolean sample) {
        if (a.length < 2) return 0;
        double mu = mean(a);
        double s = 0;
        for (double v : a) s += (v - mu) * (v - mu);
        return s / (sample ? a.length - 1 : a.length);
    }

    public static double std(double[] a, boolean sample) {
        return Math.sqrt(var(a, sample));
    }

    public static double quantile(double[] a, double q) {
        if (a.length == 0) return Double.NaN;
        double[] c = Arrays.copyOf(a, a.length);
        Arrays.sort(c);
        double pos = Math.max(0, Math.min(1, q)) * (c.length - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) return c[lo];
        return c[lo] + (c[hi] - c[lo]) * (pos - lo);
    }

    public static double round(double v, int digits) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return v;
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }

    public static List<Double> roundList(double[] a, int digits) {
        List<Double> out = new ArrayList<>(a.length);
        for (double v : a) out.add(round(v, digits));
        return out;
    }

    /** 标准正态 CDF（Abramowitz-Stegun 7.1.26，误差 < 1.5e-7）。 */
    public static double normalCdf(double z) {
        double x = Math.abs(z) / Math.sqrt(2);
        double t = 1.0 / (1.0 + 0.3275911 * x);
        double erf = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return z >= 0 ? 0.5 * (1 + erf) : 0.5 * (1 - erf);
    }

    /** 双侧 p 值（正态近似）。 */
    public static double twoSidedP(double z) {
        return 2 * (1 - normalCdf(Math.abs(z)));
    }

    public static Map<String, Object> sortedRank(List<String> names, double[] scores) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) idx.add(i);
        idx.sort((x, y) -> Double.compare(scores[y], scores[x]));
        List<Map<String, Object>> ranking = new ArrayList<>();
        for (int i = 0; i < idx.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", i + 1);
            row.put("name", i < names.size() ? names.get(i) == null ? "item" + (idx.get(i) + 1) : names.get(idx.get(i)) : "item" + (idx.get(i) + 1));
            row.put("score", round(scores[idx.get(i)], 6));
            ranking.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ranking", ranking);
        return out;
    }

    public static double[] logProbs(double[] p) {
        double[] out = new double[p.length];
        for (int i = 0; i < p.length; i++) out[i] = Math.log(Math.max(1e-300, p[i]));
        return out;
    }
}
