package com.chinacreator.gzcm.buszhi.workflow;

import com.chinacreator.gzcm.buszhi.workflow.dao.WorkflowEdgeDao;

import java.util.List;

import org.springframework.stereotype.Repository;

/**
 * 工作流边投影只读门面 — {@code {schema}.ecos_workflow_edge}（W86/F03-12 M1 只读路径）。
 *
 * <p>R2-B5 数据访问收口（架构铁律 §2.5 #1 / IR06）：本层不再直持
 * {@code JdbcTemplate}，全部数据访问下沉到 {@link WorkflowEdgeDao}
 * （extends {@code com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}）；
 * 本仓库仅作 thin façade，保留原 public API，SQL / RowMapper / schema 注入原样由 {@code dao} 层承担，
 * 门面原样委托。**只读**，写路径拆解延至 F03-12 M2。</p>
 */
@Repository
public class WorkflowEdgeRepository {

    private final WorkflowEdgeDao dao;

    public WorkflowEdgeRepository(WorkflowEdgeDao dao) {
        this.dao = dao;
    }

    public List<WorkflowEdgeEntity> findByWorkflowId(String wfId) {
        return dao.findByWorkflowId(wfId);
    }

    public int countByWorkflowId(String wfId) {
        return dao.countByWorkflowId(wfId);
    }
}
