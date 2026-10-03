package com.chinacreator.gzcm.engine.kb.profile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 画像纯统计计算（F04-06 / C.8：本服务是唯一允许的纯算法 Service）。
 * <p>
 * 只做确定性数值计算，不碰 DB / 线程 / 配置，便于纯 JUnit 覆盖：
 * <ul>
 *   <li>分位数 P10/P50/P90（线性插值，与 numpy `linear` 一致）；</li>
 *   <li>样本均值 / 样本标准差 → t 近似 95% 置信区间（n&lt;30 用 t，n≥30 用正态≈1.96）；</li>
 *   <li>缺失率 missingRate（value=null 的样本占比）。</li>
 * </ul>
 *
 * @author ECOS KB Team
 */
public final class ProfileStatsCalculator {

    /** 置信水平 95% 的临界：n≥30 用正态 1.96（近似不动点）。 */
    private static final double Z95 = 1.96;
    private static final int T_APPROX_M = 4;

    private ProfileStatsCalculator() {
    }

    /** 一组样本的统计结果（与 ProfileStatsVO 字段对齐）。 */
    public static class Result {
        public long sampleCount;       // 有效（非缺失）样本数
        public long totalSamples;      // 总样本数（含缺失）
        public BigDecimal p10;
        public BigDecimal p50;
        public BigDecimal p90;
        public BigDecimal mean;
        public BigDecimal ciLow;
        public BigDecimal ciHigh;
        public BigDecimal missingRate;

        public Result(long sampleCount, long totalSamples, BigDecimal p10, BigDecimal p50,
                      BigDecimal p90, BigDecimal mean, BigDecimal ciLow, BigDecimal ciHigh,
                      BigDecimal missingRate) {
            this.sampleCount = sampleCount;
            this.totalSamples = totalSamples;
            this.p10 = p10;
            this.p50 = p50;
            this.p90 = p90;
            this.mean = mean;
            this.ciLow = ciLow;
            this.ciHigh = ciHigh;
            this.missingRate = missingRate;
        }

        /** 有效样本数（用于退化阈值 n≥30/10≤n<30/n<10）。 */
        public long n() {
            return sampleCount;
        }
    }

    /**
     * 计算一组样本的分布统计。{@code values} 中 null 代表缺失样本。
     *
     * @param values 原始值流（含 null）
     */
    public static Result compute(List<BigDecimal> values) {
        if (values == null) {
            values = Collections.emptyList();
        }
        long total = values.size();
        List<BigDecimal> present = new ArrayList<>();
        for (BigDecimal v : values) {
            if (v != null) {
                present.add(v);
            }
        }
        long n = present.size();
        BigDecimal missingRate = round4(total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(1.0 - (n / (double) total)));

        if (n == 0) {
            return new Result(0, total, null, null, null, null, null, null, missingRate);
        }
        Collections.sort(present);
        BigDecimal p10 = percentile(present, 0.10);
        BigDecimal p50 = percentile(present, 0.50);
        BigDecimal p90 = percentile(present, 0.90);
        BigDecimal mean = mean(present);
        // 置信区间
        BigDecimal ciLow;
        BigDecimal ciHigh;
        if (n >= 2) {
            double std = stdDev(present, mean.doubleValue());
            double tcrit = n >= 30 ? Z95 : tApprox((int) n);
            double half = tcrit * std / Math.sqrt(n);
            double lo = mean.doubleValue() - half;
            double hi = mean.doubleValue() + half;
            ciLow = BigDecimal.valueOf(lo).setScale(4, RoundingMode.HALF_UP);
            ciHigh = BigDecimal.valueOf(hi).setScale(4, RoundingMode.HALF_UP);
        } else {
            // n==1：CI 退化为点估计
            ciLow = p50;
            ciHigh = p50;
        }
        return new Result(n, total, p10, p50, p90, mean, ciLow, ciHigh, missingRate);
    }

    /** 线性插值分位数（values 需已升序）。p 取值 [0,1)。 */
    static BigDecimal percentile(List<BigDecimal> sortedAsc, double p) {
        if (sortedAsc.isEmpty()) {
            return null;
        }
        if (sortedAsc.size() == 1) {
            return sortedAsc.get(0);
        }
        double idx = p * (sortedAsc.size() - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) {
            return sortedAsc.get(lo);
        }
        double frac = idx - lo;
        double v = sortedAsc.get(lo).doubleValue() + (sortedAsc.get(hi).doubleValue() - sortedAsc.get(lo).doubleValue()) * frac;
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }

    static BigDecimal mean(List<BigDecimal> vals) {
        double sum = 0;
        for (BigDecimal v : vals) {
            sum += v.doubleValue();
        }
        return BigDecimal.valueOf(sum / vals.size()).setScale(4, RoundingMode.HALF_UP);
    }

    static double stdDev(List<BigDecimal> vals, double mean) {
        int n = vals.size();
        if (n < 2) {
            return 0;
        }
        double ss = 0;
        for (BigDecimal v : vals) {
            double d = v.doubleValue() - mean;
            ss += d * d;
        }
        // 样本标准差（n-1）
        return Math.sqrt(ss / (n - 1));
    }

    /** t 分布 95% 双尾近似（小样本）。用查表法覆盖常见 n，其余回退 1.96 保守下界。 */
    static double tApprox(int n) {
        double[] table = {
                0,        // n=0 (unused)
                12.706,   // n=1
                4.303,    // n=2
                3.182,    // n=3
                2.776,    // n=4
                2.571,    // n=5
                2.447,    // n=6
                2.365,    // n=7
                2.306,    // n=8
                2.262,    // n=9
                2.228,    // n=10
                2.201,    // n=11
                2.179,    // n=12
                2.160,    // n=13
                2.145,    // n=14
                2.131,    // n=15
                2.120,    // n=16
                2.110,    // n=17
                2.101,    // n=18
                2.093,    // n=19
                2.086,    // n=20
                2.080,    // n=21
                2.074,    // n=22
                2.069,    // n=23
                2.064,    // n=24
                2.060,    // n=25
                2.056,    // n=26
                2.052,    // n=27
                2.048,    // n=28
                2.045,    // n=29
                2.042,    // n=30
        };
        if (n <= 0) {
            return Z95;
        }
        if (n < table.length) {
            return table[n];
        }
        return Z95;
    }

    static BigDecimal round4(BigDecimal v) {
        if (v == null) {
            return null;
        }
        return v.setScale(T_APPROX_M, RoundingMode.HALF_UP);
    }
}
