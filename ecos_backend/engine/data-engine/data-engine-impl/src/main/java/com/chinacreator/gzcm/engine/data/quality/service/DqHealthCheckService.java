package com.chinacreator.gzcm.engine.data.quality.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * DqHealthCheckService — DQ 模块自检的元数据探测数据访问层（PMO-48-A T6 / PMO-74 H9-T4）。
 * <p>
 * 本类只做两件只读探测（{@code information_schema.schemata} 与 {@code to_regclass}），
 * 取代原先内联在 {@code DqSelfCheckController} 的 {@code JdbcTemplate} 调用
 * （硬规则「Controller 禁止直用 JdbcTemplate — 必过 Service」，铁律 §1.5）。
 * 降级策略（异常 → issue 文案）仍由 Controller 决定，故本类<b>不吞异常</b>，
 * 数据访问失败原样抛出 {@code DataAccessException}。
 * <p>
 * 只读：不建表、不写库（Flyway 禁用、schema 变更走 ADR）。
 *
 * @author PMO-74 H9-T4
 */
@Service
public class DqHealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(DqHealthCheckService.class);

    private final JdbcTemplate jdbc;

    public DqHealthCheckService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 探测 schema 是否存在（{@code information_schema.schemata} 计数）。
     *
     * @param schemaName schema 名（如 {@code ecos_dq}）
     * @return 命中数（0 = 不存在）
     */
    public int countSchema(String schemaName) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*)::int FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class, schemaName);
        log.debug("DQ selfcheck schema probe: schema={}, count={}", schemaName, count);
        return count == null ? 0 : count;
    }

    /**
     * 探测表是否存在（{@code to_regclass('schema.table')}，表缺失时返回 NULL）。
     *
     * @param qualifiedName 带 schema 前缀的表名（如 {@code ecos_dq.dq_rule}）
     * @return 表 OID；表不存在时可能为 {@code null} 或 0
     */
    public Long resolveTableOid(String qualifiedName) {
        return jdbc.queryForObject("SELECT to_regclass(?)::int8", Long.class, qualifiedName);
    }
}
