package com.chinacreator.gzcm.engine.data.metadata;

import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.repository.DataSourceRepository;
import com.chinacreator.gzcm.engine.data.service.MetadataRowCountService;
import com.chinacreator.gzcm.runtime.access.connector.Connector;
import com.chinacreator.gzcm.runtime.access.connector.ConnectorFactory;
import com.chinacreator.gzcm.engine.data.service.ResourceSyncService;
import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PMO-37 METADATA_COLLECT 任务执行器。
 * <p>
 * 执行流程：
 * <ol>
 *   <li>读取数据源（含 metadata_config 策略）</li>
 *   <li>listResources（表/视图清单）同步落库</li>
 *   <li>按 countMethod 统计行数：EXACT/ESTIMATE（默认）/OFF</li>
 *   <li>逐表更新 td_data_resource.record_count（-1=未采集）</li>
 *   <li>写 td_metadata_collect_log 审计 + 刷新 td_datasource.last_collect_time</li>
 * </ol>
 * Connector 获取失败按 PMO-37 重试机制整任务重试 1 次（5s 间隔）；
 * 单表失败不阻塞——记失败清单，status=PARTIAL？经 TaskManagementServiceImpl
 * 约定只回 SUCCEEDED/FAILED，PARTIAL 通过 result JSON 的 partial 字段表达。
 *
 * @author DataBridge Datanet Team
 */
@Component
public class MetadataCollectTaskExecutor implements ITaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(MetadataCollectTaskExecutor.class);
    public static final String EXECUTOR_TYPE = "METADATA_COLLECT";

    private final DataSourceRepository dsRepository;
    private final ConnectorFactory connectorFactory;
    private final MetadataRowCountService rowCountService;
    private final com.chinacreator.gzcm.engine.data.service.ResourceSyncService resourceSync;
    private final MetadataCollectGitArchive gitArchive;
    /**
     * 用于取「解密回填密码后」的连接配置（PMO-49 起密码只存 password_enc）。
     * 本类处于 DataSourceServiceImpl 依赖链下游，加 @Lazy 断环。
     */
    private final com.chinacreator.gzcm.engine.data.DataSourceService dataSourceService;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, Boolean> cancelFlags = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pauseFlags = new ConcurrentHashMap<>();

    public MetadataCollectTaskExecutor(DataSourceRepository dsRepository,
                                       ConnectorFactory connectorFactory,
                                       MetadataRowCountService rowCountService,
                                       com.chinacreator.gzcm.engine.data.service.ResourceSyncService resourceSync,
                                       MetadataCollectGitArchive gitArchive,
                                       @org.springframework.context.annotation.Lazy
                                       com.chinacreator.gzcm.engine.data.DataSourceService dataSourceService) {
        this.dsRepository = dsRepository;
        this.connectorFactory = connectorFactory;
        this.rowCountService = rowCountService;
        this.resourceSync = resourceSync;
        this.gitArchive = gitArchive;
        this.dataSourceService = dataSourceService;
    }

    @Override
    public String execute(TaskExecutionPlan executionPlan, ITaskStatusCallback statusCallback)
            throws TaskExecutionException {
        Map<String, Object> config = executionPlan.getContext();
        if (config == null && executionPlan.getSteps() != null && !executionPlan.getSteps().isEmpty()) {
            config = executionPlan.getSteps().get(0).getConfig();
        }
        if (config == null) {
            throw new TaskExecutionException("执行配置为空");
        }
        String datasourceId = String.valueOf(config.get("datasourceId"));
        long start = System.currentTimeMillis();

        cancelFlags.remove(executionPlan.getTaskId());
        pauseFlags.remove(executionPlan.getTaskId());

        try {
            // 步骤1：获取表清单（10% 预估，完成前上报 statusMessage）
            if (statusCallback != null) {
                statusCallback.onStepStart(executionPlan.getTaskId(), "listResources", "获取数据表清单");
                statusCallback.onProgressUpdate(executionPlan.getTaskId(), 1, "正在通过连接器获取数据表清单...");
            }
            final String dsId = datasourceId;
            DataSourceEntity ds = withRetry(() -> {
                try {
                    DataSourceEntity entity = dsRepository.findById(dsId);
                    if (entity == null) {
                        throw new TaskExecutionException("数据源不存在: " + dsId);
                    }
                    return entity;
                } catch (TaskExecutionException e) {
                    throw new UncheckedTE(e);
                }
            });

            MetadataStrategyConfig strategy = MetadataStrategyConfig.fromJson(ds.getMetadataConfig());
            String countMethod = strategy.getCountMethod() == null
                    ? MetadataStrategyConfig.COUNT_ESTIMATE : strategy.getCountMethod();
            boolean includeRowCount = Boolean.TRUE.equals(strategy.getIncludeRowCount());

            // 1) 表清单（带 1 次整任务级重试）
            // PMO-38 T2: 数据源业务类型 (POSTGRESQL 等) 归一化到 pipeline 规范类型 (JDBC),
            // 防御纵深 — ConnectorFactory.getConnector 内部也做归一化,
            // 此处显式先归一以让错误信息更友好。
            final String connectorType =
                    ConnectorFactory.knownType(ds.getDatasourceType()) != null
                            ? ConnectorFactory.knownType(ds.getDatasourceType())
                            : ds.getDatasourceType();
            List<com.chinacreator.gzcm.common.data.model.DataResource> resources = withRetry(
                    () -> {
                        com.chinacreator.gzcm.runtime.access.connector.Connector connector =
                                connectorFactory.getConnector(connectorType);
                        // PMO-49：connection_config 中密码已剥离，须取解密回填后的配置方能建连
                        String resolvedConfig = dataSourceService.getResolvedConnectionConfig(ds.getDatasourceId());
                        return connector.listResources(
                                resolvedConfig != null ? resolvedConfig : ds.getConnectionConfig(),
                                ds.getOrgId(), ds.getDatasourceName());
                    });
            if (resources == null) {
                throw new TaskExecutionException("Connector 返回空表清单");
            }

            // 步骤1 完成：10% → 进入步骤2（90% 分配给逐表采集）
            if (statusCallback != null) {
                statusCallback.onStepComplete(executionPlan.getTaskId(), "listResources", "获取数据表清单", true,
                        "成功获取 " + resources.size() + " 张表");
                statusCallback.onProgressUpdate(executionPlan.getTaskId(), 10, "获取表清单完成，开始逐表采集元数据与行数");
                statusCallback.onStepStart(executionPlan.getTaskId(), "collectTables", "逐表采集元数据");
            }

            // 2) 逐表落库 + 行数统计
            int ok = 0;
            int failed = 0;
            int processed = 0;
            int total = resources.size();
            List<String> failedTables = new ArrayList<>();

            for (com.chinacreator.gzcm.common.data.model.DataResource r : resources) {
                if (Boolean.TRUE.equals(cancelFlags.get(executionPlan.getTaskId()))) {
                    throw new TaskExecutionException("任务已取消");
                }
                while (Boolean.TRUE.equals(pauseFlags.get(executionPlan.getTaskId()))) {
                    sleepQuiet(500);
                    if (Boolean.TRUE.equals(cancelFlags.get(executionPlan.getTaskId()))) {
                        throw new TaskExecutionException("任务已取消");
                    }
                }
                String tableName = r.getResourceName() != null ? r.getResourceName() : "?";
                try {
                    Long rowCnt = includeRowCount
                            ? rowCountService.countTable(ds.getConnectionConfig(),
                                    r.getSourcePath() == null ? r.getResourceName() : r.getSourcePath(),
                                    countMethod)
                            : null;
                    resourceSync.syncResource(datasourceId, r, rowCnt);
                    ok++;
                } catch (Exception e) {
                    failed++;
                    failedTables.add(tableName);
                    log.warn("表 {} 元数据采集失败: {}", tableName, e.getMessage());
                }
                processed++;
                // 进度映射：10〜98%（逐表，留 2% 给归档 + 审计）
                if (statusCallback != null) {
                    int progress = 10 + (processed * 88) / Math.max(total, 1);
                    int done = ok + failed;
                    String msg = String.format("采集表 %d / %d（成功 %d / 失败 %d）— 当前: %s",
                            done, total, ok, failed, tableName);
                    statusCallback.onProgressUpdate(executionPlan.getTaskId(), progress, msg);
                }
            }
            if (statusCallback != null) {
                statusCallback.onStepComplete(executionPlan.getTaskId(), "collectTables", "逐表采集元数据", true,
                        "Table collection done: ok=" + ok + " failed=" + failed);
                statusCallback.onProgressUpdate(executionPlan.getTaskId(), 98, "归档到 Git 与声明审计");
            }

            // 3) 审计 + 采集时间
            String status = failed == 0 ? "SUCCEEDED" : (ok > 0 ? "SUCCEEDED_PARTIAL" : "FAILED");
            long elapsed = System.currentTimeMillis() - start;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("datasourceId", datasourceId);
            result.put("countMethod", countMethod);
            result.put("tablesTotal", resources.size());
            result.put("tablesOk", ok);
            result.put("tablesFailed", failed);
            if (!failedTables.isEmpty()) {
                result.put("failedTables", failedTables);
            }
            result.put("partial", failed > 0);
            result.put("elapsedMs", elapsed);

            // 孤儿行清理（仅全量成功时执行，避免失败时误删）
            if (failed == 0) {
                try {
                    java.util.Set<String> kept = new java.util.LinkedHashSet<>();
                    for (com.chinacreator.gzcm.common.data.model.DataResource r : resources) {
                        kept.add(r.getSourcePath() != null && !r.getSourcePath().isBlank()
                                ? r.getSourcePath() : r.getResourceName());
                    }
                    resourceSync.removeStale(datasourceId, kept);
                } catch (Exception e) {
                    log.warn("孤儿资源清理失败（不影响任务成功）: {}", e.getMessage());
                }
            }

            // Git 存档 + 版本差异对比（失败不影响采集任务成功判定）
            // 注意：必须先于 auditLog 执行 — result 携带 gitCommit/diffSummary 落库，
            // 供 collect-diff 端点与历史版本比较的"提交说明"列使用（原先顺序反了导致 gitCommit 恒不落库）
            try {
                Map<String, Object> gitResult = gitArchive.archiveAndDiff(datasourceId, resources);
                if (Boolean.TRUE.equals(gitResult.get("archived"))) {
                    result.put("diffSummary", gitResult.get("diffSummary"));
                    result.put("diffMarkdown", gitResult.get("diffMarkdown"));
                    result.put("gitCommit", gitResult.get("commitMessage"));
                }
            } catch (Exception e) {
                log.warn("Git 存档调用失败（不影响采集任务成功）: datasource={}, error={}", datasourceId, e.getMessage());
            }

            try {
                rowCountService.auditLog(datasourceId, countMethod, resources.size(), ok,
                        failed, String.join(",", failedTables), status,
                        mapper.writeValueAsString(result), executionPlan.getTaskId(), elapsed);
                dsRepository.updateLastCollectTime(datasourceId);
            } catch (Exception e) {
                log.warn("审计写入失败（不影响采集任务成功判定）: {}", e.getMessage());
            }

            log.info("METADATA_COLLECT 完成 datasource={} status={} tables={}/{} failed={} elapsed={}ms",
                    datasourceId, status, ok, resources.size(), failed, elapsed);
            return mapper.writeValueAsString(result);

        } catch (TaskExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new TaskExecutionException("元数据采集失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancel(String taskId) {
        cancelFlags.put(taskId, Boolean.TRUE);
        log.info("METADATA_COLLECT cancel requested: {}", taskId);
    }

    @Override
    public void pause(String taskId) {
        pauseFlags.put(taskId, Boolean.TRUE);
    }

    @Override
    public void resume(String taskId) {
        pauseFlags.remove(taskId);
    }

    @Override
    public TaskStatus getStatus(String taskId) {
        TaskStatus s = new TaskStatus();
        s.setTaskId(taskId);
        s.setStatus(Boolean.TRUE.equals(cancelFlags.get(taskId))
                ? TaskStatus.Status.CANCELLED : TaskStatus.Status.RUNNING);
        return s;
    }

    private <T> T withRetry(java.util.function.Supplier<T> action) throws TaskExecutionException {
        Exception first = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return action.get();
            } catch (UncheckedTE e) {
                Throwable c = e.getCause();
                first = c instanceof Exception ? (Exception) c : new Exception(c);
                if (attempt == 0) {
                    log.warn("元数据采集首次失败，5s 后重试: {}", e.getMessage());
                    sleepQuiet(5000);
                }
            } catch (Exception e) {
                first = e;
                if (attempt == 0) {
                    log.warn("元数据采集首次失败，5s 后重试: {}", e.getMessage());
                    sleepQuiet(5000);
                }
            }
        }
        if (first instanceof TaskExecutionException) {
            throw (TaskExecutionException) first;
        }
        throw new TaskExecutionException("执行失败（重试 1 次后仍异常）: " + first.getMessage(), first);
    }

    private void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** 把 checked TaskExecutionException 转成 unchecked，供 Supplier lambda 内抛出 */
    static final class UncheckedTE extends RuntimeException {
        UncheckedTE(Throwable cause) { super(cause); }
    }
}
