package com.chinacreator.gzcm.engine.ai.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：Action 桥的 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code service.ActionBridgeServiceImpl}，只搬不改语义；
 * Fallback 默认值与审计吞异常逻辑保留在 Service 层。</p>
 */
@Repository
public class ActionBridgeDao extends JdbcTemplateDaoSupport {

    public List<Map<String, Object>> listOntologyActions() {
        return jdbcTemplate.queryForList(
            "SELECT * FROM ecos_ontology_action ORDER BY created_at DESC LIMIT 100");
    }

    public void insertActionAudit(String actionCode, String llmOutputSnippet) {
        jdbcTemplate.update(
            "INSERT INTO ecos_action_audit (action_code, llm_output_snippet, executed_at) " +
            "VALUES (?, ?, NOW())",
            actionCode, llmOutputSnippet);
    }
}
