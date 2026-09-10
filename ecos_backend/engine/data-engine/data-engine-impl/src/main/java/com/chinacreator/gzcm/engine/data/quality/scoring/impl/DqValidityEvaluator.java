package com.chinacreator.gzcm.engine.data.quality.scoring.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionEvaluator;
import com.chinacreator.gzcm.engine.data.quality.scoring.DimensionScore;
import com.chinacreator.gzcm.engine.data.quality.scoring.ScoringContext;

/**
 * 合规有效性（VALIDITY）评估器 — 枚举/合规命中通过率（rule_types: VALIDITY / ENUM）。
 *
 * <p>判定规则：</p>
 * <ul>
 *   <li>totalRows = 0 → 1.0（空表无越界值）</li>
 *   <li>totalRows > 0 → 1 - failed / total（failed = 不命中枚举/合规的项数）</li>
 * </ul>
 *
 * <p>命中敏感字段 → details.values 加 *** 占位（铁律 2.4 #3）。</p>
 *
 * @author PMO-48-B T8
 */
@Component
public class DqValidityEvaluator implements DimensionEvaluator {

    private static final String SENSITIVE_HINT = "(phone|mobile|amount|id_card|idcard|bankcard|bank_card|身份证|银行|金额|手机)";

    /** 命中枚举上限（detail.values 最多保留 5 个，避免 metadata JSONB 过大） */
    private static final int MAX_VALUES_SAMPLE = 5;

    @Override
    public DqDimension dimension() {
        return DqDimension.VALIDITY;
    }

    @Override
    public DimensionScore evaluate(ScoringContext ctx) {
        long total = ctx.getTotalRows();
        long failed = ctx.getFailedRows();
        DimensionScore s = new DimensionScore();
        s.setDimension(DqDimension.VALIDITY);
        s.setRuleCount(1);
        Map<String, Object> det = new LinkedHashMap<>();

        if (total <= 0) {
            s.setSampleSize(0);
            det.put("status", "EMPTY_SAMPLE");
            det.put("note", "样本 0 行 — 默认有效性满分 (1.0)");
            s.setScoreValue(1.0D);
        } else {
            double score = 1.0D - (double) failed / (double) total;
            s.setSampleSize((int) Math.min(total, 1000));
            s.setScoreValue(score);
            det.put("totalRows", total);
            det.put("failedRows", failed);
            det.put("validityRate", String.format("%.2f%%", score * 100.0D));
            det.put("acceptedRuleTypes", java.util.List.of("VALIDITY", "ENUM"));
        }
        maskSensitiveField(ctx, det);
        s.setDetails(det);
        return s;
    }

    private void maskSensitiveField(ScoringContext ctx, Map<String, Object> det) {
        String field = ctx.getTargetField();
        if (field == null || field.isBlank()) {
            return;
        }
        boolean sensitive = field.matches("(?i).*(" + SENSITIVE_HINT + ").*");
        if (sensitive) {
            det.put("targetField", "***");
            det.put("sensitiveMasked", true);
        }
    }
}
