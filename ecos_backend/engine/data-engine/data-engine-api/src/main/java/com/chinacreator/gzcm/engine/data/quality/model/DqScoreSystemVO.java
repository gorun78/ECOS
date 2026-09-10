package com.chinacreator.gzcm.engine.data.quality.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 系统健康度 VO — {@code /api/v1/dq/scores/system} 端点出参（PMO-48-B T8）。
 *
 * <p>字段说明：</p>
 * <ul>
 *   <li>{@code overallScore} 全部未删除资产的 overall_score 均值</li>
 *   <li>{@code count} 参与均值计算的资产数（is_deleted=FALSE）</li>
 *   <li>{@code perDimension} 近 1 天 6 维度平均分（key=DqDimension name, value=avg 0.0-1.0）</li>
 *   <li>{@code grade} 系统级 A/B/C/D/F 等级，由 overallScore 决定</li>
 * </ul>
 *
 * <p>SQL：</p>
 * <pre>
 *   SELECT AVG(overall_score) FROM ecos_dq.dq_score_asset WHERE is_deleted = FALSE
 *   SELECT dimension, AVG(score_value) FROM ecos_dq.dq_score_snapshot
 *     WHERE evaluated_at &gt;= NOW() - interval '1 day'
 *     GROUP BY dimension
 * </pre>
 *
 * @author PMO-48-B T8
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqScoreSystemVO {

    /** 全资产 overall_score 均值 0.0-1.0 */
    private double overallScore;

    /** 资产数（参与均值的） */
    private int count;

    /** 系统级等级 A/B/C/D/F */
    private String grade;

    /** 近 1 天各维度平均分（DqDimension name → 0.0-1.0） */
    private Map<String, Double> perDimension = new LinkedHashMap<>();

    /** 系统级等级由 DqDimension#gradeOf 计算，缺默认 F。 */
    public boolean isEvaluated() {
        return count > 0;
    }

    /** 容器 getter（Jackson 友好，无需额外）。 */
    public Map<String, Double> getPerDimension() {
        return perDimension != null ? perDimension : new LinkedHashMap<>();
    }

    /** 系统等级阈值复用 DqDimension#gradeOf 的语义（任取一个维度即可 — 6 维同一阈值表）。 */
    public static String gradeOf(double score) {
        return DqDimension.FRESHNESS.gradeOf(score);
    }
}
