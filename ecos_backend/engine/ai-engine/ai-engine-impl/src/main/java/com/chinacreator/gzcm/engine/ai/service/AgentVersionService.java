package com.chinacreator.gzcm.engine.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Agent 版本管理 — 操作 ecos_agent_version 表。
 *
 * <h3>操作</h3>
 * <ul>
 *   <li>{@link #insert(String, String, int, String)} — 插入新版本</li>
 *   <li>{@link #selectByAgentId(String)} — 查所有版本（按 version DESC）</li>
 *   <li>{@link #selectLatest(String)} — 查最新版本</li>
 * </ul>
 */
@Service
public class AgentVersionService {

    private static final Logger log = LoggerFactory.getLogger(AgentVersionService.class);

    private final JdbcTemplate jdbc;

    public AgentVersionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 插入一条 Agent 版本记录。
     *
     * @param id       版本记录 ID
     * @param agentId  Agent 标识
     * @param version  版本号
     * @param config   配置 JSON 文本
     */
    public void insert(String id, String agentId, int version, String config) {
        jdbc.update(
            "INSERT INTO ecos_agent_version (id, agent_id, version, config, created_at) " +
            "VALUES (?, ?, ?, ?, NOW())",
            id, agentId, version, config
        );
        log.debug("AgentVersion inserted: id={} agentId={} version={}", id, agentId, version);
    }

    /**
     * 查询指定 Agent 的所有版本（按 version 降序）。
     *
     * @param agentId Agent 标识
     * @return 版本列表（Map 包含 id, agentId, version, config, createdAt）
     */
    public List<Map<String, Object>> selectByAgentId(String agentId) {
        return jdbc.queryForList(
            "SELECT id, agent_id AS \"agentId\", version, config, created_at AS \"createdAt\" " +
            "FROM ecos_agent_version WHERE agent_id = ? ORDER BY version DESC",
            agentId
        );
    }

    /**
     * 查询指定 Agent 的最新版本。
     *
     * @param agentId Agent 标识
     * @return 最新版本 Map，若无记录则返回 null
     */
    public Map<String, Object> selectLatest(String agentId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, agent_id AS \"agentId\", version, config, created_at AS \"createdAt\" " +
            "FROM ecos_agent_version WHERE agent_id = ? ORDER BY version DESC LIMIT 1",
            agentId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }
}
