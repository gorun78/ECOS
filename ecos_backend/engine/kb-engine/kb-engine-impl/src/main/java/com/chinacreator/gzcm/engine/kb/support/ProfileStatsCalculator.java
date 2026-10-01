package com.chinacreator.gzcm.engine.kb.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * REQ-KB-02 历史画像统计量 + 分层退化纯算法（详细设计-04 F04-06，全新）。
 *
 * <p>纯函数，无 DB/LLM 依赖，直接可单测：
 * <ul>
 *   <li>分位数 P10/P50/P90（线性插值）、均值、t 近似 95% 置信区间；</li>
 *   <li>置信度分级 n≥30 HIGH / 10≤n&lt;30 MEDIUM / n&lt;10 触发退化；</li>
 *   <li>退化链 {@code 项目×部门×环节 → 项目类型×部门×环节 → 项目类型×环节 → 部门×环节 → 全局×环节}。</li>
 * </ul>
 */
public final class ProfileStatsCalculator {

    /** 高置信样本下限。 */
    public static final int HIGH_CONFIDENCE_MIN = 30;
    /** 中置信样本下限（低于即触发退化）。 */
    public static final int LOW_CONFIDENCE_MIN = 10;

    // 退化链维度组合（从细到粗），键顺序固定。
    private static final String[][] DEGRADE_CHAIN = {
            {"project", "dept", "stage"},
            {"project_type", "dept", "stage"},
            {"project_type", "stage"},
            {"dept", "stage"},
            {"global", "stage"},
    };

    private ProfileStatsCalculator() {
    }

    /** 单组统计输出（BASE 置信度由样本量与退化深度共同决定）。 */
    public record Stats(int sampleCount, double missingRate, double p10, double p50, double p90,
                        double mean, double ciLow, double ciHigh, String confidence, int degradeDepth) {
    }

    /** 全退化链维度组（可用于 UI 可视化与 resolve 对账）。 */
    public static List<List<String>> degradeChain() {
        List<List<String>> out = new ArrayList<>(DEGRADE_CHAIN.length);
        for (String[] combo : DEGRADE_CHAIN) {
            out.add(List.of(combo));
        }
        return out;
    }

    /** 取退化链中第 {@code depth} 组的维度 key 列表（越界时取最粗组）。 */
    public static List<String> chainLevel(int depth) {
        int d = Math.max(0, Math.min(depth, DEGRADE_CHAIN.length - 1));
        return List.of(DEGRADE_CHAIN[d]);
    }

    /**
     * 对一组观测值计算 P10/P50/P90/均值/95%CI 与置信度。
     *
     * @param values 观测值（null / 空白项记为缺失，计入 missingRate，但不参与统计）
     * @return 统计结果；有效样本为 0 时 sampleCount=0、confidence=LOW
     */
    public static Stats compute(List<Number> values) {
        List<Double> vals = new ArrayList<>();
        int total = 0;
        int missing = 0;
        if (values != null) {
            for (Number n : values) {
                total++;
                if (n == null || n.doubleValue() != n.doubleValue()) { // NaN 视为缺失
                    missing++;
                } else {
                    vals.add(n.doubleValue());
                }
            }
        }
        int n = vals.size();
        double missingRate = total == 0 ? 0.0 : (double) missing / total;
        if (n == 0) {
            return new Stats(0, missingRate, 0, 0, 0, 0, 0, 0, "LOW", 0);
        }
        vals.sort(Double::compareTo);
        double mean = vals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double sd = stddev(vals, mean);
        double p10 = percentile(vals, 0.10);
        double p50 = percentile(vals, 0.50);
        double p90 = percentile(vals, 0.90);
        // t 近似 95% CI（小样本用 t 值近似表，n≥30 用 z≈1.96）
        double tVal = tValue(n);
        double half = tVal * sd / Math.sqrt(n);
        return new Stats(n, missingRate, p10, p50, p90, mean, mean - half, mean + half,
                confidence(n), 0);
    }

    /** 置信度分级：n≥30 HIGH / 10≤n<30 MEDIUM / n<10 LOW（触发退化）。 */
    public static String confidence(int n) {
        if (n >= HIGH_CONFIDENCE_MIN) {
            return "HIGH";
        }
        if (n >= LOW_CONFIDENCE_MIN) {
            return "MEDIUM";
        }
        return "LOW";
    }

    /** 是否需要进一步退化（样本低于 MEDIUM 门槛）。 */
    public static boolean shouldDegrade(int n) {
        return n < LOW_CONFIDENCE_MIN;
    }

    /** 判断给定维度键集合是否等于退化链某一层（用于 resolve 归一）。 */
    public static int matchChainLevel(String[] dims) {
        if (dims == null) {
            return DEGRADE_CHAIN.length - 1;
        }
        String key = String.join("|", upper(dims));
        for (int i = 0; i < DEGRADE_CHAIN.length; i++) {
            if (String.join("|", upper(DEGRADE_CHAIN[i])).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    // ── 内部工具 ─────────────────────────────────────────────────

    private static String[] upper(String[] in) {
        String[] out = new String[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i] == null ? "" : in[i].toUpperCase(Locale.ROOT);
        }
        return out;
    }

    /** 线性插值分位（vals 须已升序）。 */
    static double percentile(List<Double> sorted, double q) {
        int n = sorted.size();
        if (n == 1) {
            return sorted.get(0);
        }
        double pos = q * (n - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) {
            return sorted.get(lo);
        }
        double frac = pos - lo;
        return sorted.get(lo) + frac * (sorted.get(hi) - sorted.get(lo));
    }

    static double stddev(List<Double> vals, double mean) {
        int n = vals.size();
        if (n < 2) {
            return 0.0;
        }
        double sumSq = 0.0;
        for (double v : vals) {
            double d = v - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / (n - 1));
    }

    /** 小样本 t 值近似（95% 双侧）：n 越大越逼近 1.96。 */
    static double tValue(int n) {
        if (n >= 30) {
            return 1.96;
        }
        // 经验近似（Goodman 表离线值，覆盖 n≥2）
        double t = switch (n) {
            case 2 -> 12.706;
            case 3 -> 4.303;
            case 4 -> 3.182;
            case 5 -> 2.776;
            case 6 -> 2.571;
            case 7 -> 2.447;
            case 8 -> 2.365;
            case 9 -> 2.262;
            case 10 -> 2.228;
            case 11 -> 2.201;
            case 12 -> 2.179;
            case 13 -> 2.160;
            case 14 -> 2.145;
            case 15 -> 2.131;
            case 16 -> 2.120;
            case 17 -> 2.110;
            case 18 -> 2.101;
            case 19 -> 2.093;
            case 20 -> 2.086;
            case 21 -> 2.080;
            case 22 -> 2.074;
            case 23 -> 2.069;
            case 24 -> 2.064;
            case 25 -> 2.060;
            case 26 -> 2.056;
            case 27 -> 2.052;
            case 28 -> 2.048;
            case 29 -> 2.045;
            default -> 1.96;
        };
        return t;
    }
}
