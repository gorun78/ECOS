package com.chinacreator.gzcm.engine.ontology.dao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c · 架构铁律 §2.5 #1 / IR06）：{@code FunctionCacheManager}
 * 写入 V172 审计表 {@code {schema}.ecos_function_audit} 的 JDBC 落点。
 *
 * <p>MC06 schema（{@code ${ecos.ontology.control-schema:ecos_ontology}}）随 DAO 注入；
 * 显式列清单（IR04）、应用侧 UUID 主键（MC01）、参数化（IR05）、no {@code ::}（MC03）。
 * SQL 文本、参数序全量搬运自 {@code FunctionCacheManager#localFallbackInsert / queryAudit}，只搬不改。</p>
 *
 * <p>两个构造：</p>
 * <ul>
 *   <li>Spring 主注入：{@code (String schema)} — {@code jdbcTemplate} 由父类 {@code @Autowired} 字段注入；</li>
 *   <li>手动构造（测试）：{@code (JdbcTemplate jdbc, String schema)} — 直接绑定 mock。</li>
 * </ul>
 */
@Repository
public class FunctionAuditDao extends JdbcTemplateDaoSupport {

    private final String schema;

    public FunctionAuditDao(
            @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.schema = schema;
    }

    /** 测试手动构造：直接绑定 mock 的 {@link JdbcTemplate}。 */
    public FunctionAuditDao(JdbcTemplate jdbc, String schema) {
        this.jdbcTemplate = jdbc;
        this.schema = schema;
    }

    public String getSchema() {
        return schema;
    }

    /**
     * 本地兜底：INSERT 到 {@code {schema}.ecos_function_audit}（V172）。
     *
     * <p>SQL 与参数序保持源代码原样（18 个占位 + 2 个 NOW()）。</p>
     */
    public int localFallbackInsert(String uuid, String functionId, String functionName,
                                   String expression, String objectType, String paramsJson,
                                   String resultSummary, int durationMs, int isCacheHit,
                                   String callerId, String traceId, String status, String errorMessage,
                                   String createBy, String updateBy) {
        String sql = "INSERT INTO " + schema + ".ecos_function_audit ("
                + "id, function_id, function_name, expression, object_type, params_json,"
                + " result_summary, duration_ms, is_cache_hit, caller_id, trace_id,"
                + " status, error_message, create_time, update_time, create_by, update_by,"
                + " is_deleted, version_no, domain) VALUES ("
                + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql,
                uuid,
                functionId,
                functionName,
                expression,
                objectType,
                paramsJson,
                resultSummary,
                durationMs,
                isCacheHit,
                callerId,
                traceId,
                status,
                errorMessage,
                createBy,
                updateBy,
                0,
                "1",
                "default");
    }

    /**
     * 审计分页查询：{@code SELECT cols FROM {schema}.ecos_function_audit WHERE is_deleted = 0 ...}。
     *
     * <p>搬迁自 {@code FunctionCacheManager#queryAudit}（保留 count 与 items 两次查询 + try/catch 语义）；
     * 参数不需要额外包装，DAO 直接返回分页 items 与 total。</p>
     */
    public Map<String, Object> queryAudit(String status, String callerId, int page, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        String table = schema + ".ecos_function_audit";
        String cols = "id, function_id, function_name, expression, object_type, params_json,"
                + " result_summary, duration_ms, is_cache_hit, caller_id, trace_id,"
                + " status, error_message, create_time, update_time, version_no, is_deleted, domain";

        StringBuilder sql = new StringBuilder("SELECT ").append(cols).append(" FROM ").append(table)
                .append(" WHERE is_deleted = 0");
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM ").append(table)
                .append(" WHERE is_deleted = 0");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            countSql.append(" AND status = ?");
            params.add(status);
        }
        if (callerId != null && !callerId.isBlank()) {
            sql.append(" AND caller_id = ?");
            countSql.append(" AND caller_id = ?");
            params.add(callerId);
        }

        int total;
        try {
            Integer count = jdbcTemplate.queryForObject(countSql.toString(), Integer.class, params.toArray());
            total = count != null ? count : 0;
        } catch (Exception e) {
            total = 0;
        }

        sql.append(" ORDER BY create_time DESC LIMIT ? OFFSET ?");
        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageSize);
        dataParams.add((page - 1) * pageSize);

        List<Map<String, Object>> items;
        try {
            items = jdbcTemplate.queryForList(sql.toString(), dataParams.toArray());
        } catch (Exception e) {
            items = List.of();
        }

        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }
}
