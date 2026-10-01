package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.dao.AgentVersionDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p>R2-B4-ai 收口：SQL 落点下沉至 {@link AgentVersionDao}。</p>
 */
@Service
public class AgentVersionService {

    private static final Logger log = LoggerFactory.getLogger(AgentVersionService.class);

    private final AgentVersionDao dao;

    public AgentVersionService(AgentVersionDao dao) {
        this.dao = dao;
    }

    public void insert(String id, String agentId, int version, String config) {
        dao.insert(id, agentId, version, config);
        log.debug("AgentVersion inserted: id={} agentId={} version={}", id, agentId, version);
    }

    public List<Map<String, Object>> selectByAgentId(String agentId) {
        return dao.selectByAgentId(agentId);
    }

    public Map<String, Object> selectLatest(String agentId) {
        List<Map<String, Object>> rows = dao.selectLatest(agentId);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
