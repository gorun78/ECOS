package com.chinacreator.gzcm.sysman.dao;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * World Model 场景读写 DAO（D104）。
 *
 * <p><b>缺陷形态（2026-11-15 活体实证）</b>：{@code ScenarioController} 的四个 CRUD 端点
 * 全部操作进程内 {@code ConcurrentHashMap<String, ScenarioEntity> store}，构造函数还塞进一条
 * 「默认场景」假数据；而 {@code ecos_control.ecos_wm_scenario} 里实存 4 行真实业务场景
 * （Scenario A/B 各两条），从未被该控制器读取。后果与 D103 同型：{@code POST /scenarios}
 * 返回 {@code success:true} 加 UUID，但重启即丢；列表里看到的永远是那条编造的「默认场景」。</p>
 *
 * <p><b>主键口径</b>：表用 {@code nextval} 生成 {@code bigint}，DDL 不可改（DB 红线：只加不删）。
 * 对外 {@link com.chinacreator.gzcm.sysman.controller.model.ScenarioEntity#getId()} 是
 * {@code String}，故写入由库发号、读出转字符串，<b>API 形状零变更</b>。</p>
 *
 * <p><b>config_json</b>：表里有这一列（rego/诊断侧按动作与成本影响读取），
 * {@code ScenarioEntity} 无对应字段。写路径保留传入 JSON 原样；读路径不解析回实体，
 * 避免把未知结构强塞进扁平 VO。</p>
 */
@Repository
public class WorldModelScenarioDao extends com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport {

    /** 场景行（对外 id 字符串化；时间戳原样透传给控制器转毫秒）。 */
    public record ScenarioRow(String id, String name, String description, String configJson,
                              String status, Timestamp createdAt, Timestamp updatedAt) {
    }

    /** 列表：status 为空即全量。 */
    public List<ScenarioRow> list(String status) {
        String base = "SELECT id, name, description, config_json, status, created_at, updated_at "
                + "FROM ecos_wm_scenario ";
        // PostgreSQL 不能推断 `? IS NULL` 的参数类型（could not determine data type of parameter $1），
        // 故按 status 是否为空分两条 SQL，而不是一条带 ?? 的通用语句
        String sql = status == null ? base + "ORDER BY id" : base + "WHERE status = ? ORDER BY id";
        return jdbcTemplate.query(sql, ROW_MAPPER, status == null ? new Object[0] : new Object[]{status});
    }

    public ScenarioRow find(long id) {
        List<ScenarioRow> rows = jdbcTemplate.query("""
                SELECT id, name, description, config_json, status, created_at, updated_at
                FROM ecos_wm_scenario WHERE id = ?
                """, ROW_MAPPER, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static final RowMapper<ScenarioRow> ROW_MAPPER =
            (rs, rowNum) -> new ScenarioRow(
                    String.valueOf(rs.getLong("id")),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("config_json"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at"),
                    rs.getTimestamp("updated_at"));

    /** 建场景：返回库生成主键。 */
    public long insert(String name, String description, String configJson, String status) {
        return GeneratedKeys.insertAndReturnId(jdbcTemplate, """
                INSERT INTO ecos_wm_scenario (name, description, config_json, status)
                VALUES (?, ?, ?, ?)
                """,
                name,
                description == null ? "" : description,
                configJson == null ? "{}" : configJson,
                status == null ? "DRAFT" : status);
    }

    /** 更新：只写非空字段（与旧内存实现「null 保留原值」语义一致）。 */
    public int update(long id, String name, String description, String configJson, String status) {
        return jdbcTemplate.update("""
                UPDATE ecos_wm_scenario SET
                    name = COALESCE(?, name),
                    description = COALESCE(?, description),
                    config_json = COALESCE(?, config_json),
                    status = COALESCE(?, status),
                    updated_at = now()
                WHERE id = ?
                """, name, description, configJson, status, id);
    }

    public int delete(long id) {
        return jdbcTemplate.update("DELETE FROM ecos_wm_scenario WHERE id = ?", id);
    }
}
