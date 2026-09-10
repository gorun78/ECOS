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
 * 完整性（COMPLETENESS）评估器 — 失空率 (rule_types: NOT_NULL / PRESENCE / OWN)。
 * <p>
 * 计算：
 * <ul>
 *   <li>totalRows = 0 → 1.0（空表视完整）</li>
 *   <li>totalRows > 0 → 1 - failedRows / totalRows</li>
 * </ul>
 * <p>
 * 样本 0 行但有 OWN 类型（自有数据）→ 同样视为 1.0。
 * 命中敏感字段（target_field ∈ phone/mobile/金额/身份证/银行卡/手机/amount）时，
 * details.values 字段名仍在，值用 *** 占位（铁律 2.4 #3）。
 * </p>
 *
 * @author PMO-48-B T8
 */
@Component
public class DqCompletenessEvaluator implements DimensionEvaluator {

    private static final String SENSITIVE_HINT = "(phone|mobile|amount|id_card|idcard|bankcard|bank_card|身份证|银行|金额|手机)";

    @Override
    public DqDimension dimension() {
        return DqDimension.COMPLETENESS;
    }

    @Override
    public DimensionScore evaluate(ScoringContext ctx) {
        long total = ctx.getTotalRows();
        long failed = ctx.getFailedRows();
        DimensionScore s = new DimensionScore();
        s.setDimension(DqDimension.COMPLETENESS);
        s.setRuleCount(1);
        Map<String, Object> det = new LinkedHashMap<>();

        if (total <= 0) {
            s.setSampleSize(0);
            det.put("status", "EMPTY_SAMPLE");
            det.put("note", "样本 0 行 — 空表视完整 (1.0)");
            s.setScoreValue(1.0D);
        } else {
            double score = 1.0D - (double) failed / (double) total;
            s.setSampleSize((int) Math.min(total, 1000));
            s.setScoreValue(score);
            det.put("totalRows", total);
            det.put("failedRows", failed);
            det.put("completenessRate", ratioPct(score));
        }
        maskSensitiveField(ctx, det);
        s.setDetails(det);
        return s;
    }

    /** 命中敏感字段 → detail 留占位符，score 仍可算。 */
    private void maskSensitiveField(ScoringContext ctx, Map<String, Object> det) {
        String field = ctx.getTargetField();
        if (field != null && field.matches("(?i).*(" + SENSITIVE_HINT + ").*")) {
            det.put("targetField", "***");
            det.put("sensitiveMasked", true);
        }
    }

    private String ratioPct(double score) {
        return String.format("%.2f%%", score * 100.0D);
    }
}
