package com.chinacreator.gzcm.engine.data.quality.scoring.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionScore;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;

/**
 * 唯一性（UNIQUENESS）评估器 — 主键/业务键无重复（rule_types: UNIQUE）。
 *
 * <p>判定规则：</p>
 * <ul>
 *   <li>totalRows = 0 或 NULL → 1.0（空表无重复）</li>
 *   <li>totalRows > 0 → 1 - failedRows / totalRows（failed 视为重复项数）</li>
 * </ul>
 *
 * <p>Phase 2 简化：把 dq_rule_check 的 failed_rows / total_rows 解释为
 * "重复行数 / 总行数"，不主动拉样本（拉样本属 Phase 3 监控调度器）。</p>
 *
 * @author PMO-48-B T8
 */
@Component
public class DqUniquenessEvaluator implements DimensionEvaluator {

    @Override
    public DqDimension dimension() {
        return DqDimension.UNIQUENESS;
    }

    @Override
    public DimensionScore evaluate(ScoringContext ctx) {
        long total = ctx.getTotalRows();
        long failed = ctx.getFailedRows();
        DimensionScore s = new DimensionScore();
        s.setDimension(DqDimension.UNIQUENESS);
        s.setRuleCount(1);
        Map<String, Object> det = new LinkedHashMap<>();

        if (total <= 0) {
            s.setSampleSize(0);
            det.put("status", "EMPTY_SAMPLE");
            det.put("note", "样本 0 行 — 空表无重复 (1.0)");
            s.setScoreValue(1.0D);
        } else {
            double score = 1.0D - (double) failed / (double) total;
            s.setSampleSize((int) Math.min(total, 1000));
            s.setScoreValue(score);
            det.put("totalRows", total);
            det.put("duplicateRows", failed);
            det.put("uniqueRate", String.format("%.2f%%", score * 100.0D));
        }
        s.setDetails(det);
        return s;
    }
}
