package com.chinacreator.gzcm.buszhi.workflow;

import com.chinacreator.gzcm.buszhi.workflow.dao.WorkflowNodeDao;

import java.util.List;

import org.springframework.stereotype.Repository;

/**
 * 工作流节点投影只读门面 — {@code {schema}.ecos_workflow_node}（W86/F03-12 M1 只读路径）。
 *
 * <p>R2-B5 数据访问收口（架构铁律 §2.5 #1 / IR06）：本层不再直持
 * {@code JdbcTemplate}，全部数据访问下沉到 {@link WorkflowNodeDao}
 * （extends {@code com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}）；
 * 本仓库仅作 thin façade，保留原 public API，SQL / RowMapper / schema 注入原样由 {@code dao} 层承担，
 * 门面原样委托。**只读**。</p>
 *
 * <p>软删 {@code {schema}.ecos_workflow} 唯一模型写路径配套：读侧走投影表检索（DR04 MC02：
 * 主表 {@code nodes_json} 只做存取，不参与 WHERE）。写路径（save 时拆解 nodes/edges 入投影表）
 * 延至 F03-12 M2 — V173 已迁移 11 行 v2 遗留数据。</p>
 */
@Repository
public class WorkflowNodeRepository {

    private final WorkflowNodeDao dao;

    public WorkflowNodeRepository(WorkflowNodeDao dao) {
        this.dao = dao;
    }

    /** 只读投影：某工作流全量节点，软删行不过滤（投影表无跨主表软删联动，V173 §5 旧表只停写）。 */
    public List<WorkflowNodeEntity> findByWorkflowId(String wfId) {
        return dao.findByWorkflowId(wfId);
    }

    public int countByWorkflowId(String wfId) {
        return dao.countByWorkflowId(wfId);
    }
}
