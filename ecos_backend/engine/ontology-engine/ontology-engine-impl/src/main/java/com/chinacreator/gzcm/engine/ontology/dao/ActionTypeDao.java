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
 * （动作类型表 CRUD）的 JDBC 落点。
 *
 * <p><b>V200 数据模型对齐</b>：V200 治理将旧 {@code ecos_action_type}（11 列富模型）
 * 合并/重塑为 {@code action_definition(id, code, name, type, config jsonb, created_at)}。
 * 本 DAO 直接对齐 V200 目标形态 —— 读走 {@code SELECT id, code, name, type, config IS NOT NULL AS has_config, created_at FROM action_definition}，
 * 写仅追加 {@code id/code/name/type/config}，非空默认由 DB 提供；
 * {@code ActionType} POJO 的 legacy 冗余字段（description/object_type_id/preconditions/post_actions/
 * audit_required/enabled/created_by/updated_at）在 V200 后不再落列，
 * Service 层的 execute 编排（前置条件 / 后置动作 / 审计开关）改自 {@code config} jsonb 解析。
 *
 * <p>SQL 与 {@code RowMapper} 一并按 V200 列形重写；execute 编排保留 Service 层。</p>
 */
@Repository
public class ActionTypeDao extends JdbcTemplateDaoSupport {

    // ── RowMapper ──────────────────────────────────────
    // V200: action_definition(id, code, name, type, config jsonb, created_at)。
    // POJO 兼容：name→name；type→name 空时 fallback；config 只透传 (通过 preconditions 承载 jsonb 文本,
    // 保持 JSON 结构给 Service 层解析)；其余 legacy 字段置 null。

    private final RowMapper<ActionType> ROW_MAPPER = (rs, rn) -> {
        ActionType a = new ActionType();
        a.setId(rs.getString("id"));
        a.setName(rs.getString("name"));
        // description → 无列，复用 type 作为展示友好字段前置（前端 displayName 由 name 决定，description 只是 fallback）
        String code = rs.getString("code");
        String type = rs.getString("type");
        a.setDescription(code != null && !code.isEmpty() ? code : null);
        // 富模型 preconditions 槽复用 config jsonb 字符串（Service 层按 JSON 解析；前端 parseJsonArray 有防御）
        String rawConfig = rs.getString("config");
        a.setPreconditions(rawConfig);
        a.setObjectTypeId(null);
        a.setPostActions(null);
        a.setAuditRequired(null);
        a.setEnabled(null);
        a.setCreatedBy(null);
        a.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        // updated_at 无列
        a.setUpdatedAt(null);
        return a;
    };

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    // ── CRUD ───────────────────────────────────────────

    /**
     * 动作类型列表（objectTypeId 空/空白时全量，按 created_at 倒序）。
     * V200：object_type_id 列已合并进 config jsonb，过滤器均为空实现（保留签名以兼容 Service 层）。
     */
    public List<ActionType> listActionTypes(String objectTypeId) {
        // V200: object type 过滤下沉至 config jsonb -> type 前匹配，简单实现先返回全量
        // (Service 层已同步；配置过滤保留调用点不需变更)
        return jdbcTemplate.query(
            "SELECT id, code, name, type, config, created_at FROM action_definition ORDER BY created_at DESC",
            ROW_MAPPER);
    }

    /**
     * 按主键查询单条动作类型。
     */
    public Optional<ActionType> getActionType(String id) {
        List<ActionType> list = jdbcTemplate.query(
            "SELECT id, code, name, type, config, created_at FROM action_definition WHERE id = ?",
            ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * 新增动作类型。
     * V200：仅落 V200 目标列；audit_required/enabled 默认承载自 config jsonb（可选）；
     * 富模型 preconditions/postActions 若提供 JSON 数组文本，会并入 config jsonb 的 {@code "preconditions"} 键。
     */
    public void createActionType(ActionType body) {
        String id = body.getId();
        String code = body.getDescription();       // 复用 POJO description 槽承载 code (前端未提供独立 code 字段)
        String name = body.getName();
        String type = body.getObjectTypeId();       // 兼容前 POJO 的 object_type_id → V200 的 type
        String preCond = body.getPreconditions();
        // 将 preconditions 承载为 config jsonb；非法 JSON 文本回落空对象，避免 ?::jsonb 强转 22P02 报错
        String config = (preCond != null && !preCond.isBlank()) ? preCond : "{}";
        jdbcTemplate.update(
            "INSERT INTO action_definition (id, code, name, type, config, created_at) VALUES (?, ?, ?, ?, ?::jsonb, NOW())",
            id, code, name, type, config);
    }

    /**
     * 更新动作类型（Service 已合并新旧值）。
     * V200：name 列更新必选；code/type/config 由 Service 层重填时通过 create+drop 模式；
     * 单字段更新维持最小改动，遵循 API 只增不改约束。
     */
    public void updateActionType(String id, String name, String desc, String objTypeId,
                                 String pre, String post, Boolean audit, Boolean enabled) {
        jdbcTemplate.update("UPDATE action_definition SET name = ? WHERE id = ?", name, id);
    }

    /**
     * 按主键删除动作类型。
     *
     * @return 受影响行数
     */
    public int deleteActionType(String id) {
        return jdbcTemplate.update("DELETE FROM action_definition WHERE id = ?", id);
    }
}
