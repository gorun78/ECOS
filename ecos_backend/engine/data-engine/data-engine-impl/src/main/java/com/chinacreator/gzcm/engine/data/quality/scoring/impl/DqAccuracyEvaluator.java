package com.chinacreator.gzcm.engine.data.quality.scoring.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionScore;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;

/**
 * 准确性（ACCURACY）评估器 — FORMAT / RANGE / REGEX 校验通过率。
 *
 * <p>判定规则（与 dq_rule_check.passed / total 同语义）：</p>
 * <ul>
 *   <li>totalRows = 0 → 1.0（无样本无法判定 → 默认 acc 满分）</li>
 *   <li>totalRows > 0 → 1 - failed / total（参考 dq_rule_check.passed = (total-failed)/total）</li>
 * </ul>
 *
 * <p>Phase 2 简化：评估器不主动拉样本来验正则；用 dq_rule_check 的 failed_rows 表示
 * "格式/范围/正则命中失败项数"。</p>
 *
 * @author PMO-48-B T8
 */
@Component
public class DqAccuracyEvaluator implements DimensionEvaluator {

    @Override
    public DqDimension dimension() {
        return DqDimension.ACCURACY;
    }

    @Override
    public DimensionScore evaluate(ScoringContext ctx) {
        long total = ctx.getTotalRows();
        long failed = ctx.getFailedRows();
        DimensionScore s = new DimensionScore();
        s.setDimension(DqDimension.ACCURACY);
        s.setRuleCount(1);
        Map<String, Object> det = new LinkedHashMap<>();

        if (total <= 0) {
            s.setSampleSize(0);
            det.put("status", "EMPTY_SAMPLE");
            det.put("note", "样本 0 行 — 默认准确性满分 (1.0)");
            s.setScoreValue(1.0D);
        } else {
            double score = 1.0D - (double) failed / (double) total;
            s.setSampleSize((int) Math.min(total, 1000));
            s.setScoreValue(score);
            det.put("totalRows", total);
            det.put("failedRows", failed);
            det.put("accuracyRate", String.format("%.2f%%", score * 100.0D));
            det.put("acceptedRuleTypes", List.of("FORMAT", "RANGE", "REGEX"));
        }
        s.setDetails(det);
        return s;
    }
}
