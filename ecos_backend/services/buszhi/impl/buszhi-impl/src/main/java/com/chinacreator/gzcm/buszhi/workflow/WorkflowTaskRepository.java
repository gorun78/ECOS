package com.chinacreator.gzcm.buszhi.workflow;

import com.chinacreator.gzcm.buszhi.workflow.dao.WorkflowTaskDao;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

/**
 * 工作流任务门面 — {@code {schema}.ecos_workflow_task} 表 CRUD（W85）。
 *
 * <p>R2-B5 数据访问收口（架构铁律 §2.5 #1 / IR06）：本层不再直持
 * {@code JdbcTemplate}，全部数据访问下沉到 {@link WorkflowTaskDao}
 * （extends {@code com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}）；
 * 本仓库仅作 thin façade，保留原 public API，SQL / RowMapper / schema 注入原样由 {@code dao} 层承担，
 * 门面原样委托。R-7 归属①：schema 由 {@code ecos.ontology.control-schema} 注入（默认 {@code ecos_ontology}）；
 * 显式列清单，无 {@code SELECT *}；JSON 文本值以 {@code ?} 绑定 String（不再 {@code ?::jsonb}）。</p>
 */
@Repository
public class WorkflowTaskRepository {

    private final WorkflowTaskDao dao;

    public WorkflowTaskRepository(WorkflowTaskDao dao) {
        this.dao = dao;
    }

    public Optional<WorkflowTaskEntity> findById(String id) {
        return dao.findById(id);
    }

    public List<WorkflowTaskEntity> findByInstanceId(String instanceId) {
        return dao.findByInstanceId(instanceId);
    }

    public List<WorkflowTaskEntity> findByAssignee(String assignee, int limit) {
        return dao.findByAssignee(assignee, limit);
    }

    public List<WorkflowTaskEntity> findByStatus(String status, int limit) {
        return dao.findByStatus(status, limit);
    }

    /** 查找待认领的任务（无 assignee + 状态 New） */
    public List<WorkflowTaskEntity> findUnassigned(int limit) {
        return dao.findUnassigned(limit);
    }

    public int insert(WorkflowTaskEntity entity) {
        return dao.insert(entity);
    }

    public int claim(String taskId, String assignee) {
        return dao.claim(taskId, assignee);
    }

    public int complete(String taskId, String result, String completedBy) {
        return dao.complete(taskId, result, completedBy);
    }

    public int transfer(String taskId, String newAssignee) {
        return dao.transfer(taskId, newAssignee);
    }

    public int reject(String taskId, String reason) {
        return dao.reject(taskId, reason);
    }
}
