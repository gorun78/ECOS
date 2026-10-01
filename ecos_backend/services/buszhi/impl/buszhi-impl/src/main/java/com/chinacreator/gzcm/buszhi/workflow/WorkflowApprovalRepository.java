package com.chinacreator.gzcm.buszhi.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 工作流审批记录仓库 — {@code {schema}.ecos_workflow_approval} 表 CRUD（W85/O-17）。
 *
 * <p>W85/R-7 归属①：审批表与主表同源控制域 {@code ecos_ontology}（V173 已迁移 approval 从 public 到
 * ecos_ontology，旧 public 裸名 {@code ecos_workflow_approval} 结构必达 relation does not exist）。
 * 所有 SQL schema 限定 + 显式列清单 + 无 {@code SELECT *} + 无 {@code ?::jsonb}。
 *
 * <p>JSON 列走 V173 合规列 {@code form_json TEXT}（MC02：现网 jsonb 列 {@code form_data} 只停写），
 * 服务端以字符串绑定（Jackson 序列化已在实体/服务侧完成），不再依赖 PG {@code ::jsonb} 转换。
 */
@Repository
public class WorkflowApprovalRepository {

    /**
     * R-7 归属① workflow→控制域 Eagle schema；V173 已迁移 approval 从 public 到 ecos_ontology。
     * 运行期实际 schema 经 {@code ecos.ontology.control-schema} 注入；本常量为规范基准值，
     * 供源扫描可达性守卫（WorkflowApprovalTableReachableTest）与文档引用。
     */
    public static final String SCHEMA = "ecos_ontology";

    private static final Logger log = LoggerFactory.getLogger(WorkflowApprovalRepository.class);
    private final JdbcTemplate jdbc;
    private final String schema;

    public WorkflowApprovalRepository(JdbcTemplate jdbc,
                                      @Value("${ecos.ontology.control-schema:ecos_ontology}") String schema) {
        this.jdbc = jdbc;
        this.schema = schema;
    }

    /** 显式列清单 — 与 V173 §3 合规形态一致（form_json TEXT 承接 JSON 文本，form_data 旧 jsonb 列只停写）。 */
    private static final String COLS =
        "id, task_id, instance_id, approver, decision, opinion, form_json, created_at";

    private final RowMapper<WorkflowApprovalEntity> ROW_MAPPER = (rs, rowNum) -> {
        WorkflowApprovalEntity e = new WorkflowApprovalEntity();
        e.setId(rs.getString("id"));
        e.setTaskId(rs.getString("task_id"));
        e.setInstanceId(rs.getString("instance_id"));
        e.setApprover(rs.getString("approver"));
        e.setDecision(rs.getString("decision"));
        e.setOpinion(rs.getString("opinion"));
        e.setFormData(rs.getString("form_json"));
        e.setCreatedAt(toLDT(rs.getTimestamp("created_at")));
        return e;
    };

    private static LocalDateTime toLDT(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    public Optional<WorkflowApprovalEntity> findById(String id) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_approval WHERE id = ?";
        List<WorkflowApprovalEntity> list = jdbc.query(sql, ROW_MAPPER, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<WorkflowApprovalEntity> findByTaskId(String taskId) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_approval WHERE task_id = ? ORDER BY created_at";
        return jdbc.query(sql, ROW_MAPPER, taskId);
    }

    public List<WorkflowApprovalEntity> findByInstanceId(String instanceId) {
        String sql = "SELECT " + COLS + " FROM " + schema + ".ecos_workflow_approval WHERE instance_id = ? ORDER BY created_at";
        return jdbc.query(sql, ROW_MAPPER, instanceId);
    }

    public int insert(WorkflowApprovalEntity entity) {
        String sql = """
            INSERT INTO %s.ecos_workflow_approval
            (id, task_id, instance_id, approver, decision, opinion, form_json, created_at)
            VALUES (?,?,?,?,?,?,?, NOW())
            """.formatted(schema);
        return jdbc.update(sql,
            entity.getId(), entity.getTaskId(), entity.getInstanceId(),
            entity.getApprover(), entity.getDecision(), entity.getOpinion(), entity.getFormData());
    }
}
