package com.chinacreator.gzcm.workspace.blueprint;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 场景蓝图数据访问（DDL V271 {@code workspace.ecos_scenario_blueprint}）。
 *
 * <p>架构铁律 §2.5#1 三层纪律：raw-SQL 一律收口 DAO（{@code ..dao..} 包形态的
 * {@link JdbcTemplateDaoSupport} 子类，本类落 {@code ..blueprint..} 包但类名不以
 * Service/Aggregator/Runtime/Controller 结尾，同型合规；Service/Controller 禁私持
 * JdbcTemplate，见 {@code DataAccessArchTest}）。
 *
 * <p>ST07：表落 workspace 场景层 schema（DDL 单源 {@code docs/50-db_script/02-flyway-migration/gateway/V271}）；
 * MC01 应用侧 UUID；MC03 索引前置条件并入 WHERE（is_deleted）；DR05 approval_required 列名带 required 语义。</p>
 */
@Repository
public class ScenarioBlueprintDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "workspace.ecos_scenario_blueprint";

    private static final String COLUMNS =
            "id, tenant_id, org_id, scenario_id, version, status, goal_statement, goal_success_text, " +
            "scope_json_text, dependencies_text, capabilities_text, gaps_text, risks_text, tests_text, " +
            "content_json, artifact_digest, approval_required, approval_ref, context_digest, " +
            "release_artifact_ref, released_time, trace_id, actor, transition_reason, domain, version_no, " +
            "create_time, update_time, create_by, update_by, is_deleted";

    private final RowMapper<ScenarioBlueprint> ROW_MAPPER = (rs, rowNum) -> {
        ScenarioBlueprint b = new ScenarioBlueprint();
        b.setId(rs.getString("id"));
        b.setTenantId(rs.getString("tenant_id"));
        b.setOrgId(rs.getString("org_id"));
        b.setScenarioId(rs.getString("scenario_id"));
        b.setVersion(rs.getInt("version"));
        b.setStatus(rs.getString("status"));
        b.setGoalStatement(rs.getString("goal_statement"));
        b.setGoalSuccessText(rs.getString("goal_success_text"));
        b.setScopeJsonText(rs.getString("scope_json_text"));
        b.setDependenciesText(rs.getString("dependencies_text"));
        b.setCapabilitiesText(rs.getString("capabilities_text"));
        b.setGapsText(rs.getString("gaps_text"));
        b.setRisksText(rs.getString("risks_text"));
        b.setTestsText(rs.getString("tests_text"));
        b.setContentJson(rs.getString("content_json"));
        b.setArtifactDigest(rs.getString("artifact_digest"));
        b.setApprovalRequired(rs.getInt("approval_required"));
        b.setApprovalRef(rs.getString("approval_ref"));
        b.setContextDigest(rs.getString("context_digest"));
        b.setReleaseArtifactRef(rs.getString("release_artifact_ref"));
        Timestamp released = rs.getTimestamp("released_time");
        b.setReleasedTime(released == null ? null : released.toLocalDateTime());
        b.setTraceId(rs.getString("trace_id"));
        b.setActor(rs.getString("actor"));
        b.setTransitionReason(rs.getString("transition_reason"));
        b.setDomain(rs.getString("domain"));
        b.setVersionNo(rs.getString("version_no"));
        Timestamp ct = rs.getTimestamp("create_time");
        b.setCreateTime(ct == null ? null : ct.toLocalDateTime());
        Timestamp ut = rs.getTimestamp("update_time");
        b.setUpdateTime(ut == null ? null : ut.toLocalDateTime());
        b.setCreateBy(rs.getString("create_by"));
        b.setUpdateBy(rs.getString("update_by"));
        b.setIsDeleted(rs.getInt("is_deleted"));
        return b;
    };

    // ═══════════════ 读 ═══════════════

    /** 按主键读（未删除）。 */
    public List<ScenarioBlueprint> findById(String id) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE id = ? AND is_deleted = 0",
                ROW_MAPPER, id);
    }

    /** 场景下全部版本（升序，未删除）。 */
    public List<ScenarioBlueprint> listByScenario(String scenarioId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM " + TABLE
                        + " WHERE scenario_id = ? AND is_deleted = 0 ORDER BY version ASC",
                ROW_MAPPER, scenarioId);
    }

    /** 场景当前最新版本号（0 = 无版本；取 is_deleted=0）。 */
    public Integer maxVersionOfScenario(String scenarioId) {
        List<Integer> rows = jdbcTemplate.queryForList(
                "SELECT COALESCE(MAX(version), 0) FROM " + TABLE
                        + " WHERE scenario_id = ? AND is_deleted = 0",
                Integer.class, scenarioId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 按 (scenario, version) 读（未删除；uk_v271_bp_scen_ver 保证至多一行）。 */
    public List<ScenarioBlueprint> findByScenarioAndVersion(String scenarioId, int version) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM " + TABLE
                        + " WHERE scenario_id = ? AND version = ? AND is_deleted = 0",
                ROW_MAPPER, scenarioId, version);
    }

    // ═══════════════ 写 ═══════════════

    /**
     * 新建蓝图行（version + uk_v271_bp_scen_ver 冲突 → DataIntegrityViolation，
     * 由 Service 捕获转 409 幂等冲突）。
     */
    public int insert(ScenarioBlueprint b) {
        return jdbcTemplate.update(
                "INSERT INTO " + TABLE + " (" + COLUMNS + ") VALUES (" +
                "? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ?" +
                ")",
                b.getId(), b.getTenantId(), b.getOrgId(), b.getScenarioId(), b.getVersion(),
                b.getStatus() == null ? "DRAFT" : b.getStatus(),
                b.getGoalStatement(), b.getGoalSuccessText(), b.getScopeJsonText(),
                b.getDependenciesText(), b.getCapabilitiesText(), b.getGapsText(),
                b.getRisksText(), b.getTestsText(), b.getContentJson(), b.getArtifactDigest(),
                b.getApprovalRequired(), b.getApprovalRef(), b.getContextDigest(),
                b.getReleaseArtifactRef(),
                b.getReleasedTime() == null ? null : Timestamp.valueOf(b.getReleasedTime()),
                b.getTraceId(), b.getActor(), b.getTransitionReason(),
                b.getDomain() == null ? "default" : b.getDomain(),
                b.getVersionNo() == null ? "1" : b.getVersionNo(),
                b.getCreateTime() == null ? new Timestamp(System.currentTimeMillis()) : Timestamp.valueOf(b.getCreateTime()),
                b.getUpdateTime() == null ? new Timestamp(System.currentTimeMillis()) : Timestamp.valueOf(b.getUpdateTime()),
                b.getCreateBy(), b.getUpdateBy(), 0);
    }

    /** 状态迁移落库（status/actor/原因/traceId/updated 七列，带从态 WHERE 守卫防并发双迁）。 */
    public int updateTransition(String id, String toStatus, String actor, String reason,
                                String traceId, String fromStatus) {
        return jdbcTemplate.update(
                "UPDATE " + TABLE + " SET status = ?, actor = ?, transition_reason = ?, trace_id = ?, " +
                "update_time = NOW(), update_by = ? " +
                "WHERE id = ? AND status = ? AND is_deleted = 0",
                toStatus, actor, reason, traceId, actor, id, fromStatus);
    }

    /** 审批引用回填（approval 端点）。 */
    public int updateApprovalRef(String id, String approvalRef, String actor) {
        return jdbcTemplate.update(
                "UPDATE " + TABLE + " SET approval_ref = ?, update_time = NOW(), update_by = ? " +
                "WHERE id = ? AND is_deleted = 0",
                approvalRef, actor, id);
    }

    /** 发布落库（状态/摘要/发布引用/发布时间，带状态守卫）。 */
    public int updateRelease(String id, String toStatus, String artifactDigest,
                             String releaseArtifactRef, String actor, String fromStatus) {
        return jdbcTemplate.update(
                "UPDATE " + TABLE + " SET status = ?, artifact_digest = ?, release_artifact_ref = ?, " +
                "actor = ?, trace_id = ?, transition_reason = ?, released_time = NOW(), " +
                "update_time = NOW(), update_by = ? " +
                "WHERE id = ? AND status = ? AND is_deleted = 0",
                toStatus, artifactDigest, releaseArtifactRef, actor, actor, actor, actor,
                id, fromStatus);
    }
}
