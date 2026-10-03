package com.chinacreator.gzcm.engine.kb.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * CURATED 事实取数默认实现（F04-06 / PRD-04 §2.3-2）。
 * <p>
 * 只读 {@code ecos_dw} CURATED 层（{@code dq_status=PUBLISHED}，经 data-engine 落库的事实表）；
 * <b>禁读 RAW</b>（B-1）。JdbcTemplate 走 runtime-access 注入的只读 DataSource。
 * <p>
 * 查询标识 {@code sourceQueryRef} 由 metric + 窗口 + 过滤构成，落画像表可复现（S-1）。
 * 表名/列名为可复现占位（实际 CURATED 事实表由分册 02 R-1 口径定名，取数实现按"可复现"留缝，
 * 真实列映射按 datanet 供数契约对齐后可调；此处默认以 {@code ecos_dw.ecos_biz_stage_fact} 为参照）。
 *
 * @author ECOS KB Team
 */
@Component
public class JdbcCuratedFactSource implements CuratedFactSource {

    private static final Logger log = LoggerFactory.getLogger(JdbcCuratedFactSource.class);

    private final JdbcTemplate jdbc;

    public JdbcCuratedFactSource(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CuratedFactRow> fetch(String metricCode, String windowFrom, String windowTo, String sourceQueryRef) {
        // 只读 CURATED（dq_status='PUBLISHED'）；禁读 RAW。
        // 过滤语义：指标列名按 metric_code 落位（约定 fact 行有 metric_value + 四轴维度列）。
        String sql = "SELECT metric_value AS value, project_id AS project, project_type AS projectType, "
                + "department_id AS department, stage AS stage "
                + "FROM ecos_dw.ecos_biz_stage_fact "
                + "WHERE dq_status = 'PUBLISHED' "
                + "AND metric_code = ? "
                + "AND period >= ? AND period <= ? "
                + "AND ftype = 'ACTUAL'";
        try {
            final String m = metricCode;
            final String wf = windowFrom;
            final String wt = windowTo;
            List<CuratedFactRow> rows = jdbc.query(sql, (rs, i) -> {
                BigDecimal v = rs.getBigDecimal(1);
                String project = rs.getString(2);
                String ptype = rs.getString(3);
                String dept = rs.getString(4);
                String stage = rs.getString(5);
                return new CuratedFactRow(v, project, ptype, dept, stage);
            }, m, wf, wt);
            log.debug("JdbcCuratedFactSource.fetch: ref={} rows={}", sourceQueryRef, rows.size());
            return rows;
        } catch (Exception e) {
            log.error("JdbcCuratedFactSource.fetch 失败: ref={} err={}", sourceQueryRef, e.getMessage(), e);
            throw e;
        }
    }
}
