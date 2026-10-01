package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.dao.PipelineProvenanceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Pipeline 执行级溯源记录器（PMO-36 T4）。
 *
 * <p>每个步骤执行后写 ecos_provenance_entry，与决策层溯源共用同一张表（统一溯源）。</p>
 *
 * <p>B1 收口（R2-B1 / 架构铁律 §2.5 #1 / IR06）：raw-SQL 已下沉
 * {@link PipelineProvenanceDao}，本 Service 只调 DAO 方法、不再依赖 JdbcTemplate。</p>
 */
@Component
public class PipelineProvenanceRecorder {

    private static final Logger log = LoggerFactory.getLogger(PipelineProvenanceRecorder.class);

    private final PipelineProvenanceDao provenanceDao;

    public PipelineProvenanceRecorder(PipelineProvenanceDao provenanceDao) {
        this.provenanceDao = provenanceDao;
    }

    public void recordStep(String stepRunId, String taskId, String nodeType, String status, long elapsedMs) {
        try {
            provenanceDao.insertStepProvenance(stepRunId, taskId, nodeType, status, elapsedMs);
            log.debug("Pipeline 溯源记录: stepRunId={}, taskId={}, status={}", stepRunId, taskId, status);
        } catch (Exception e) {
            // 溯源失败不影响管线执行
            log.warn("Pipeline 溯源记录失败 (不影响执行): stepRunId={}, error={}", stepRunId, e.getMessage());
        }
    }
}
