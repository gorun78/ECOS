package com.chinacreator.gzcm.engine.ontology.dao;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.model.ActionType;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * B3-a 数据访问收口（R2-B3 / 架构铁律 §2.5 #1 / IR06）：{@code ActionTypeServiceImpl}
 * （动作类型表 ecos_action_type CRUD）的 JDBC 落点。
 *
 * <p>SQL 与 {@code RowMapper} 原样搬运，只搬不改语义；execute 编排
 * （前置条件 / 执行 / 后置动作）保留在 Service 层。</p>
 */
@Repository
public class ActionTypeDao extends JdbcTemplateDaoSupport {

    // ── RowMapper ──────────────────────────────────────

    private final RowMapper<ActionType> ROW_MAPPER = (rs, rn) -> {
        ActionType a = new ActionType();
        a.setId(rs.getString("id"));
        a.setName(rs.getString("name"));
        a.setDescription(rs.getString("description"));
        a.setObjectTypeId(rs.getString("object_type_id"));
        a.setPreconditions(rs.getString("preconditions"));
        a.setPostActions(rs.getString("post_actions"));
        a.setAuditRequired(rs.getBoolean("audit_required"));
        a.setEnabled(rs.getBoolean("enabled"));
        a.setCreatedBy(rs.getString("created_by"));
        a.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        a.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        return a;
    };

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    // ── CRUD ───────────────────────────────────────────

    /**
     * 动作类型列表（objectTypeId 空/空白时全量，按创建时间倒序）。
     */
    public List<ActionType> listActionTypes(String objectTypeId) {
        if (objectTypeId != null && !objectTypeId.isBlank()) {
            return jdbcTemplate.query(
                "SELECT * FROM ecos_action_type WHERE object_type_id = ? ORDER BY created_at DESC",
                ROW_MAPPER, objectTypeId);
        }
        return jdbcTemplate.query("SELECT * FROM ecos_action_type ORDER BY created_at DESC", ROW_MAPPER);
    }

    /**
     * 按主键查询单条动作类型。
     */
    public Optional<ActionType> getActionType(String id) {
        List<ActionType> list = jdbcTemplate.query(
            "SELECT * FROM ecos_action_type WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 新增动作类型。
     */
    public void createActionType(ActionType body) {
        jdbcTemplate.update("""
            INSERT INTO ecos_action_type (id, name, description, object_type_id,
                preconditions, post_actions, audit_required, enabled, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
            body.getId(), body.getName(), body.getDescription(), body.getObjectTypeId(),
            body.getPreconditions(), body.getPostActions(),
            body.getAuditRequired() != null ? body.getAuditRequired() : true,
            body.getEnabled() != null ? body.getEnabled() : true,
            body.getCreatedBy());
    }

    /**
     * 更新动作类型（Service 已合并新旧值）。
     */
    public void updateActionType(String id, String name, String desc, String objTypeId,
                                 String pre, String post, Boolean audit, Boolean enabled) {
        jdbcTemplate.update("""
            UPDATE ecos_action_type SET
                name = ?, description = ?, object_type_id = ?,
                preconditions = ?, post_actions = ?,
                audit_required = ?, enabled = ?,
                updated_at = NOW()
            WHERE id = ?
            """,
            name, desc, objTypeId, pre, post, audit, enabled, id);
    }

    /**
     * 按主键删除动作类型。
     *
     * @return 受影响行数
     */
    public int deleteActionType(String id) {
        return jdbcTemplate.update("DELETE FROM ecos_action_type WHERE id = ?", id);
    }
}
