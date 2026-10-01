package com.chinacreator.gzcm.buszhi.workflow;

import com.chinacreator.gzcm.buszhi.workflow.dao.WorkflowInstanceDao;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

/**
 * 工作流实例门面 — {@code {schema}.ecos_workflow_instance} 表 CRUD（W85）。
 *
 * <p>R2-B5 数据访问收口（架构铁律 §2.5 #1 / IR06）：本层不再直持
 * {@code JdbcTemplate}，全部数据访问下沉到 {@link WorkflowInstanceDao}
 * （extends {@code com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}）；
 * 本仓库仅作 thin façade，保留原 public API，SQL / RowMapper / schema 注入原样由 {@code dao} 层承担，
 * 门面原样委托。R-7 归属①：schema 由 {@code ecos.ontology.control-schema} 注入（默认 {@code ecos_ontology}）；
 * 显式列清单，无 {@code SELECT *}；JSON 文本值以 {@code ?} 绑定 String（DR07 治理，不再 {@code ?::jsonb}）。</p>
 */
@Repository
public class WorkflowInstanceRepository {

    private final WorkflowInstanceDao dao;

    public WorkflowInstanceRepository(WorkflowInstanceDao dao) {
        this.dao = dao;
    }

    public Optional<WorkflowInstanceEntity> findById(String id) {
        return dao.findById(id);
    }

    public List<WorkflowInstanceEntity> findAll(int limit) {
        return dao.findAll(limit);
    }

    public List<WorkflowInstanceEntity> findByWorkflowId(String workflowId, int limit) {
        return dao.findByWorkflowId(workflowId, limit);
    }

    public int insert(WorkflowInstanceEntity entity) {
        return dao.insert(entity);
    }

    public int updateStatus(String id, String status, String errorMessage) {
        return dao.updateStatus(id, status, errorMessage);
    }

    public int updateCurrentNodes(String id, String currentNodeIds) {
        return dao.updateCurrentNodes(id, currentNodeIds);
    }

    public int complete(String id) {
        return dao.complete(id);
    }
}
