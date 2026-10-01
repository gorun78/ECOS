package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * B1 数据访问收口（R2-B1 / 架构铁律 §2.5 #1 / IR06）：Pipeline 执行级溯源 raw-SQL 落点。
 *
 * <p>原 {@code service.PipelineProvenanceRecorder} 直接持 {@code jdbcTemplate} 内联
 * INSERT，位于 {@code ..service..} 包被 {@code DataAccessArchTest} 挡回。SQL 下沉至此
 * DAO（{@code ..dao..} 由 runtime-core {@code MyBatisConfig}/扫描约定覆盖），Service 侧
 * 只调本 DAO 方法。INSERT 语义原样搬迁，不改表名/字段。</p>
 */
@Repository
public class PipelineProvenanceDao extends JdbcTemplateDaoSupport {

    /**
     * 单条步骤溯源落库（{@code ecos_provenance_entry}）。调用方自行吞异常（不影响管线执行），
     * 本 DAO 仅负责执行 SQL。
     */
    public void insertStepProvenance(String stepRunId, String taskId, String nodeType,
                                     String status, long elapsedMs) {
        String id = UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
            "INSERT INTO ecos_provenance_entry " +
            "(id, entity_type, entity_id, source_type, source_ref, agent, activity, detail, timestamp) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            id,
            "pipeline_step",
            stepRunId,
            "PIPELINE",
            taskId,
            "data-engine",
            "execute",
            String.format("nodeType=%s, status=%s, elapsedMs=%d", nodeType, status, elapsedMs),
            new Timestamp(System.currentTimeMillis())
        );
    }
}
