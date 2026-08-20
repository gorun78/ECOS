package com.chinacreator.gzcm.engine.data.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Pipeline 执行级溯源记录器（PMO-36 T4）。
 *
 * <p>每个步骤执行后写 ecos_provenance_entry，与决策层溯源共用同一张表（统一溯源）。</p>
 */
@Component
public class PipelineProvenanceRecorder {

    private static final Logger log = LoggerFactory.getLogger(PipelineProvenanceRecorder.class);

    private final JdbcTemplate jdbc;

    public PipelineProvenanceRecorder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 记录步骤执行溯源。
     *
     * @param stepRunId  步骤运行 ID
     * @param taskId     管线任务 ID
     * @param nodeType   节点类型
     * @param status     执行状态
     * @param elapsedMs  耗时
     */
    public void recordStep(String stepRunId, String taskId, String nodeType, String status, long elapsedMs) {
        try {
            String id = UUID.randomUUID().toString().replace("-", "");
            jdbc.update(
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
            log.debug("Pipeline 溯源记录: stepRunId={}, taskId={}, status={}", stepRunId, taskId, status);
        } catch (Exception e) {
            // 溯源失败不影响管线执行
            log.warn("Pipeline 溯源记录失败 (不影响执行): stepRunId={}, error={}", stepRunId, e.getMessage());
        }
    }
}
