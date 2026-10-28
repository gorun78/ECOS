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

    /**
     * 触发一次 PG→图库投影并落台账（附录 A 第 15 条 {@code POST /api/v1/knowledge/graph/sync}）。
     *
     * <p>台账口径：进入即写 RUNNING 行（带 MDC {@code traceId}），投影成功按
     * {@code written} 节点数＋图库边数收口并回填 {@code report_json}（含对账明细）；
     * 投影失败标 FAILED 且<b>异常原样上浮</b>（禁「200 ＋ 空对账」伪成功）。</p>
     *
     * <p>{@code scope} 只作运维标签与台账/回显：投影恒为 PG→图库全量镜像，不退化为按 scope 的部分镜像
     * （PG 为唯一权威，图库为可重放的从属副本）。</p>
     *
     * @param scope      范围标签（空白按 {@code ALL}；超 20 字符仅截台账列宽，不影响投影）
     * @param fullResync {@code true} = 先清空投影子图再全量重写
     * @return 投影结果 {@code {graphForm,attempted,written,notAttempted,reason,reconcileDiff}}
     *         ＋台账 {@code {jobId,scope,traceId}}
     */
    Map<String, Object> runProjection(String scope, boolean fullResync);

    /**
     * 最近一次成功同步的完成时刻（ISO-8601，{@code ecos_kg_sync_log.finished_time} 最大值）。
     *
     * <p>无成功行返回 {@code null}；台账不可读时异常上浮（禁把「读不到」渲染成「从未同步」）。</p>
     */
    String getLastSyncTime();

    /**
     * 分页读取同步日志（附录 A 第 15 条 {@code GET /api/v1/knowledge/graph/sync/logs}）。
     *
     * <p>条目含 {@code traceId}（V179 {@code trace_id}）；台账不可读时异常上浮
     * （由 kb 局部 advice 落 500 {@code ECOS-KB-050}），不做空列表伪装。</p>
     *
     * @param page 页码（从 1 起，小于 1 归 1）
     * @param size 每页条数（1~200，越界钳制）
     * @return {@code {items,total,page,size}}
     */
    Map<String, Object> getSyncLogsPage(int page, int size);
}
