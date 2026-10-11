package com.chinacreator.gzcm.common.dao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 状态机引擎数据访问收口（R2 B4c-runtime · 架构铁律 §2.5#1 / IR06）。
 *
 * <p>{@code ecos_object_state_machine} / demo 实体表 status 更新全部 raw-SQL 自
 * {@code com.chinacreator.gzcm.common.engine.StateMachineEngine} 逐字节下沉，
 * SQL 文本与参数顺序不变；{@code StateMachineEngine} 退化为转换校验/事件编排门面，
 * SQL 只经本 DAO 执行。</p>
 *
 * <p>注：本 DAO 与 {@code StateMachineEngine} 同处 {@code common-api} 底座，而
 * {@code JdbcTemplateDaoSupport} 居 {@code runtime-core}（runtime-core 依赖 common-api），
 * 反向继承会形成模块环（阻断构建），故本 DAO 直接自持 {@link JdbcTemplate}
 * （{@code *Dao} 为收口点，架构门禁允许）。</p>
 *
 * <p>红线 EN01/IR01：slot 全部参数化。</p>
 */
@Repository
public class StateMachineDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> queryAvailableTransitions(String entityCode, String currentStatus) {
        String sql = """
            SELECT id, entity_code, from_status, to_status, transition_code, transition_name,
                   require_role, guard_rule, side_effect, sort_order
            FROM ecos_object_state_machine
            WHERE entity_code = ? AND from_status = ?
            ORDER BY sort_order
            """;
        return jdbcTemplate.queryForList(sql, entityCode, currentStatus);
    }

    public List<Map<String, Object>> queryTransitionDef(String entityCode, String currentStatus, String transitionCode) {
        String sql = """
            SELECT to_status, transition_name, require_role
            FROM ecos_object_state_machine
            WHERE entity_code = ? AND from_status = ? AND transition_code = ?
            """;
        return jdbcTemplate.queryForList(sql, entityCode, currentStatus, transitionCode);
    }

    /** 更新 demo 实体表的 status（表名由 ENTITY_TABLE 白名单给出）。 */
    public int updateEntityStatus(String table, String newStatus, String objectId) {
        return jdbcTemplate.update("UPDATE " + table + " SET status = ? WHERE id = ?", newStatus, objectId);
    }

    public List<Map<String, Object>> queryDefinitions(String entityCode) {
        String sql = """
            SELECT id, entity_code, from_status, to_status, transition_code, transition_name,
                   require_role, guard_rule, side_effect, sort_order, created_at
            FROM ecos_object_state_machine
            WHERE entity_code = ?
            ORDER BY sort_order
            """;
        return jdbcTemplate.queryForList(sql, entityCode);
    }

    public void insertDefinition(String id, String entityCode, String fromStatus, String toStatus,
                                 String transitionCode, String transitionName, String requireRole,
                                 String guardRule, String sideEffect, int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO ecos_object_state_machine (id, entity_code, from_status, to_status, transition_code, transition_name, require_role, guard_rule, side_effect, sort_order, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                """, id, entityCode, fromStatus, toStatus, transitionCode, transitionName, requireRole, guardRule, sideEffect, sortOrder);
    }

    public int deleteDefinition(String id) {
        return jdbcTemplate.update("DELETE FROM ecos_object_state_machine WHERE id = ?", id);
    }
}
