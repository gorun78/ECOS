package com.chinacreator.gzcm.aimod;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate 仓库 — Agent 配置持久化 CRUD
 * <p>
 * 遵循 WorkflowRepository / OntologyRepository 模式。
 * tools 和 knowledge 以 JSON 字符串存储在 TEXT 列中。
 * </p>
 */
@Repository
public class AgentRepository {

    private static final Logger log = LoggerFactory.getLogger(AgentRepository.class);

    private final JdbcTemplate jdbc;

    public AgentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

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
        return jdbc.query(sql, ROW_MAPPER, limit);
    }

    public Optional<AgentEntity> findById(String id) {
        String sql = "SELECT * FROM ecos_agent WHERE id = ?";
        List<AgentEntity> list = jdbc.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insert(AgentEntity entity) {
        String sql = """
            INSERT INTO ecos_agent (id, name, model_provider, model_name, system_prompt, tools, knowledge, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """;
        return jdbc.update(sql,
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
        return jdbc.update(sql, name, modelProvider, modelName, systemPrompt,
            tools, knowledge, status, id);
    }

    public int updateTools(String id, String tools) {
        String sql = "UPDATE ecos_agent SET tools = ?, updated_at = NOW() WHERE id = ?";
        return jdbc.update(sql, tools, id);
    }

    public int updateKnowledge(String id, String knowledge) {
        String sql = "UPDATE ecos_agent SET knowledge = ?, updated_at = NOW() WHERE id = ?";
        return jdbc.update(sql, knowledge, id);
    }

    public int publish(String id) {
        String sql = "UPDATE ecos_agent SET status = 'published', updated_at = NOW() WHERE id = ?";
        return jdbc.update(sql, id);
    }

    public int deleteById(String id) {
        return jdbc.update("DELETE FROM ecos_agent WHERE id = ?", id);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ecos_agent", Long.class);
    }
}
