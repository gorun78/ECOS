package com.chinacreator.gzcm.engine.ai.service;

import com.chinacreator.gzcm.engine.ai.dao.DiagnosticAgentDao;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 诊断 Agent 服务 — 从 DiagnosticAgentController 下沉的 JDBC 访问层。
 *
 * <p>包含工具定义查询、Agent 配置查询、目标偏差/因果链/场景查询等 JDBC 操作，
 * 数据访问统一收口至 {@link DiagnosticAgentDao}。
 * Fallback 硬编码数据仍保留在 Controller（与原行为一致）。</p>
 */
@Service
public class DiagnosticAgentService {

    private final DiagnosticAgentDao diagnosticAgentDao;

    public DiagnosticAgentService(DiagnosticAgentDao diagnosticAgentDao) {
        this.diagnosticAgentDao = diagnosticAgentDao;
    }

    /**
     * 查询诊断工具定义。
     *
     * @param sql    完整 SQL (Controller 拼接 category 条件后传入)
     * @param params SQL 参数数组
     * @return 工具行列表
     */
    public List<Map<String, Object>> queryTools(String sql, Object[] params) {
        return diagnosticAgentDao.queryTools(sql, params);
    }

    /**
     * 查询 Agent 配置（ecos_agent 表）。
     *
     * @param agentId Agent ID
     * @return Agent 配置行列表（通常 0 或 1 行）
     */
    public List<Map<String, Object>> queryAgentConfig(String agentId) {
        return diagnosticAgentDao.queryAgentConfig(agentId);
    }

    /**
     * 查询目标偏差（ecos_wm_goal，状态 AT_RISK / CRITICAL）。
     *
     * @return 偏差行列表
     */
    public List<Map<String, Object>> queryGoalDeviations() {
        return diagnosticAgentDao.queryGoalDeviations();
    }

    /**
     * 查询因果链（ecos_wm_causal_link JOIN ecos_wm_goal）。
     *
     * @return 因果链行列表
     */
    public List<Map<String, Object>> queryCausalChains() {
        return diagnosticAgentDao.queryCausalChains();
    }

    /**
     * 查询场景（ecos_wm_scenario）。
     *
     * @return 场景行列表
     */
    public List<Map<String, Object>> queryScenarios() {
        return diagnosticAgentDao.queryScenarios();
    }

    public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
        return diagnosticAgentDao.query(sql, rowMapper, args);
    }
    public <T> List<T> queryList(String sql, RowMapper<T> rowMapper, Object... args) {
        return diagnosticAgentDao.query(sql, rowMapper, args);
    }

}
