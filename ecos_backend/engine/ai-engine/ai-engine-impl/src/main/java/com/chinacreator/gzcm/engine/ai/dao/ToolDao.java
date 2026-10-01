package com.chinacreator.gzcm.engine.ai.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：工具执行器 / 注册中心的 JDBC 落点。
 *
 * <p>SQL 原样搬运自原 {@code service.ToolExecutorService} 与 {@code service.ToolRegistry}，
 * 只搬不改语义；H8-T4 安全闸/RestTemplate/Rest-client 逻辑保留在 Service 层。</p>
 */
@Repository
public class ToolDao extends JdbcTemplateDaoSupport {

    /** 加载单个工具定义（producer: ToolExecutorService.loadFromDatabase）。 */
    public List<Map<String, Object>> loadToolDefinition(String toolName) {
        return jdbcTemplate.queryForList(
            "SELECT code, name, description, tool_type, endpoint_url, http_method, schema_json " +
            "FROM ecos_ai.ecos_tool_definition WHERE code = ? AND status = 'ACTIVE'",
            toolName);
    }

    /** 查询用户是否强制沙盒（producer: ToolExecutorService.querySandboxMandatory）。 */
    public List<Map<String, Object>> querySandboxMandatory(String userId) {
        return jdbcTemplate.queryForList(
            "SELECT sandbox_mandatory FROM td_user_security_profile " +
            "WHERE scope_type = 'user' AND user_id = ? LIMIT 1",
            userId);
    }

    /** 写沙盒拦截审计（producer: ToolExecutorService.writeSandboxBlockAudit）。 */
    public void insertSandboxAudit(String userId, String toolName, String changesJson) {
        jdbcTemplate.update(
            "INSERT INTO ecos_audit_log (username, operation, entity_type, entity_id, changes, ip_address, created_at, category) " +
            "VALUES (?, ?, ?, ?, CAST(? AS jsonb), NULL, NOW(), ?)",
            userId, "SANDBOX_BLOCK", "TOOL_EXECUTION", toolName, changesJson, "security");
    }

    /** 通用 SELECT 执行（producer: ToolExecutorService.executeSql）。 */
    public List<Map<String, Object>> queryForList(String sql, Object[] params) {
        return jdbcTemplate.queryForList(sql, params);
    }

    /** 通用 query_db 工具执行（producer: ToolRegistry.registerQueryDb）。 */
    public List<Map<String, Object>> executeSelect(String sql, Object[] params) {
        if (params == null || params.length == 0) {
            return jdbcTemplate.queryForList(sql);
        }
        return jdbcTemplate.queryForList(sql, params);
    }
}
