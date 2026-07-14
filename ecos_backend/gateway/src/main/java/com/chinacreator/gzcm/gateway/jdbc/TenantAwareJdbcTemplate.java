package com.chinacreator.gzcm.gateway.jdbc;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 租户感知的 JdbcTemplate 包装。
 * 对涉及租户隔离表的 SQL 自动追加 WHERE tenant_id=? 过滤条件。
 * admin/system 级操作（TenantContextHolder 为空）直接放行不过滤。
 *
 * <p>按 MVP 简化版策略，只重写最常用的 5 类方法：
 * query / queryForList / queryForMap / queryForObject / update。
 */
public class TenantAwareJdbcTemplate extends JdbcTemplate {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareJdbcTemplate.class);

    /** 需要租户隔离的业务表（P1-1 已添加 tenant_id 列的 8 张表 + 2 张辅助表） */
    private static final Set<String> TENANT_TABLES = new HashSet<>(Arrays.asList(
        "ecos_objects", "ecos_object_relation",
        "ecos_dq_rule",
        "ecos_workflow_instance",
        "ecos_glossary_term"
    ));

    /** 匹配 SQL 中涉及的表名：FROM/JOIN/UPDATE/INTO 后的 ecos_xxx */
    private static final Pattern TABLE_PATTERN = Pattern.compile(
        "\\b(FROM|JOIN|UPDATE|INTO)\\s+(ecos_\\w+)", Pattern.CASE_INSENSITIVE);

    /** 检测 SQL 中是否已有 WHERE 子句（简单版，不处理子查询中的 WHERE） */
    private static final Pattern WHERE_PATTERN = Pattern.compile(
        "\\bWHERE\\b", Pattern.CASE_INSENSITIVE);

    public TenantAwareJdbcTemplate(DataSource dataSource) {
        super(dataSource);
    }

    // ──────────── 核心辅助方法 ────────────

    /**
     * 判断当前 SQL 是否需要追加租户过滤条件。
     * 条件：(1) TenantContextHolder 中有 tenantId；且 (2) SQL 涉及租户隔离表。
     */
    private boolean shouldEnrich(String sql) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return false;
        }
        if (sql == null) {
            return false;
        }
        Matcher m = TABLE_PATTERN.matcher(sql);
        while (m.find()) {
            String tableName = m.group(2).toLowerCase();
            if (TENANT_TABLES.contains(tableName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在 SQL 中追加 tenant_id 过滤条件。
     * 如果已有 WHERE → 在 ORDER BY/LIMIT/OFFSET 之前追加 "AND tenant_id = ?"
     * 否则 → 在 ORDER BY/LIMIT/OFFSET 之前追加 "WHERE tenant_id = ?"
     */
    String enrichSql(String sql) {
        if (!shouldEnrich(sql)) {
            return sql;
        }
        // 找到 ORDER BY / LIMIT / OFFSET 位置，在此前插入
        String upper = sql.toUpperCase();
        int insertPos = sql.length();
        for (String kw : new String[]{" ORDER BY ", " LIMIT ", " OFFSET "}) {
            int pos = upper.indexOf(kw);
            if (pos > 0 && pos < insertPos) {
                insertPos = pos;
            }
        }
        String prefix = sql.substring(0, insertPos);
        String suffix = sql.substring(insertPos);
        if (WHERE_PATTERN.matcher(sql).find()) {
            return prefix + " AND tenant_id = ? " + suffix;
        } else {
            return prefix + " WHERE tenant_id = ? " + suffix;
        }
    }

    /**
     * 在参数数组末尾追加 tenantId。
     * @param sql  原始 SQL（用于判断是否需要追加）
     * @param args 原始参数数组
     * @return 追加后的参数数组
     */
    Object[] enrichArgs(String sql, Object[] args) {
        if (!shouldEnrich(sql)) {
            return args;
        }
        String tenantId = TenantContextHolder.getTenantId();
        if (args == null || args.length == 0) {
            return new Object[] { tenantId };
        }
        Object[] enriched = Arrays.copyOf(args, args.length + 1);
        enriched[args.length] = tenantId;
        return enriched;
    }

    // ──────────── query ────────────

    @Override
    public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
        return super.query(enrichSql(sql), enrichArgs(sql, null), rowMapper);
    }

    @Override
    public <T> List<T> query(String sql, Object[] args, RowMapper<T> rowMapper) {
        return super.query(enrichSql(sql), enrichArgs(sql, args), rowMapper);
    }

    @Override
    public <T> List<T> query(String sql, Object[] args, int[] argTypes, RowMapper<T> rowMapper) {
        return super.query(enrichSql(sql), enrichArgs(sql, args), argTypes, rowMapper);
    }

    @Override
    public <T> List<T> query(String sql, PreparedStatementSetter pss, RowMapper<T> rowMapper) {
        // PreparedStatementSetter 无法简单追加参数，记录警告并委托原方法
        if (shouldEnrich(sql)) {
            log.warn("Tenant enrichment skipped for PreparedStatementSetter query: tenantId={}",
                TenantContextHolder.getTenantId());
        }
        return super.query(sql, pss, rowMapper);
    }

    // ──────────── queryForList ────────────

    @Override
    public List<Map<String, Object>> queryForList(String sql) {
        return super.queryForList(enrichSql(sql), enrichArgs(sql, null));
    }

    @Override
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return super.queryForList(enrichSql(sql), enrichArgs(sql, args));
    }

    @Override
    public <T> List<T> queryForList(String sql, Class<T> elementType) {
        return super.queryForList(enrichSql(sql), elementType, enrichArgs(sql, null));
    }

    @Override
    public <T> List<T> queryForList(String sql, Class<T> elementType, Object... args) {
        return super.queryForList(enrichSql(sql), elementType, enrichArgs(sql, args));
    }

    // ──────────── queryForMap ────────────

    @Override
    public Map<String, Object> queryForMap(String sql) {
        return super.queryForMap(enrichSql(sql), enrichArgs(sql, null));
    }

    @Override
    public Map<String, Object> queryForMap(String sql, Object... args) {
        return super.queryForMap(enrichSql(sql), enrichArgs(sql, args));
    }

    // ──────────── queryForObject ────────────

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType) {
        return super.queryForObject(enrichSql(sql), requiredType, enrichArgs(sql, null));
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        return super.queryForObject(enrichSql(sql), requiredType, enrichArgs(sql, args));
    }

    @Override
    public <T> T queryForObject(String sql, Object[] args, Class<T> requiredType) {
        return super.queryForObject(enrichSql(sql), enrichArgs(sql, args), requiredType);
    }

    @Override
    public <T> T queryForObject(String sql, RowMapper<T> rowMapper) {
        return super.queryForObject(enrichSql(sql), enrichArgs(sql, null), rowMapper);
    }

    @Override
    public <T> T queryForObject(String sql, Object[] args, RowMapper<T> rowMapper) {
        return super.queryForObject(enrichSql(sql), enrichArgs(sql, args), rowMapper);
    }

    @Override
    public <T> T queryForObject(String sql, Object[] args, int[] argTypes, RowMapper<T> rowMapper) {
        return super.queryForObject(enrichSql(sql), enrichArgs(sql, args), argTypes, rowMapper);
    }

    // ──────────── update ────────────

    @Override
    public int update(String sql) {
        return super.update(enrichSql(sql), enrichArgs(sql, null));
    }

    @Override
    public int update(String sql, Object... args) {
        return super.update(enrichSql(sql), enrichArgs(sql, args));
    }

    @Override
    public int update(String sql, Object[] args, int[] argTypes) {
        return super.update(enrichSql(sql), enrichArgs(sql, args), argTypes);
    }
}
