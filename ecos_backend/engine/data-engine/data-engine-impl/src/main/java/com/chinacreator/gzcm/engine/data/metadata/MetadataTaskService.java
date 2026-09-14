package com.chinacreator.gzcm.engine.data.metadata;

import com.chinacreator.gzcm.engine.data.repository.DataSourceRepository;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PMO-37 元数据采集提交服务 —— 数据源生命周期与任务引擎的唯一桥梁。
 * <p>
 * submitCollect():  submitTask → parseTask → executeTask（在调用线程执行，
 * 故注册处统一 @Async 包装），完成后缓存失效由调用方触发。
 * <p>
 * 无注册执行器时（异常降级）返回 null，调用方应回退同步直采并记 WARN。
 *
 * @author DataBridge Datanet Team
 */
@Service
public class MetadataTaskService {

    private static final Logger log = LoggerFactory.getLogger(MetadataTaskService.class);

    private final ITaskManagementService taskManagementService;
    private final DataSourceRepository dsRepository;

    public MetadataTaskService(ITaskManagementService taskManagementService,
                               DataSourceRepository dsRepository) {
        this.taskManagementService = taskManagementService;
        this.dsRepository = dsRepository;
    }

    /**
     * PMO-38 T4 (BUG-D1 修): 分开 submitUUID + 后台 execute 的入口。
     * <p>
     * 与 submitCollect 区别:任务引擎 submitTask 生成的 UUID
     * 在 parseTask 后立即返回给调用方, followup executeTask
     * 走线程池异步跑, 执行结果 JSON 存 TaskStatus.result (可被
     * /collect-status/{uuid} 轮询)。
     * <p>
     * 适用于需要"提交即拿到真实 taskId"的调用方 (例:
     * MetadataController.collectAsync / MetadataAsyncTrigger.submitAsync)。
     *
     * @param datasourceId 数据源 ID
     * @return submitTask 返回的 UUID; 抛异常时向上抛 RuntimeException
     */
    public String submitOnly(String datasourceId) {
        String dsName = "unknown";
        try {
            var ds = dsRepository.findById(datasourceId);
            if (ds != null) {
                dsName = ds.getDatasourceName();
            }
        } catch (Exception e) {
            log.warn("读取数据源名称失败: {}", e.getMessage());
        }

        TaskDescription desc = new TaskDescription();
        desc.setTaskName("元数据采集: " + dsName);
        desc.setTaskType(MetadataCollectTaskParser.TASK_TYPE);
        desc.setDescription("采集数据源 " + datasourceId + " 的表清单/行数/字段元数据");
        Map<String, Object> params = new HashMap<>();
        params.put("datasourceId", datasourceId);
        desc.setParameters(params);
        desc.setAsync(true);

        String taskId;
        try {
            taskId = taskManagementService.submitTask(desc);
        } catch (Exception e) {
            log.error("METADATA_COLLECT 任务提交失败 datasource={}: {}", datasourceId, e.getMessage(), e);
            throw new RuntimeException("元数据采集任务提交失败: " + e.getMessage(), e);
        }
        log.info("METADATA_COLLECT 任务已提交(仅 submitOnly): taskId={}, datasourceId={}", taskId, datasourceId);

        // parseTask 纯注册执行计划, 同步可完成
        try {
            taskManagementService.parseTask(taskId);
        } catch (Exception pe) {
            log.warn("parseTask 失败, 请检查任务类型注册: {} (taskId={})", pe.getMessage(), taskId);
            throw new RuntimeException("parseTask 失败: " + pe.getMessage(), pe);
        }

        // 后台 execute — executeTask 内部走 executor + statusCallback + 持久化
        final String tid = taskId;
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                taskManagementService.executeTask(tid);
            } catch (Exception ee) {
                log.warn("后台执行失败: taskId={} err={}", tid, ee.getMessage());
            }
        });

        return taskId;
    }

    /**
     * 提交并执行元数据采集任务。
     *
     * @param datasourceId 数据源 ID
     * @param sync         true=同步等待完成（原有接口语义保持不变）；false=提交后台执行
     * @return 完成时返回采集结果 JSON 字符串；sync=false 立即返回 null
     */
    public String submitCollect(String datasourceId, boolean sync) {
        String dsName = "unknown";
        try {
            var ds = dsRepository.findById(datasourceId);
            if (ds != null) {
                dsName = ds.getDatasourceName();
            }
        } catch (Exception e) {
            log.warn("读取数据源名称失败: {}", e.getMessage());
        }

        TaskDescription desc = new TaskDescription();
        desc.setTaskName("元数据采集: " + dsName);
        desc.setTaskType(MetadataCollectTaskParser.TASK_TYPE);
        desc.setDescription("采集数据源 " + datasourceId + " 的表清单/行数/字段元数据");
        Map<String, Object> params = new HashMap<>();
        params.put("datasourceId", datasourceId);
        desc.setParameters(params);
        desc.setAsync(!sync);

        try {
            String taskId = taskManagementService.submitTask(desc);
            log.info("METADATA_COLLECT 任务已提交: taskId={}, datasourceId={}", taskId, datasourceId);
            if (!sync) {
                return null;
            }
            // 同步语义（原有 POST /metadata/collect 行为不变）
            taskManagementService.parseTask(taskId);
            return taskManagementService.executeTask(taskId);
        } catch (Exception e) {
            log.error("METADATA_COLLECT 任务执行失败 datasource={}: {}", datasourceId, e.getMessage(), e);
            throw new RuntimeException("元数据采集任务执行失败: " + e.getMessage(), e);
        }
    }

    /** 查任务状态（含 result） */
    public TaskStatus queryStatus(String taskId) {
        try {
            return taskManagementService.getTaskStatus(taskId);
        } catch (Exception e) {
            log.warn("查询任务状态失败 {}: {}", taskId, e.getMessage());
            return null;
        }
    }

    /**
     * 同步执行（提交 + 解析 + 同步等待完成）— 给"立即采集"按钮用。
     *
     * <p>与 submitCollect 区别：
     * <ul>
     *   <li>submitCollect 在调用线程里同步走 executeTask，并默认走 ITaskExecutor；
     *       本方法显式标 setAsync(false) 让 TaskManagementService 不以异步 fire-and-forget
     *       方式运行，且执行过程 executor 内的 statusCallback 持续上报进度</li>
     *   <li>完成后直接返回 result JSON 字符串（进度相关字段已手写在 TaskStatus 里供前端轮询）</li>
     * </ul>
     *
     * @param datasourceId 数据源 ID
     * @return 采集结果 JSON 字符串；抛出异常时向上抛 {@link RuntimeException}
     */
    public String executeSync(String datasourceId) {
        String dsName = "unknown";
        try {
            var ds = dsRepository.findById(datasourceId);
            if (ds != null) {
                dsName = ds.getDatasourceName();
            }
        } catch (Exception e) {
            log.warn("读取数据源名称失败: {}", e.getMessage());
        }

        TaskDescription desc = new TaskDescription();
        desc.setTaskName("元数据采集(同步): " + dsName);
        desc.setTaskType(MetadataCollectTaskParser.TASK_TYPE);
        desc.setDescription("同步立即执行: 采集数据源 " + datasourceId + " 的表清单/行数/字段元数据");
        Map<String, Object> params = new HashMap<>();
        params.put("datasourceId", datasourceId);
        desc.setParameters(params);
        desc.setAsync(false);

        try {
            String taskId = taskManagementService.submitTask(desc);
            log.info("METADATA_COLLECT 同步任务已提交: taskId={}, datasourceId={}", taskId, datasourceId);
            taskManagementService.parseTask(taskId);
            String result = taskManagementService.executeTask(taskId);
            log.info("METADATA_COLLECT 同步任务完成: taskId={}", taskId);
            return result;
        } catch (Exception e) {
            log.error("METADATA_COLLECT 同步任务执行失败 datasource={}: {}", datasourceId, e.getMessage(), e);
            throw new RuntimeException("元数据采集任务执行失败: " + e.getMessage(), e);
        }
    }

    /** 查任务的 ITaskManagementService 引用（保留给需要跨服务的上下文） */
    public ITaskManagementService taskManagementService() {
        return taskManagementService;
    }
}
