package com.chinacreator.gzcm.sysman.dao;

import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * WorldModelGraph 写入 DAO（D103）。
 *
 * <p><b>缺陷形态（2026-11-15 活体实证）</b>：{@code WorldModelGraphController} 的五个写端点
 * （建/改/删目标、建/删因果链）此前全部操作进程内 {@code ConcurrentHashMap}，而读面查
 * PostgreSQL {@code ecos_wm_goal}。后果是 {@code POST /goals} 返回 {@code success:true}
 * 加一个 UUID，但该行从未落库（实测创建后 {@code SELECT count(*)} 不变），重启即丢；
 * 且两张表主键都是 {@code bigint}，内存写的 UUID 与库里数值 ID 是两套形态，
 * 导致「父目标不存在: 1」这类自相矛盾的拒绝。</p>
 *
 * <p><b>主键口径</b>：表用 {@code nextval} 序列，DDL 不可改（DB 红线：只加不删）。
 * 因此写入一律由库生成 {@code bigint}，对外仍以字符串返回（`ApiResponse` 里的 id 是
 * {@code String}），<b>对外 API 形状不变</b>，只在内部做字符串↔long 的严格转换。</p>
 */
@Repository
public class WorldModelGraphWriteDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    /** 建目标：返回库生成的数值主键。 */
    public long insertGoal(String name, String description, Long parentId, Integer progress,
                           String status, String goalType, Integer weight, String orgId,
                           String ownerUserId) {
        String sql = """
            INSERT INTO ecos_wm_goal
                (name, description, parent_id, progress, status, goal_type, weight, org_id, owner_user_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        return GeneratedKeys.insertAndReturnId(jdbcTemplate, sql,
                name,
                description == null ? "" : description,
                parentId,
                progress == null ? 0 : progress,
                status == null ? "PLANNED" : status,
                goalType == null ? "STRATEGIC" : goalType,
                weight == null ? 50 : weight,
                orgId,
                ownerUserId);
    }

    /** 更新目标：只写非空字段（与旧内存实现的「null 保留原值」语义一致）。 */
    public int updateGoal(long id, String name, String description, Long parentId,
                          Integer progress, String status, String goalType, Integer weight) {
        String sql = """
            UPDATE ecos_wm_goal SET
                name = COALESCE(?, name),
                description = COALESCE(?, description),
                parent_id = COALESCE(?, parent_id),
                progress = COALESCE(?, progress),
                status = COALESCE(?, status),
                goal_type = COALESCE(?, goal_type),
                weight = COALESCE(?, weight),
                updated_at = now()
            WHERE id = ?
            """;
        return jdbcTemplate.update(sql, name, description, parentId, progress, status,
                goalType, weight, id);
    }

    public int deleteGoal(long id) {
        return jdbcTemplate.update("DELETE FROM ecos_wm_goal WHERE id = ?", id);
    }

    /** 目标是否存在（父子校验用，替代旧的内存 containsKey）。 */
    public boolean goalExists(long id) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_wm_goal WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    /**
     * 目标详情行（详情面用，与列表同一批列）。
     *
     * <p>{@code id}/{@code parentId} 保持原始 long/{@code Long}：wire 面由控制器的 VO 决定，
     * DAO 不替控制器做字符串化，避免同一字段两处各写一遍转换。</p>
     */
    public record GoalRow(long id, String name, String description, Long parentId, int progress,
                          String status, String goalType, Object weight, String orgId,
                          String ownerUserId, java.sql.Timestamp createdAt, java.sql.Timestamp updatedAt) {
    }

    /** 单条目标。 */
    public GoalRow findGoal(long id) {
        String sql = """
            SELECT id, name, description, parent_id, progress, status, goal_type, weight,
                   org_id, owner_user_id, created_at, updated_at
            FROM ecos_wm_goal WHERE id = ?
            """;
        List<GoalRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            long pid = rs.getLong("parent_id");
            Long parentId = rs.wasNull() ? null : pid;
            return new GoalRow(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    parentId,
                    rs.getInt("progress"),
                    rs.getString("status"),
                    rs.getString("goal_type"),
                    rs.getObject("weight"),
                    rs.getString("org_id"),
                    rs.getString("owner_user_id"),
                    rs.getTimestamp("created_at"),
                    rs.getTimestamp("updated_at"));
        }, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 建因果链：source/target 必须是库中真实目标（外键会兜底，这里先显式校验给出可读错误）。 */
    public long insertLink(long sourceGoalId, long targetGoalId, String relationshipType,
                           String description, BigDecimal correlation) {
        String sql = """
            INSERT INTO ecos_wm_causal_link
                (source_goal_id, target_goal_id, relationship_type, description, correlation_coefficient)
            VALUES (?, ?, ?, ?, ?)
            """;
        return GeneratedKeys.insertAndReturnId(jdbcTemplate, sql,
                sourceGoalId, targetGoalId,
                relationshipType == null ? "POSITIVE" : relationshipType,
                description == null ? "" : description,
                correlation == null ? BigDecimal.ZERO : correlation);
    }

    public int deleteLink(long id) {
        return jdbcTemplate.update("DELETE FROM ecos_wm_causal_link WHERE id = ?", id);
    }

    /** 删除目标时级联删除的链数（外键已 ON DELETE CASCADE，这里先数出来回报）。 */
    public int countLinksOfGoal(long goalId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_wm_causal_link WHERE source_goal_id = ? OR target_goal_id = ?",
                Integer.class, goalId, goalId);
        return n == null ? 0 : n;
    }

    /** 因果链行（id 与两端目标按字符串出，与既有 wire 面一致）。 */
    public record LinkRow(String id, String sourceGoalId, String targetGoalId, String label,
                          String description, java.sql.Timestamp createdAt) {
    }

    public LinkRow findLink(long id) {
        List<LinkRow> rows = jdbcTemplate.query("""
                SELECT id, source_goal_id, target_goal_id, relationship_type, description, created_at
                FROM ecos_wm_causal_link WHERE id = ?
                """, (rs, rowNum) -> new LinkRow(
                        String.valueOf(rs.getLong("id")),
                        String.valueOf(rs.getLong("source_goal_id")),
                        String.valueOf(rs.getLong("target_goal_id")),
                        rs.getString("relationship_type"),
                        rs.getString("description"),
                        rs.getTimestamp("created_at")),
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
