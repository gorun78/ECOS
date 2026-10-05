package com.chinacreator.gzcm.engine.cognitive2.fc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 分册09 F09-13 · 回测五指标 + 阈值驱动复审路由（§3.4 / C206 / C207 / C208 / R-66①）。
 *
 * <p>五指标：MAE / MAPE / 偏差方向 / 区间覆盖率 / 数据覆盖率。</p>
 *
 * <p><b>除零守卫</b>（W225/C207，红线）：<i>剔除</i>实际为 0 的行（不从分母扣减也不以 1 兜底），
 * 计 {@link BacktestReport#zeroActualCount()} 披露；MAPE 若 {@code n<5} ⇒ 打 LOW_SAMPLE 标记
 * 并<b>不</b>告警（§3.4）。</p>
 *
 * <p><b>阈值配置</b>（R-66①）：{@link Thresholds} 由调用者提供，来自 sysman 配置门面
 * （{@code ecos.fc.backtest.review_threshold.*}）——本类不接受服务端默认伴生调用者篡改，
 * 若 caller 传 null，本类 fail-loud 抛 {@link IllegalStateException}（禁默认即放行语义）。</p>
 */
public final class FcBacktestCalc {

    public record SampleRow(BigDecimal forecast, BigDecimal actual,
                             BigDecimal p10, BigDecimal p90) {
        public SampleRow(BigDecimal f, BigDecimal a) { this(f, a, null, null); }
    }

    /** 阈值配置（R-66①）：由 caller 经 sysman 配置门面显式传入，禁止默认伴生。 */
    public static final class Thresholds {
        private final BigDecimal mapePct;          // 默认 15% (caller supplies)
        private final BigDecimal coveragePct;      // 默认 80% (caller supplies)
        private final int minSample;               // 默认 5
        public Thresholds(BigDecimal mapePct, BigDecimal coveragePct, int minSample) {
            if (mapePct == null || coveragePct == null) {
                throw new IllegalArgumentException("thresholds null: caller 须经 sysman 配置门面显式传入 (R-66①)");
            }
            if (minSample <= 0) throw new IllegalArgumentException("minSample must > 0");
            this.mapePct = mapePct;
            this.coveragePct = coveragePct;
            this.minSample = minSample;
        }
        public BigDecimal mapePct() { return mapePct; }
        public BigDecimal coveragePct() { return coveragePct; }
        public int minSample() { return minSample; }
    }

    /** 复审建议：role ≠ "NONE" 表示应经 ITaskManagementService 生成复审任务（production 侧接线）。 */
    public record ReviewAdvice(boolean required, String role, String reason) {}

    public record BacktestReport(BigDecimal mae, BigDecimal mape,
                                  String biasDirection, BigDecimal intervalCoverage,
                                  BigDecimal dataCoverage, int zeroActualCount, int sampleCount,
                                  String sampleFlag, ReviewAdvice review) {}

    public static BacktestReport compute(String period, String granularity,
                                         String projectId, String departmentId, String stage,
                                         List<SampleRow> samples, Thresholds thresholds) {
        if (thresholds == null) throw new IllegalStateException("thresholds 缺省禁止（R-66①），caller 须经 sysman 配置门面显式传入");
        List<SampleRow> valid = new ArrayList<>();
        int zeroActual = 0;
        for (SampleRow r : samples) {
            if (r.forecast() == null || r.actual() == null) continue;
            if (r.actual().compareTo(BigDecimal.ZERO) == 0) { zeroActual++; continue; }
            valid.add(r);
        }
        int n = valid.size();
        String sampleFlag = n < thresholds.minSample() ? "LOW_SAMPLE" : null;
        // 数据覆盖率 = 有效样本数 / 到达的原始行数（含 zero_actual 剔除）
        int arrived = samples == null ? 0 : samples.size();
        BigDecimal dataCoverage = arrived == 0
                ? null
                : BigDecimal.valueOf(n).divide(BigDecimal.valueOf(arrived), 4, RoundingMode.HALF_UP);
        // MAE / MAPE / 方向 / 区间覆盖率
        BigDecimal absErrorSum = BigDecimal.ZERO;
        BigDecimal absPctSum = BigDecimal.ZERO;
        BigDecimal diffSum = BigDecimal.ZERO;        // 有向差
        BigDecimal intervalBad = BigDecimal.ZERO;   // 落点 [P10,P90] 外样本数
        BigDecimal mae = null, mape = null, cov = null;
        String direction = null;
        if (n > 0) {
            for (SampleRow r : valid) {
                BigDecimal d = r.forecast().subtract(r.actual());
                BigDecimal absD = d.abs();
                absErrorSum = absErrorSum.add(absD);
                absPctSum = absPctSum.add(absD.divide(r.actual().abs(), 6, RoundingMode.HALF_UP));
                diffSum = diffSum.add(d);
                if (r.p10() != null && r.p90() != null) {
                    boolean inside = r.actual().compareTo(r.p10()) >= 0 && r.actual().compareTo(r.p90()) <= 0;
                    if (!inside) intervalBad = intervalBad.add(BigDecimal.ONE);
                } else {
                    // 未提供区间 ⇒ 记失败样本（覆盖率口径保守）
                    intervalBad = intervalBad.add(BigDecimal.ONE);
                }
            }
            mae = absErrorSum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
            mape = absPctSum.divide(BigDecimal.valueOf(n), 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
            direction = diffSum.signum() > 0 ? "OVER" : (diffSum.signum() < 0 ? "UNDER" : "NEUTRAL");
            cov = BigDecimal.valueOf(n).subtract(intervalBad).divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
        }
        ReviewAdvice review = n == 0
                ? new ReviewAdvice(false, "NONE", "no valid sample (LOW_TRAVEL)")
                : reviewAdvice(cov, mape, sampleFlag, thresholds);
        return new BacktestReport(mae, mape, direction, cov, dataCoverage,
                zeroActual, n, sampleFlag, review);
    }

    /** 阈值判定：MAPE>阈值 或 覆盖率<阈值 ⇒ 复审任务；LOW_SAMPLE 不告警（§3.4）。 */
    public static ReviewAdvice reviewAdvice(BigDecimal intervalCoverage,
                                                            BigDecimal mape,
                                                            String sampleFlag,
                                                            Thresholds t) {
        if ("LOW_SAMPLE".equals(sampleFlag)) {
            return new ReviewAdvice(false, "NONE", "LOW_SAMPLE no-alert");
        }
        if (intervalCoverage != null && mape != null
                && (mape.compareTo(t.mapePct()) > 0
                        || intervalCoverage.multiply(BigDecimal.valueOf(100)).compareTo(t.coveragePct()) < 0)) {
            // 角色路由：假设偏差→"finance+ops_owner"；画像偏差→"knowledge_admin"（§3.4）。
            // 本批次生产侧由 caller 依据"因子来自画像 vs 假设"决定，本函数返回"需要角色复审"这一通用标记。
            return new ReviewAdvice(true, "PENDING_ROUTING",
                    "mape=" + mape + " > " + t.mapePct() + " 或 coverage=" + intervalCoverage + " < " + t.coveragePct());
        }
        return new ReviewAdvice(false, "NONE", "within thresholds");
    }

    private FcBacktestCalc() {}
}
