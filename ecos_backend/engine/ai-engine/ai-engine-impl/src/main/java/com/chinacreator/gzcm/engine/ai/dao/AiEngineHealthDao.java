package com.chinacreator.gzcm.engine.ai.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：AI 引擎健康检查的 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code service.AiEngineHealthService}，只搬不改语义；
 * 异常降级（返回 -1）逻辑保留在 Service 层。</p>
 */
@Repository
public class AiEngineHealthDao extends JdbcTemplateDaoSupport {

    public Integer pingDb() {
        return jdbcTemplate.queryForObject("SELECT 1", Integer.class);
    }

    public Long countAgents() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_agent_registry", Long.class);
    }

    public Long countMissions() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_mission", Long.class);
    }

    public Long countGuardrailPolicies() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_guardrail_policy", Long.class);
    }

    public Long countExecutionPlans() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_execution_plan", Long.class);
    }
}
