package com.chinacreator.gzcm.buszhi.workflow;

import com.chinacreator.gzcm.buszhi.workflow.dao.WorkflowLogDao;

import java.util.List;

import org.springframework.stereotype.Repository;

/**
 * 工作流执行日志门面 — {@code {schema}.ecos_workflow_log} 表（W85）。
 *
 * <p>R2-B5 数据访问收口（架构铁律 §2.5 #1 / IR06）：本层不再直持
 * {@code JdbcTemplate}，全部数据访问下沉到 {@link WorkflowLogDao}
 * （extends {@code com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}）；
 * 本仓库仅作 thin façade，保留原 public API，SQL / RowMapper / schema 注入 / log-id 生成语义原样由
 * {@code dao} 层承担，门面原样委托。R-7 归属①：schema 由 {@code ecos.ontology.control-schema} 注入
 * （默认 {@code ecos_ontology}）；显式列清单，无 {@code SELECT *}；JSON 文本值以 {@code ?} 绑定 String（不再 {@code ?::jsonb}）。</p>
 */
@Repository
public class WorkflowLogRepository {

    private final WorkflowLogDao dao;

    public WorkflowLogRepository(WorkflowLogDao dao) {
        this.dao = dao;
    }

    public List<WorkflowLogEntity> findByInstanceId(String instanceId) {
        return dao.findByInstanceId(instanceId);
    }

    public List<WorkflowLogEntity> findByTraceId(String traceId) {
        return dao.findByTraceId(traceId);
    }

    public int insert(WorkflowLogEntity entity) {
        return dao.insert(entity);
    }

    /** 快捷记录日志 */
    public void log(String instanceId, String nodeId, String nodeType, String eventType,
                    String message, String details, Long durationMs, String traceId) {
        dao.log(instanceId, nodeId, nodeType, eventType, message, details, durationMs, traceId);
    }
}
