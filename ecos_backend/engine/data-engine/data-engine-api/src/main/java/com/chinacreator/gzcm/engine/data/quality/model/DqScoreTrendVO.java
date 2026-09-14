package com.chinacreator.gzcm.engine.data.quality.model;

import java.time.LocalDate;

import com.chinacreator.gzcm.engine.data.quality.scoring.DqDimension;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 评分趋势 VO — 单天数某一维度的快照（PMO-48-B T8）。
 *
 * <p>对应 SQL：
 * <pre>
 *   SELECT to_date(evaluated_at, 'YYYY-MM-DD') AS day,
 *          dimension,
 *          AVG(score_value) AS score_value,
 *          COUNT(*) AS hits
 *   FROM ecos_dq.dq_score_snapshot
 *   WHERE scope_type = ? AND scope_id = ?
 *     AND evaluated_at &gt;= NOW() - (? || ' days')::interval
 *   GROUP BY 1, 2
 *   ORDER BY 1 DESC, 2
 * </pre>
 * </p>
 *
 * <p>前端 RadarChart/LineChart 渲染用。</p>
 *
 * @author PMO-48-B T8
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqScoreTrendVO {

    /** 日期（按天分组） */
    private LocalDate date;

    /** 维度编码（DqDimension name 大写） */
    private String dimension;

    /** 当日该维度平均分 0.0-1.0 */
    private double scoreValue;

    /** 当日该维度的评估次数（诊断位） */
    private int hits;

    /** 构造器 — 便于 Service 拼装 List&lt;DqScoreTrendVO&gt;。 */
    public DqScoreTrendVO() {
    }

    public DqScoreTrendVO(LocalDate date, String dimension, double scoreValue, int hits) {
        this.date = date;
        this.dimension = dimension;
        this.scoreValue = scoreValue;
        this.hits = hits;
    }

    /** 转 DqDimension（null 不抛错）。 */
    public DqDimension toDimension() {
        if (dimension == null || dimension.isBlank()) {
            return null;
        }
        try {
            return DqDimension.valueOf(dimension.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
