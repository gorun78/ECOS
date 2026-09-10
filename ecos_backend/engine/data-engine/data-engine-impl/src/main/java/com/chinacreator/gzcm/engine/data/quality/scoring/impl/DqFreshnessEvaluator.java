package com.chinacreator.gzcm.engine.data.quality.scoring.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionScore;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;

/**
 * 及时性（FRESHNESS）评估器 — executed_at 距 now 的分钟数对照阈值（rule_types: FRESHNESS / TIMELINESS）。
 *
 * <p>默认阈值：5 分钟 → 1.0；60 分钟 → 0.0；中间线性。</p>
 * <p>
 * 可定制：参数 {@code freshness_minutes}（int）替换 5/60 阈值（如 10/120 表示 10 分钟满分，120 分钟 0 分）。
 * </p>
 *
 * <p>边界（铁律 1.3 / 0.2）：</p>
 * <ul>
 *   <li>executedAt = null → 1.0（无时序信号 → 默认满分；Service 端落库时 detail 注释）</li>
 *   <li>diff &lt;= 5 min → 1.0；diff &gt;= 60 min → 0.0；线性插值</li>
 *   <li>执行时间在未来（时钟漂移）→ 视 0 diff 返回 1.0</li>
 * </ul>
 *
 * @author PMO-48-B T8
 */
@Component
public class DqFreshnessEvaluator implements DimensionEvaluator {

    /** 默认阈值（5 min 满分，60 min 0 分） */
    static final int DEFAULT_FRESH_MIN = 5;
    static final int DEFAULT_STALE_MIN = 60;

    @Override
    public DqDimension dimension() {
        return DqDimension.FRESHNESS;
    }

    @Override
    public DimensionScore evaluate(ScoringContext ctx) {
        DimensionScore s = new DimensionScore();
        s.setDimension(DqDimension.FRESHNESS);
        s.setRuleCount(1);
        s.setSampleSize((int) Math.min(ctx.getTotalRows(), 1000));
        Map<String, Object> det = new LinkedHashMap<>();

        Map<String, Object> params = ctx.getParameters();
        // 自定义阈值（非数字时回退默认 5/60）
        int freshMin = parseIntSafe(prm(params, "freshness_minutes"), DEFAULT_FRESH_MIN);
        int staleMin = parseIntSafe(prm(params, "stale_minutes"), DEFAULT_STALE_MIN);

        LocalDateTime executedAt = ctx.getExecutedAt();
        if (executedAt == null) {
            s.setScoreValue(1.0D);
            det.put("status", "NO_EXECUTED_AT");
            det.put("note", "无 executed_at — 默认满分 (1.0)");
            det.put("freshThresholdMin", freshMin);
            det.put("staleThresholdMin", staleMin);
            s.setDetails(det);
            return s;
        }

        long diffMin = Duration.between(executedAt, LocalDateTime.now()).toMinutes();
        if (diffMin < 0L) {
            diffMin = 0L;
        }
        double score;
        if (diffMin <= freshMin) {
            score = 1.0D;
        } else if (diffMin >= staleMin) {
            score = 0.0D;
        } else {
            double span = staleMin - freshMin;
            double ratio = (staleMin - diffMin) / span;
            score = clamp(ratio);
        }
        s.setScoreValue(score);
        det.put("executedAtEpochMs", executedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        det.put("ageMinutes", diffMin);
        det.put("freshThresholdMin", freshMin);
        det.put("staleThresholdMin", staleMin);
        det.put("freshnessScore", String.format("%.2f", score));
        s.setDetails(det);
        return s;
    }

    private String prm(Map<String, Object> params, String key) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        Object v = params.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static int parseIntSafe(String s, int def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            int n = Integer.parseInt(s.trim());
            return n > 0 ? n : def;
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    private static double clamp(double v) {
        if (Double.isNaN(v) || v < 0.0D) {
            return 0.0D;
        } else if (v > 1.0D) {
            return 1.0D;
        }
        return v;
    }
}
