package com.chinacreator.gzcm.engine.cognitive2.dao;

import com.chinacreator.gzcm.engine.cognitive2.model.OutcomeEvaluationEntity;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * W→D 结果评估数据访问（《认知域与智能域补充详细设计 v1.0》§9；V267 建表 + V274 补列）。
 *
 * <p>纪律：schema 限定 {@code ecos_cognitive.}（ST07 / R-13 ①）；读列面显式清单，0 {@code SELECT *}
 * （IR04）；JSON 列一律 TEXT，无 {@code ?::jsonb} 强转（MC02/MC03）；窗口列 {@code DATE} 无时区；
 * 只加不改（IR03）—— 本 DAO 无任何 UPDATE 生产资产的路径，评估记录落库后仅允许状态自
 * {@code PENDING} 单向推进（{@link #transitionStatus}，CAS 语义，0 行受影响由 Service 判 409）。</p>
 */
@Repository
public class OutcomeEvaluationDao extends JdbcTemplateDaoSupport {

    /** 控制域认知引擎 schema 权威载体（ST07）。 */
    static final String TABLE = "ecos_cognitive.ecos_cognitive_outcome_evaluation";

    /** 读列面（显式清单，IR04；下划线列名 → camelCase 别名，省一层 RowMapper 映射表）。 */
    private static final String READ_COLUMNS =
            "id, tenant_id AS tenantId, org_id AS orgId, scenario_id AS scenarioId, "
            + "blueprint_id AS blueprintId, blueprint_version AS blueprintVersion, "
            + "run_id AS runId, forecast_run_id AS forecastRunId, "
            + "actual_asset_id AS actualAssetId, actual_version AS actualVersion, "
            + "caliber_id AS caliberId, caliber_alignment AS caliberAlignment, "
            + "window_from_date AS windowFromDate, window_to_date AS windowToDate, "
            + "status, metric_results_text AS metricResultsJson, diagnoses_text AS diagnosesJson, "
            + "update_candidates_text AS updateCandidatesJson, warnings_text AS warningsJson, "
            + "blockers_text AS blockersJson, calculation_refs_text AS calculationRefsJson, "
            + "trace_id AS traceId, idempotency_key AS idempotencyKey, version_no AS versionNo, "
            + "create_by AS createBy, update_by AS updateBy, create_time AS createTime, update_time AS updateTime";

    private static final RowMapper<OutcomeEvaluationEntity> MAPPER = new OutcomeEvaluationRowMapper();

    /**
     * 落评估记录（§9.2 步骤 10 闭环记录）。
     *
     * @return 受影响行数（幂等唯一索引冲突由 Service 捕获 {@code DuplicateKeyException} 后回读既有记录）
     */
    public int insert(OutcomeEvaluationEntity e) {
        return jdbcTemplate.update(
            "INSERT INTO " + TABLE + " ("
            + "id, tenant_id, org_id, scenario_id, blueprint_id, blueprint_version, "
            + "run_id, forecast_run_id, actual_asset_id, actual_version, caliber_id, caliber_alignment, "
            + "window_from_date, window_to_date, status, metric_results_text, diagnoses_text, "
            + "update_candidates_text, warnings_text, blockers_text, calculation_refs_text, "
            + "trace_id, idempotency_key, domain, version_no, create_time, update_time, "
            + "create_by, update_by, is_deleted) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
            + "'default', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, 0)",
            e.getId(), e.getTenantId(), e.getOrgId(), e.getScenarioId(), e.getBlueprintId(),
            e.getBlueprintVersion(), e.getRunId(), e.getForecastRunId(), e.getActualAssetId(),
            e.getActualVersion(), e.getCaliberId(), e.getCaliberAlignment(),
            toDate(e.getWindowFromDate()), toDate(e.getWindowToDate()),
            e.getStatus(), e.getMetricResultsJson(), e.getDiagnosesJson(),
            e.getUpdateCandidatesJson(), e.getWarningsJson(), e.getBlockersJson(),
            e.getCalculationRefsJson(), e.getTraceId(), e.getIdempotencyKey(),
            e.getVersionNo(), e.getCreateBy(), e.getUpdateBy());
    }

    /** 单行读取（未删除）。无记录返回 {@code null}（禁返回空对象伪造成功）。 */
    public OutcomeEvaluationEntity findById(String id) {
        List<OutcomeEvaluationEntity> rows = jdbcTemplate.query(
            "SELECT " + READ_COLUMNS + " FROM " + TABLE + " WHERE id = ? AND is_deleted = 0",
            MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 幂等回读（V274 唯一索引 {@code uk_cv274_outcome_idem}）。
     * {@code idempotencyKey} 空白时直接返回 {@code null} —— 禁用空键命中任意历史记录。
     */
    public OutcomeEvaluationEntity findByIdempotencyKey(String tenantId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        List<OutcomeEvaluationEntity> rows = jdbcTemplate.query(
            "SELECT " + READ_COLUMNS + " FROM " + TABLE
            + " WHERE tenant_id = ? AND idempotency_key = ? AND is_deleted = 0 "
            + "ORDER BY create_time DESC, id LIMIT 1",
            MAPPER, tenantId, idempotencyKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 场景维列表（走 {@code idx_cv267_outcome_scenario}；status 可选过滤）。 */
    public List<OutcomeEvaluationEntity> listByScenario(String tenantId, String scenarioId, String status) {
        StringBuilder sql = new StringBuilder("SELECT " + READ_COLUMNS + " FROM " + TABLE
                + " WHERE is_deleted = 0 AND tenant_id = ? AND scenario_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(scenarioId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status.toUpperCase());
        }
        sql.append(" ORDER BY create_time DESC, id LIMIT 200");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    /** 运行维列表（走 {@code idx_cv267_outcome_run}）。 */
    public List<OutcomeEvaluationEntity> listByRun(String runId) {
        return jdbcTemplate.query(
            "SELECT " + READ_COLUMNS + " FROM " + TABLE
            + " WHERE is_deleted = 0 AND run_id = ? ORDER BY create_time DESC, id LIMIT 200",
            MAPPER, runId);
    }

    /**
     * 状态单向推进（CAS：{@code WHERE status = ?} 带原状态，0 行受影响 ⇒ Service 判 409，禁静默成功）。
     * 只允许推进评估记录自身状态；本方法<b>不</b>触碰任何生产资产表（SUP-OUT-01）。
     */
    public int transitionStatus(String id, String fromStatus, String toStatus, String operator) {
        return jdbcTemplate.update(
            "UPDATE " + TABLE + " SET status = ?, update_by = ?, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = ? AND is_deleted = 0 AND status = ?",
            toStatus, operator, id, fromStatus);
    }

    private static Date toDate(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }

    /** 显式 RowMapper（列名以 SQL 别名给到 camelCase；DATE → LocalDate 由 JDBC 4.2 承担）。 */
    private static final class OutcomeEvaluationRowMapper implements RowMapper<OutcomeEvaluationEntity> {
        @Override
        public OutcomeEvaluationEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            OutcomeEvaluationEntity e = new OutcomeEvaluationEntity();
            e.setId(rs.getString("id"));
            e.setTenantId(rs.getString("tenantId"));
            e.setOrgId(rs.getString("orgId"));
            e.setScenarioId(rs.getString("scenarioId"));
            e.setBlueprintId(rs.getString("blueprintId"));
            e.setBlueprintVersion(rs.getString("blueprintVersion"));
            e.setRunId(rs.getString("runId"));
            e.setForecastRunId(rs.getString("forecastRunId"));
            e.setActualAssetId(rs.getString("actualAssetId"));
            e.setActualVersion(rs.getString("actualVersion"));
            e.setCaliberId(rs.getString("caliberId"));
            e.setCaliberAlignment(rs.getString("caliberAlignment"));
            Date from = rs.getDate("windowFromDate");
            e.setWindowFromDate(from == null ? null : from.toLocalDate());
            Date to = rs.getDate("windowToDate");
            e.setWindowToDate(to == null ? null : to.toLocalDate());
            e.setStatus(rs.getString("status"));
            e.setMetricResultsJson(rs.getString("metricResultsJson"));
            e.setDiagnosesJson(rs.getString("diagnosesJson"));
            e.setUpdateCandidatesJson(rs.getString("updateCandidatesJson"));
            e.setWarningsJson(rs.getString("warningsJson"));
            e.setBlockersJson(rs.getString("blockersJson"));
            e.setCalculationRefsJson(rs.getString("calculationRefsJson"));
            e.setTraceId(rs.getString("traceId"));
            e.setIdempotencyKey(rs.getString("idempotencyKey"));
            e.setVersionNo(rs.getString("versionNo"));
            e.setCreateBy(rs.getString("createBy"));
            e.setUpdateBy(rs.getString("updateBy"));
            return e;
        }
    }
}
