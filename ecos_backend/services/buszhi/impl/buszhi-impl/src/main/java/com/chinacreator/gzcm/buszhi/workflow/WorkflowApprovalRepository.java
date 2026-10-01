package com.chinacreator.gzcm.buszhi.workflow;

import com.chinacreator.gzcm.buszhi.workflow.dao.WorkflowApprovalDao;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

/**
 * 工作流审批记录门面 — {@code {schema}.ecos_workflow_approval} 表 CRUD（W85/O-17）。
 *
 * <p>R2-B5 数据访问收口（架构铁律 §2.5 #1 / IR06）：本层不再直持
 * {@code JdbcTemplate}，全部数据访问下沉到 {@link WorkflowApprovalDao}
 * （extends {@code com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport}）；
 * 本仓库仅作 thin façade，保留原 public API，SQL / RowMapper / schema 注入 /
 * JSON 列（form_json TEXT，MC02）字符串绑定语义完整由 {@code dao} 层承担，门面原样委托。</p>
 *
 * <p>W85/R-7 归属①：审批表与主表同源控制域 {@code ecos_ontology}（V173 已迁移 approval 从 public 到
 * ecos_ontology），所有 SQL schema 限定 + 显式列清单 + 无 {@code SELECT *} + 无 {@code ?::jsonb}。</p>
 */
@Repository
public class WorkflowApprovalRepository {

    /**
     * R-7 归属① workflow→控制域 Eagle schema；V173 已迁移 approval 从 public 到 ecos_ontology。
     * 运行期实际 schema 经 {@code ecos.ontology.control-schema} 注入；本常量为规范基准值，
     * 供源扫描可达性守卫（WorkflowApprovalTableReachableTest）与文档引用。
     */
    public static final String SCHEMA = "ecos_ontology";

    private final WorkflowApprovalDao dao;

    public WorkflowApprovalRepository(WorkflowApprovalDao dao) {
        this.dao = dao;
    }

    public Optional<WorkflowApprovalEntity> findById(String id) {
        return dao.findById(id);
    }

    public List<WorkflowApprovalEntity> findByTaskId(String taskId) {
        return dao.findByTaskId(taskId);
    }

    public List<WorkflowApprovalEntity> findByInstanceId(String instanceId) {
        return dao.findByInstanceId(instanceId);
    }

    public int insert(WorkflowApprovalEntity entity) {
        return dao.insert(entity);
    }
}
