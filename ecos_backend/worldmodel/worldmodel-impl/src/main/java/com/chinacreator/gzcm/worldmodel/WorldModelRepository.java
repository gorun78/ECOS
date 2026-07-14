package com.chinacreator.gzcm.worldmodel;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * JdbcTemplate 仓库 — World Model 持久化 CRUD
 * <p>
 * 遵循 AgentRepository / WorkflowRepository 模式。
 * 管理目标(ecos_wm_goal)、因果链(ecos_wm_causal_link)、场景(ecos_wm_scenario)三张表。
 * </p>
 */
@Repository
public class WorldModelRepository {

    private static final Logger log = LoggerFactory.getLogger(WorldModelRepository.class);

    private final JdbcTemplate jdbc;

    public WorldModelRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════ Goal RowMapper ═══════════════════════

    private final RowMapper<GoalEntity> GOAL_ROW_MAPPER = (rs, rowNum) -> {
        GoalEntity e = new GoalEntity();
        e.setId(rs.getLong("id"));
        e.setName(rs.getString("name"));
        e.setDescription(rs.getString("description"));
        long pid = rs.getLong("parent_id");
        e.setParentId(rs.wasNull() ? null : pid);
        e.setProgress(rs.getInt("progress"));
        e.setStatus(rs.getString("status"));
        e.setGoalType(rs.getString("goal_type"));
        e.setWeight(getIntOrNull(rs, "weight"));
        e.setOrgId(rs.getString("org_id"));
        e.setOwnerUserId(rs.getString("owner_user_id"));
        e.setStartDate(rs.getDate("start_date") != null
            ? rs.getDate("start_date").toLocalDate() : null);
        e.setEndDate(rs.getDate("end_date") != null
            ? rs.getDate("end_date").toLocalDate() : null);
        e.setTargetValue(rs.getBigDecimal("target_value"));
        e.setCurrentValue(rs.getBigDecimal("current_value"));
        e.setUnit(rs.getString("unit"));
        e.setLinkedWorkflowId(rs.getString("linked_workflow_id"));
        e.setKpiFormula(rs.getString("kpi_formula"));
        e.setMeasureFrequency(rs.getString("measure_frequency"));
        e.setAlertThresholdWarn(rs.getBigDecimal("alert_threshold_warn"));
        e.setAlertThresholdCritical(rs.getBigDecimal("alert_threshold_critical"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        e.setUpdatedAt(rs.getTimestamp("updated_at") != null
            ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return e;
    };

    // ═══════════════ CausalLink RowMapper ════════════════

    private final RowMapper<CausalLinkEntity> LINK_ROW_MAPPER = (rs, rowNum) -> {
        CausalLinkEntity e = new CausalLinkEntity();
        e.setId(rs.getLong("id"));
        e.setSourceGoalId(rs.getLong("source_goal_id"));
        e.setTargetGoalId(rs.getLong("target_goal_id"));
        e.setRelationshipType(rs.getString("relationship_type"));
        e.setDescription(rs.getString("description"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        return e;
    };

    // ═══════════════ Scenario RowMapper ══════════════════

    private final RowMapper<ScenarioEntity> SCENARIO_ROW_MAPPER = (rs, rowNum) -> {
        ScenarioEntity e = new ScenarioEntity();
        e.setId(rs.getLong("id"));
        e.setName(rs.getString("name"));
        e.setDescription(rs.getString("description"));
        e.setConfigJson(rs.getString("config_json"));
        e.setStatus(rs.getString("status"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        e.setUpdatedAt(rs.getTimestamp("updated_at") != null
            ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return e;
    };

    // ═══════════════════════════════════════════════════════
    //  GOAL CRUD
    // ═══════════════════════════════════════════════════════

    public List<GoalEntity> findAllGoals() {
        return jdbc.query("SELECT * FROM ecos_wm_goal ORDER BY id", GOAL_ROW_MAPPER);
    }

    public Optional<GoalEntity> findGoalById(Long id) {
        List<GoalEntity> list = jdbc.query(
            "SELECT * FROM ecos_wm_goal WHERE id = ?", GOAL_ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public long insertGoal(GoalEntity entity) {
        String sql = """
            INSERT INTO ecos_wm_goal (name, description, parent_id, progress, status,
                goal_type, weight, org_id, owner_user_id, start_date, end_date,
                target_value, current_value, unit, linked_workflow_id,
                kpi_formula, measure_frequency, alert_threshold_warn, alert_threshold_critical,
                created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::date, ?::date, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, entity.getName());
            ps.setString(2, entity.getDescription());
            if (entity.getParentId() != null) {
                ps.setLong(3, entity.getParentId());
            } else {
                ps.setNull(3, java.sql.Types.BIGINT);
            }
            ps.setInt(4, entity.getProgress() != null ? entity.getProgress() : 0);
            ps.setString(5, entity.getStatus() != null ? entity.getStatus() : "PLANNED");
            ps.setString(6, entity.getGoalType() != null ? entity.getGoalType() : "STRATEGIC");
            if (entity.getWeight() != null) {
                ps.setInt(7, entity.getWeight());
            } else {
                ps.setNull(7, java.sql.Types.INTEGER);
            }
            ps.setString(8, entity.getOrgId());
            ps.setString(9, entity.getOwnerUserId());
            if (entity.getStartDate() != null) {
                ps.setString(10, entity.getStartDate().toString());
            } else {
                ps.setNull(10, java.sql.Types.VARCHAR);
            }
            if (entity.getEndDate() != null) {
                ps.setString(11, entity.getEndDate().toString());
            } else {
                ps.setNull(11, java.sql.Types.VARCHAR);
            }
            ps.setBigDecimal(12, entity.getTargetValue());
            ps.setBigDecimal(13, entity.getCurrentValue());
            ps.setString(14, entity.getUnit());
            ps.setString(15, entity.getLinkedWorkflowId());
            ps.setString(16, entity.getKpiFormula() != null ? entity.getKpiFormula() : "currentValue/targetValue*100");
            ps.setString(17, entity.getMeasureFrequency() != null ? entity.getMeasureFrequency() : "MONTHLY");
            ps.setBigDecimal(18, entity.getAlertThresholdWarn() != null ? entity.getAlertThresholdWarn() : java.math.BigDecimal.valueOf(80));
            ps.setBigDecimal(19, entity.getAlertThresholdCritical() != null ? entity.getAlertThresholdCritical() : java.math.BigDecimal.valueOf(50));
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) entity.setId(key.longValue());
        log.info("Goal inserted: id={}, name={}", entity.getId(), entity.getName());
        return entity.getId();
    }

    public int updateGoal(Long id, String name, String description, Long parentId,
                          Integer progress, String status,
                          String goalType, Integer weight, String orgId, String ownerUserId,
                          java.time.LocalDate startDate, java.time.LocalDate endDate,
                          java.math.BigDecimal targetValue, java.math.BigDecimal currentValue,
                          String unit, String linkedWorkflowId,
                          String kpiFormula, String measureFrequency,
                          java.math.BigDecimal alertThresholdWarn, java.math.BigDecimal alertThresholdCritical) {
        String sql = """
            UPDATE ecos_wm_goal SET
                name = COALESCE(?, name),
                description = COALESCE(?, description),
                parent_id = COALESCE(?, parent_id),
                progress = COALESCE(?, progress),
                status = COALESCE(?, status),
                goal_type = COALESCE(?, goal_type),
                weight = COALESCE(?, weight),
                org_id = COALESCE(?, org_id),
                owner_user_id = COALESCE(?, owner_user_id),
                start_date = COALESCE(?::date, start_date),
                end_date = COALESCE(?::date, end_date),
                target_value = COALESCE(?, target_value),
                current_value = COALESCE(?, current_value),
                unit = COALESCE(?, unit),
                linked_workflow_id = COALESCE(?, linked_workflow_id),
                kpi_formula = COALESCE(?, kpi_formula),
                measure_frequency = COALESCE(?, measure_frequency),
                alert_threshold_warn = COALESCE(?, alert_threshold_warn),
                alert_threshold_critical = COALESCE(?, alert_threshold_critical),
                updated_at = NOW()
            WHERE id = ?
            """;
        return jdbc.update(sql,
            name, description, parentId, progress, status,
            goalType, weight, orgId, ownerUserId,
            startDate != null ? startDate.toString() : null,
            endDate != null ? endDate.toString() : null,
            targetValue, currentValue, unit, linkedWorkflowId,
            kpiFormula, measureFrequency, alertThresholdWarn, alertThresholdCritical,
            id);
    }

    public int deleteGoalById(Long id) {
        return jdbc.update("DELETE FROM ecos_wm_goal WHERE id = ?", id);
    }

    /** 删除目标关联的所有因果链 */
    public int deleteCausalLinksByGoalId(Long goalId) {
        return jdbc.update(
            "DELETE FROM ecos_wm_causal_link WHERE source_goal_id = ? OR target_goal_id = ?",
            goalId, goalId);
    }

    /** 删除目标关联的业务指标 */
    public int deleteBizMetricsByGoalId(Long goalId) {
        return jdbc.update("DELETE FROM ecos_biz_metric WHERE goal_id = ?", goalId);
    }

    /** 删除目标关联的业务指标 */
    public int deleteBizTargetsByGoalId(Long goalId) {
        return jdbc.update("DELETE FROM ecos_biz_target WHERE goal_id = ?", goalId);
    }

    /** 删除目标关联的项目 */
    public int deleteBizProjectsByGoalId(Long goalId) {
        return jdbc.update("DELETE FROM ecos_biz_project WHERE goal_id = ?", goalId);
    }

    /** 将子目标的 parent_id 置空 */
    public int orphanChildren(Long parentId) {
        return jdbc.update(
            "UPDATE ecos_wm_goal SET parent_id = NULL WHERE parent_id = ?", parentId);
    }

    public long countGoals() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_wm_goal", Long.class);
    }

    private static Integer getIntOrNull(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int val = rs.getInt(column);
        return rs.wasNull() ? null : val;
    }

    // ═══════════════════════════════════════════════════════
    //  CAUSAL LINK CRUD
    // ═══════════════════════════════════════════════════════

    public List<CausalLinkEntity> findAllCausalLinks() {
        return jdbc.query("SELECT * FROM ecos_wm_causal_link ORDER BY id", LINK_ROW_MAPPER);
    }

    public Optional<CausalLinkEntity> findCausalLinkById(Long id) {
        List<CausalLinkEntity> list = jdbc.query(
            "SELECT * FROM ecos_wm_causal_link WHERE id = ?", LINK_ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public long insertCausalLink(CausalLinkEntity entity) {
        String sql = """
            INSERT INTO ecos_wm_causal_link (source_goal_id, target_goal_id, relationship_type, description, created_at)
            VALUES (?, ?, ?, ?, NOW())
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setLong(1, entity.getSourceGoalId());
            ps.setLong(2, entity.getTargetGoalId());
            ps.setString(3, entity.getRelationshipType() != null ? entity.getRelationshipType() : "POSITIVE");
            ps.setString(4, entity.getDescription() != null ? entity.getDescription() : "");
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) entity.setId(key.longValue());
        return entity.getId();
    }

    public int deleteCausalLinkById(Long id) {
        return jdbc.update("DELETE FROM ecos_wm_causal_link WHERE id = ?", id);
    }

    public int updateCausalLink(Long id, Long sourceGoalId, Long targetGoalId,
                                 String relationshipType, String description) {
        return jdbc.update("""
            UPDATE ecos_wm_causal_link
            SET source_goal_id = COALESCE(?, source_goal_id),
                target_goal_id = COALESCE(?, target_goal_id),
                relationship_type = COALESCE(?, relationship_type),
                description = COALESCE(?, description)
            WHERE id = ?
            """, sourceGoalId, targetGoalId, relationshipType, description, id);
    }

    public long countCausalLinks() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_wm_causal_link", Long.class);
    }

    // ═══════════════════════════════════════════════════════
    //  SCENARIO CRUD
    // ═══════════════════════════════════════════════════════

    public List<ScenarioEntity> findAllScenarios() {
        return jdbc.query("SELECT * FROM ecos_wm_scenario ORDER BY id", SCENARIO_ROW_MAPPER);
    }

    public Optional<ScenarioEntity> findScenarioById(Long id) {
        List<ScenarioEntity> list = jdbc.query(
            "SELECT * FROM ecos_wm_scenario WHERE id = ?", SCENARIO_ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public long insertScenario(ScenarioEntity entity) {
        String sql = """
            INSERT INTO ecos_wm_scenario (name, description, config_json, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, NOW(), NOW())
            """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, entity.getName());
            ps.setString(2, entity.getDescription() != null ? entity.getDescription() : "");
            ps.setString(3, entity.getConfigJson() != null ? entity.getConfigJson() : "{}");
            ps.setString(4, entity.getStatus() != null ? entity.getStatus() : "DRAFT");
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key != null) entity.setId(key.longValue());
        log.info("Scenario inserted: id={}, name={}", entity.getId(), entity.getName());
        return entity.getId();
    }

    public int updateScenario(Long id, String name, String description,
                              String configJson, String status) {
        String sql = """
            UPDATE ecos_wm_scenario SET
                name = COALESCE(?, name),
                description = COALESCE(?, description),
                config_json = COALESCE(?, config_json),
                status = COALESCE(?, status),
                updated_at = NOW()
            WHERE id = ?
            """;
        return jdbc.update(sql, name, description, configJson, status, id);
    }

    public int deleteScenarioById(Long id) {
        return jdbc.update("DELETE FROM ecos_wm_scenario WHERE id = ?", id);
    }

    public long countScenarios() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_wm_scenario", Long.class);
    }
}
