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
     * 异步执行本体→KG 映射（模式化入口，PMO 批次 B3-2 T3）。
     *
     * @param objectType 对象类型，{@code ALL} 表示全量本体
     * @param jobId      任务 ID
     * @param mode       {@code FULL}=全量抽取（读全部映射本体实例，忽略水位线）；
     *                   {@code INCREMENTAL}=按持久化水位线增量抽取
     */
    void runKgMapper(String objectType, String jobId, String mode);

    /**
     * 异步执行本体→KG 映射（模式化 + dry-run 入口，PMO 批次 B3-2 T4）。
     *
     * @param objectType 对象类型，{@code ALL} 表示全量本体
     * @param jobId      任务 ID
     * @param mode       {@code FULL} / {@code INCREMENTAL}
     * @param dryRun     {@code true} = 预览（只统计不落库）
     */
    void runKgMapper(String objectType, String jobId, String mode, boolean dryRun);

    /**
     * 图谱构建（模式化提交入口，PMO 批次 B3-2 T3；供
     * {@code POST /api/v1/knowledge/graph/build} 的 {@code mode} 入参调用）。
     *
     * <p>写 {@code kg_sync_log} RUNNING 行后异步执行骨架对齐 + 实例抽取。
     *
     * @param jobId 任务 ID（由调用方生成，用于溯源/回滚）
     * @param mode  {@code FULL} / {@code INCREMENTAL}（缺省按 FULL）
     */
    void triggerBuildSync(String jobId, String mode);

    /**
     * 图谱构建（模式化 + dry-run 提交入口，PMO 批次 B3-2 T4）。
     *
     * @param jobId  任务 ID
     * @param mode   {@code FULL} / {@code INCREMENTAL}
     * @param dryRun {@code true} = dry-run 预览（不写图谱，仅回填报告）
     */
    void triggerBuildSync(String jobId, String mode, boolean dryRun);

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
