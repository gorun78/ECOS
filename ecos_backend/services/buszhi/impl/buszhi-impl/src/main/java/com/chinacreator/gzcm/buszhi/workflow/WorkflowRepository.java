package com.chinacreator.gzcm.buszhi.workflow;

import com.chinacreator.gzcm.buszhi.workflow.dao.WorkflowDao;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

/**
 * 工作流持久化门面（W85/W86，《详细设计-03-本体域》）。
 *
 * <p>R2-B5 数据访问收口（架构铁律 §2.5 #1 / IR06）：本层不再直持 JDBC，
 * 全部数据访问下沉到 {@link WorkflowDao}（extends runtime 数据底座 {@code JdbcTemplateDaoSupport}）；
 * 本仓库仅作 thin façade，保留原 public API，SQL / RowMapper / schema 注入 / 软删语义（W86/O-18）
 * 完整由 {@code dao} 层承担，门面原样委托。</p>
 *
 * <p>投影表（{@code ecos_workflow_node}/{@code ecos_workflow_edge}）走
 * {@link WorkflowNodeRepository}/{@link WorkflowEdgeRepository} 只读；
 * 写路径（save 时拆解 nodes/edges 入投影表）延至 F03-12 M2 — V173 已迁移 11 行 v2 遗留数据。
 */
@Repository
public class WorkflowRepository {

    private final WorkflowDao dao;

    public WorkflowRepository(WorkflowDao dao) {
        this.dao = dao;
    }

    public List<WorkflowEntity> findAll(int limit) {
        return dao.findAll(limit);
    }

    public Optional<WorkflowEntity> findById(String id) {
        return dao.findById(id);
    }

    public int insert(WorkflowEntity entity) {
        return dao.insert(entity);
    }

    public int update(String id, String name, String description, String mode, String nodes, String edges) {
        return dao.update(id, name, description, mode, nodes, edges);
    }

    public int publish(String id) {
        return dao.publish(id);
    }

    /**
     * 软删（W86/O-18，DR05 禁物理 DELETE）：委托 {@code dao} 标记 {@code is_deleted=1} +
     * 审计列 {@code update_by} 取当前操作人（{@link WorkflowActorContext}，未鉴权回退 {@code system}）。
     */
    public int deleteById(String id) {
        return dao.deleteById(id);
    }

    public long count() {
        return dao.count();
    }
}
