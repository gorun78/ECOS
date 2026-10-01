package com.chinacreator.gzcm.engine.ai.dao;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：{@code ecos_agent_version} 表的 JDBC 落点。
 * <p>SQL 原样搬运自原 {@code service.AgentVersionService}，只搬不改语义。</p>
 */
@Repository
public class AgentVersionDao extends JdbcTemplateDaoSupport {

    public void insert(String id, String agentId, int version, String config) {
        jdbcTemplate.update(
            "INSERT INTO ecos_agent_version (id, agent_id, version, config, created_at) " +
            "VALUES (?, ?, ?, ?, NOW())",
            id, agentId, version, config
        );
    }

    public List<Map<String, Object>> selectByAgentId(String agentId) {
        return jdbcTemplate.queryForList(
            "SELECT id, agent_id AS \"agentId\", version, config, created_at AS \"createdAt\" " +
            "FROM ecos_agent_version WHERE agent_id = ? ORDER BY version DESC",
            agentId
        );
    }

    public List<Map<String, Object>> selectLatest(String agentId) {
        return jdbcTemplate.queryForList(
            "SELECT id, agent_id AS \"agentId\", version, config, created_at AS \"createdAt\" " +
            "FROM ecos_agent_version WHERE agent_id = ? ORDER BY version DESC LIMIT 1",
            agentId
        );
    }
}
