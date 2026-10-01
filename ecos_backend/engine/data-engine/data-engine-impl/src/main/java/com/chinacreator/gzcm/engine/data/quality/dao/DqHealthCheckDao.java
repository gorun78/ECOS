package com.chinacreator.gzcm.engine.data.quality.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * DqHealthCheckDao — DQ 自检只读元数据探测（information_schema / to_regclass）。
 * <p>
 * PMO-48-A T6 / PMO-74 H9-T4。原内联在 {@code DqHealthCheckService} 的 JdbcTemplate 调用
 * 收口到本 DAO（架构铁律 §2.5 #1：*Service 禁私持 JdbcTemplate，必过 DAO）。
 * 只读：不建表、不写库；不吞异常，DataAccessException 原样上抛由 Service 决定降级策略。
 *
 * @author PMO-74 H9-T4
 */
@Repository
public class DqHealthCheckDao extends JdbcTemplateDaoSupport {

    /**
     * 探测 schema 是否存在（information_schema.schemata 计数）。
     *
     * @param schemaName schema 名（如 {@code ecos_data}）
     * @return 命中数（可能为 null；Service 侧做 null → 0 归一 + debug 日志）
     */
    public Integer countSchema(String schemaName) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*)::int FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, schemaName);
    }

    /**
     * 探测表是否存在（to_regclass('schema.table')，表缺失时返回 NULL）。
     *
     * @param qualifiedName 带 schema 前缀的表名（如 {@code ecos_data.dq_rule}）
     * @return 表 OID；表不存在时可能为 {@code null} 或 0
     */
    public Long resolveTableOid(String qualifiedName) {
        return jdbcTemplate.queryForObject("SELECT to_regclass(?)::int8", Long.class, qualifiedName);
    }
}
