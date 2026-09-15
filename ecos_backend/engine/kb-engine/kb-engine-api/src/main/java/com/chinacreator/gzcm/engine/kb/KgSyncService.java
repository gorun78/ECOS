package com.chinacreator.gzcm.engine.kb;

import java.util.List;
import java.util.Map;

public interface KgSyncService {

    List<Map<String, Object>> getSyncStatus();

    String getOverallStatus();

    void triggerFullSync(String syncId);

    void triggerObjectSync(String syncId, String objectType);

    List<Map<String, Object>> getSyncLogs(int limit);

    /**
     * 异步执行本体→KG 映射（PMO-50 T2/T4 共用入口，impl 侧 {@code @Async} 代理调度）。
     *
     * @param objectType 对象类型，{@code ALL} 表示全量
     * @param jobId      任务 ID，执行结果回填 {@code ecos_knowledge.kg_sync_log}
     */
    void runKgMapper(String objectType, String jobId);

    /**
     * 按 job 维度查询同步日志（PMO-50 T4 /api/v1/knowledge/sync/jobs/{jobId}/logs）。
     *
     * @param jobId 任务 ID
     * @param limit 返回条数上限
     */
    List<Map<String, Object>> getJobLogs(String jobId, int limit);

    /**
     * 按 job 维度列出同步任务（PMO-54 GraphBuilderTab 任务列表，{@code GET /api/v1/knowledge/sync/jobs}）。
     *
     * @param limit 返回条数上限
     */
    List<Map<String, Object>> listJobs(int limit);
}
