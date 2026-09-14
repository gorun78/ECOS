package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.engine.cognitive2.ForecastService;
import com.chinacreator.gzcm.engine.cognitive2.model.ForecastPoint;
import com.chinacreator.gzcm.engine.cognitive2.model.ForecastRequest;
import com.chinacreator.gzcm.engine.cognitive2.model.ForecastResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 时序预测服务实现 — 统计基线模型（PMO-51 T2）。
 *
 * <p>算法（决策 3：不引第三方 ML 框架）：
 * <ol>
 *   <li><b>水线</b>：移动均值（最近 3 点，点数不足取全部）</li>
 *   <li><b>趋势</b>：最小二乘线性回归斜率（点数 ≥3 时）</li>
 *   <li><b>因子修正</b>：context/KG 提供 causalFactor(-1~1) 时按 50% 权重叠加</li>
 *   <li><b>置信区间</b>：历史残差标准差 × 1.96（95%）</li>
 * </ol>
 * 纯内存计算，无 DB 依赖，推理结果不落盘（铁律 #2）。</p>
 */
@Service
public class ForecastServiceImpl implements ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastServiceImpl.class);
    private static final int MIN_POINTS = 2;
    private static final int MAX_HORIZON = 60;

    @Override
    public ForecastResult forecast(ForecastRequest request) {
        validate(request);
        List<ForecastRequest.SeriesPoint> series = request.getSeries();
        int horizon = Math.min(Math.max(request.getHorizon(), 1), MAX_HORIZON);

        double[] values = new double[series.size()];
        for (int i = 0; i < series.size(); i++) {
            values[i] = series.get(i).getValue();
        }
        long lastT = series.get(series.size() - 1).getT();
        long stepT = series.size() > 1 ? (lastT - series.get(0).getT()) / (series.size() - 1) : 1;
        if (stepT <= 0) {
            stepT = 1;
        }

        // 水线 + 趋势
        double level = movingAverage(values);
        double slope = leastSquaresSlope(values);
        double residualStd = residualStd(values, level, slope);
        double n = values.length;

        // KG / context 因子修正
        double factor = extractCausalFactor(request);
        boolean kgAdjusted = factor != 0.0;
        double factorDelta = factor * 0.5 * level;

        ForecastResult result = new ForecastResult();
        result.setMetric(request.getMetric());
        result.setDomain(request.getDomain() == null ? "default" : request.getDomain());
        result.setModelId(request.getModelId() == null ? "baseline-linear" : request.getModelId());
        result.setKgAdjusted(kgAdjusted);

        for (int h = 1; h <= horizon; h++) {
            // 线性外推：以最后一帧为原点，slope * h
            double base = level + slope * h + (kgAdjusted ? factorDelta * h : 0.0);
            // 置信区间随步长加宽（sqrt(h) 扩散）
            double halfWidth = (residualStd + 0.5 * Math.abs(slope) * h) * 1.96 * Math.sqrt(h + 0.5);
            ForecastPoint point = new ForecastPoint(h, lastT + stepT * h, base, base - halfWidth, base + halfWidth);
            result.getPoints().add(point);
        }

        // 置信度：历史越平稳、点越多、因子未修正 → 置信越高
        double cv = level != 0 ? residualStd / Math.abs(level) : (residualStd > 0 ? 1.0 : 0.0);
        double confidence = clamp(0.95 - cv * 0.5 - (n < 5 ? 0.1 : 0.0) - (kgAdjusted ? 0.05 : 0.0), 0.2, 0.95);
        result.setConfidence(confidence);

        result.setSummary(String.format(
            "baseline-linear: 水平=%.4f 斜率=%.4f 残差std=%.4f 步长=%d horizon=%d%s",
            level, slope, residualStd, stepT, horizon, kgAdjusted ? " (含因果因子修正 factor=" + factor + ")" : ""));
        result.getJustifications().add(String.format("移动均值(水平)=%.4f", level));
        result.getJustifications().add(String.format("最小二乘趋势(斜率)=%.4f/步", slope));
        if (kgAdjusted) {
            result.getJustifications().add(String.format("KG/context 因果因子修正 factor=%.3f（叠加 50%% 权重）", factor));
        } else {
            result.getJustifications().add("未提供因果因子，纯统计基线外推");
        }
        result.getJustifications().add(String.format("置信区间=残差std(%.4f)×1.96×sqrt(h+0.5)", residualStd));

        log.info("预测完成 metric={} horizon={} confidence={} kgAdjusted={}",
                request.getMetric(), horizon, confidence, kgAdjusted);
        return result;
    }

    // ── 统计工具 ──

    private double movingAverage(double[] values) {
        int start = Math.max(0, values.length - 3);
        double sum = 0;
        int count = 0;
        for (int i = start; i < values.length; i++) {
            sum += values[i];
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }

    /** 最小二乘斜率：x = 0..n-1 */
    private double leastSquaresSlope(double[] y) {
        int n = y.length;
        if (n < 3) {
            return 0.0;
        }
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            sx += i;
            sy += y[i];
            sxx += (double) i * i;
            sxy += (double) i * y[i];
        }
        double denom = n * sxx - sx * sx;
        if (denom == 0) {
            return 0.0;
        }
        return (n * sxy - sx * sy) / denom;
    }

    private double residualStd(double[] y, double level, double slope) {
        int n = y.length;
        double sumSq = 0;
        for (int i = 0; i < n; i++) {
            double pred = level + slope * i;
            double r = y[i] - pred;
            sumSq += r * r;
        }
        return Math.sqrt(sumSq / Math.max(1, n - 2));
    }

    /** 从 context 提取因果因子（-1~1），kgCausalFactor 优先。 */
    private double extractCausalFactor(ForecastRequest request) {
        if (request.getContext() == null) {
            return 0.0;
        }
        Object f = request.getContext().get("kgCausalFactor");
        if (f instanceof Number) {
            return clamp(((Number) f).doubleValue(), -1.0, 1.0);
        }
        return 0.0;
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void validate(ForecastRequest request) {
        if (request == null) {
            throw new BusinessException(400, "FCST-400: 预测请求不可为空");
        }
        if (request.getMetric() == null || request.getMetric().isBlank()) {
            throw new BusinessException(400, "FCST-400: 指标名 metric 必填");
        }
        List<ForecastRequest.SeriesPoint> series = request.getSeries();
        if (series == null || series.size() < MIN_POINTS) {
            throw new BusinessException(400, "FCST-400: 历史序列 series 至少 " + MIN_POINTS + " 点");
        }
        for (ForecastRequest.SeriesPoint p : series) {
            if (p == null || Double.isNaN(p.getValue()) || Double.isInfinite(p.getValue())) {
                throw new BusinessException(400, "FCST-400: 序列存在非法数值");
            }
        }
    }
}
