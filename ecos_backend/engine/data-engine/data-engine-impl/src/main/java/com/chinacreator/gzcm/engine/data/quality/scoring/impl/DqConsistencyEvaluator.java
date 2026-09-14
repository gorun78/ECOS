package com.chinacreator.gzcm.engine.data.quality.scoring.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionScore;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;

/**
 * 一致性（CONSISTENCY）评估器 — 跨表冲突检查（rule_types: CONSISTENCY / STATISTICAL）。
 *
 * <p>Phase 2 简化：直接读 dq_rule_check.pass_rate（最近一次规则检查通过率），作为
 * 该次跨表一致性快照。</p>
 *
 * <p>判定规则：</p>
 * <ul>
 *   <li>无 lastCheck.passRate（null 或 totalRows=0）→ 1.0（空表/无样本）</li>
 *   <li>有 lastCheck.passRate → 视作 (pass / total)，回写 0.0-1.0</li>
 * </ul>
 *
 * @author PMO-48-B T8
 */
@Component
public class DqConsistencyEvaluator implements DimensionEvaluator {

    @Override
    public DqDimension dimension() {
        return DqDimension.CONSISTENCY;
    }

    @Override
    public DimensionScore evaluate(ScoringContext ctx) {
        DimensionScore s = new DimensionScore();
        s.setDimension(DqDimension.CONSISTENCY);
        s.setRuleCount(1);
        Map<String, Object> det = new LinkedHashMap<>();

        // 优先取 lastCheck.passRate（来自 dq_rule_check.pass_rate 字段，0.0-1.0）
        Double passRate = readPassRate(ctx);
        long total = ctx.getTotalRows();

        if (passRate == null && total <= 0) {
            s.setSampleSize(0);
            det.put("status", "EMPTY_SAMPLE");
            det.put("note", "样本 0 行或 passRate 缺失 — 默认一致性满分 (1.0)");
            s.setScoreValue(1.0D);
        } else if (passRate != null) {
            s.setSampleSize((int) Math.min(total, 1000));
            s.setScoreValue(clamp(passRate));
            det.put("source", "dq_rule_check.pass_rate");
            det.put("passRate", passRate);
            det.put("consistencyRate", String.format("%.2f%%", s.getScoreValue() * 100.0D));
        } else {
            // 兜底：用 total / failed 估算 pass_rate
            double score = 1.0D - (double) ctx.getFailedRows() / (double) total;
            s.setSampleSize((int) Math.min(total, 1000));
            s.setScoreValue(clamp(score));
            det.put("source", "estimated (1 - failed/total)");
            det.put("consistencyRate", String.format("%.2f%%", s.getScoreValue() * 100.0D));
        }
        s.setDetails(det);
        return s;
    }

    private Double readPassRate(ScoringContext ctx) {
        Map<String, Object> last = ctx.getLastCheck();
        if (last == null || last.isEmpty()) {
            return null;
        }
        Object pr = last.get("passRate");
        if (pr == null) {
            pr = last.get("pass_rate");
        }
        if (pr instanceof Number) {
            return ((Number) pr).doubleValue();
        }
        if (pr instanceof String) {
            try {
                return Double.parseDouble((String) pr);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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
