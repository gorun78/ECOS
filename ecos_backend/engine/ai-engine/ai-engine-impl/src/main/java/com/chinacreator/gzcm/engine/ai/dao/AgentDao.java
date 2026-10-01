package com.chinacreator.gzcm.engine.ai.dao;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.chinacreator.gzcm.engine.ai.config.AgentEntity;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

/**
 * 数据访问收口（R2-B4-ai / 架构铁律 §2.5 #1 / IR06）：{@code ecos_agent} 配置持久化 CRUD 的 JDBC 落点。
 * <p>
 * 原 {@code config.AgentRepository}，SQL 与 {@code RowMapper} 原样搬运，只搬不改语义。
 * tools 和 knowledge 以 JSON 字符串存储在 TEXT 列中。
 * </p>
 */
@Repository
public class AgentDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(AgentDao.class);


    private final RowMapper<AgentEntity> ROW_MAPPER = (rs, rowNum) -> {
        AgentEntity e = new AgentEntity();
        e.setId(rs.getString("id"));
        e.setName(rs.getString("name"));
        e.setModelProvider(rs.getString("model_provider"));
        e.setModelName(rs.getString("model_name"));
        e.setSystemPrompt(rs.getString("system_prompt"));
        e.setTools(rs.getString("tools"));
        e.setKnowledge(rs.getString("knowledge"));
        e.setStatus(rs.getString("status"));
        e.setCreatedAt(rs.getTimestamp("created_at") != null
            ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        e.setUpdatedAt(rs.getTimestamp("updated_at") != null
            ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return e;
    };

    public List<AgentEntity> findAll(int limit) {
        String sql = "SELECT * FROM ecos_agent ORDER BY updated_at DESC LIMIT ?";
        return jdbcTemplate.query(sql, ROW_MAPPER, limit);
    }

    public Optional<AgentEntity> findById(String id) {
        String sql = "SELECT * FROM ecos_agent WHERE id = ?";
        List<AgentEntity> list = jdbcTemplate.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(AgentEntity entity) {
        String sql = """
            INSERT INTO ecos_agent (id, name, model_provider, model_name, system_prompt, tools, knowledge, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
        return jdbcTemplate.update(sql,
            entity.getId(), entity.getName(), entity.getModelProvider(),
            entity.getModelName(), entity.getSystemPrompt(),
            entity.getTools(), entity.getKnowledge(), entity.getStatus());
    }

    public int update(String id, String name, String modelProvider, String modelName,
                      String systemPrompt, String tools, String knowledge, String status) {
        String sql = """
            UPDATE ecos_agent SET
                name = COALESCE(?, name),
                model_provider = COALESCE(?, model_provider),
                model_name = COALESCE(?, model_name),
                system_prompt = COALESCE(?, system_prompt),
                tools = COALESCE(?, tools),
                knowledge = COALESCE(?, knowledge),
                status = COALESCE(?, status),
                updated_at = NOW()
            WHERE id = ?
            """;
        return jdbcTemplate.update(sql, name, modelProvider, modelName, systemPrompt,
            tools, knowledge, status, id);
    }

    public int updateTools(String id, String tools) {
        String sql = "UPDATE ecos_agent SET tools = ?, updated_at = NOW() WHERE id = ?";
        return jdbcTemplate.update(sql, tools, id);
    }

    public int updateKnowledge(String id, String knowledge) {
        String sql = "UPDATE ecos_agent SET knowledge = ?, updated_at = NOW() WHERE id = ?";
        return jdbcTemplate.update(sql, knowledge, id);
    }

    public int publish(String id) {
        String sql = "UPDATE ecos_agent SET status = 'published', updated_at = NOW() WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    public int deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM ecos_agent WHERE id = ?", id);
    }

    public long count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ecos_agent", Long.class);
    }
}
