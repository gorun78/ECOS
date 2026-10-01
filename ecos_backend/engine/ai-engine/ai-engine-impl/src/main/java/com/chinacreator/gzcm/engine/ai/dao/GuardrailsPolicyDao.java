package com.chinacreator.gzcm.engine.ai.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：Guardrail 策略的 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code service.GuardrailsServiceImpl}，只搬不改语义；
 * 校验、参数校验、ID 生成逻辑保留在 Service 层。</p>
 */
@Repository
public class GuardrailsPolicyDao extends JdbcTemplateDaoSupport {

    public List<Map<String, Object>> listPolicies() {
        return jdbcTemplate.queryForList(
            "SELECT * FROM ecos_guardrail_policy ORDER BY created_at DESC");
    }

    public int insertPolicy(String id, String name, String type, String severity, boolean isEnabled) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_guardrail_policy (id, name, type, severity, is_enabled, parameters, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
            id, name, type, severity, isEnabled, "[]");
    }

    public int deletePolicy(String id) {
        return jdbcTemplate.update("DELETE FROM ecos_guardrail_policy WHERE id = ?", id);
    }
}
