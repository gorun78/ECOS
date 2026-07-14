package com.chinacreator.gzcm.runtime.core.task.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.runtime.core.task.callback.ITaskStatusCallback;
import com.chinacreator.gzcm.runtime.core.task.executor.ITaskExecutor;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.parser.ITaskParser;
import com.chinacreator.gzcm.runtime.core.task.persistence.ITaskPersistenceService;
import com.chinacreator.gzcm.runtime.core.task.service.ITaskManagementService;

@Service
public class TaskManagementServiceImpl implements ITaskManagementService {

    private static final Logger logger = LoggerFactory.getLogger(TaskManagementServiceImpl.class);

    private final Map<String, TaskDescription> tasks = new ConcurrentHashMap<>();
    private final Map<String, TaskExecutionPlan> plans = new ConcurrentHashMap<>();
    private final Map<String, TaskStatus> statuses = new ConcurrentHashMap<>();
    private final Map<String, ITaskParser> parsers = new HashMap<>();
    private final Map<String, ITaskExecutor> executors = new HashMap<>();

    private ITaskStatusCallback statusCallback;
    private ITaskPersistenceService persistenceService;

    @Override
    public String submitTask(TaskDescription taskDescription) throws TaskManagementException {
        String id = taskDescription.getTaskId();
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            taskDescription.setTaskId(id);
        }
        
        // 设置创建时间
        if (taskDescription.getCreateTime() == null) {
            taskDescription.setCreateTime(new Date());
        }
        
        tasks.put(id, taskDescription);
        
        // 初始化任务状态
        TaskStatus status = new TaskStatus();
        status.setTaskId(id);
        status.setStatus(TaskStatus.Status.PENDING);
        status.setProgress(0);
        status.setStartTime(new Date());
        status.setUpdateTime(new Date());
        statuses.put(id, status);
        
        // 持久化任务描述和状态
        if (persistenceService != null) {
            try {
                persistenceService.saveTask(taskDescription);
                persistenceService.saveStatus(status);
            } catch (Exception e) {
                logger.warn("持久化任务失败: taskId={}", id, e);
                // 不抛出异常，允许任务继续执行
            }
        }
        
        logger.info("任务已提交: taskId={}, taskName={}", id, taskDescription.getTaskName());
        return id;
    }

    @Override
    public TaskExecutionPlan parseTask(String taskId) throws TaskManagementException {
        TaskDescription desc = tasks.get(taskId);
        if (desc == null) {
            // 尝试从持久化服务加载
            if (persistenceService != null) {
                try {
                    desc = persistenceService.getTask(taskId);
                    if (desc != null) {
                        tasks.put(taskId, desc);
                    }
                } catch (Exception e) {
                    logger.warn("从持久化服务加载任务失败: taskId={}", taskId, e);
                }
            }
            if (desc == null) {
                throw new TaskManagementException("task not found: " + taskId);
            }
        }
        
        // 查找支持该任务类型的解析器
        ITaskParser parser = findParser(desc.getTaskType());
        if (parser == null) {
            throw new TaskManagementException("No parser found for task type: " + desc.getTaskType());
        }
        
        // 使用解析器解析任务
        TaskExecutionPlan plan;
        try {
            parser.validate(desc);
            plan = parser.parse(desc);
        } catch (ITaskParser.TaskParseException e) {
            throw new TaskManagementException("Task parse failed: " + e.getMessage(), e);
        }
        
        plans.put(taskId, plan);
        
        // 更新任务状态
        TaskStatus status = statuses.get(taskId);
        if (status == null) {
            status = new TaskStatus();
            status.setTaskId(taskId);
        }
        status.setStatus(TaskStatus.Status.PARSED);
        status.setUpdateTime(new Date());
        statuses.put(taskId, status);
        
        // 持久化执行计划和状态
        if (persistenceService != null) {
            try {
                persistenceService.savePlan(plan);
                persistenceService.saveStatus(status);
            } catch (Exception e) {
                logger.warn("持久化执行计划失败: taskId={}", taskId, e);
            }
        }
        
        logger.info("任务已解析: taskId={}", taskId);
        return plan;
    }

    @Override
    public String executeTask(String taskId) throws TaskManagementException {
        // 获取执行计划
        TaskExecutionPlan plan = plans.get(taskId);
        if (plan == null) {
            // 尝试从持久化服务加载
            if (persistenceService != null) {
                try {
                    plan = persistenceService.getPlan(taskId);
                    if (plan != null) {
                        plans.put(taskId, plan);
                    }
                } catch (Exception e) {
                    logger.warn("从持久化服务加载执行计划失败: taskId={}", taskId, e);
                }
            }
            if (plan == null) {
                // 如果计划不存在，先解析
                plan = parseTask(taskId);
            }
        }
        
        // 获取任务状态
        TaskStatus status = statuses.get(taskId);
        if (status == null) {
            status = new TaskStatus();
            status.setTaskId(taskId);
        }
        
        // 更新状态为运行中
        status.setStatus(TaskStatus.Status.RUNNING);
        status.setStartTime(new Date());
        status.setUpdateTime(new Date());
        statuses.put(taskId, status);
        
        if (statusCallback != null) {
            statusCallback.onStatusUpdate(status);
        }
        
        if (persistenceService != null) {
            try {
                persistenceService.saveStatus(status);
            } catch (Exception e) {
                logger.warn("持久化任务状态失败: taskId={}", taskId, e);
            }
        }
        
        // 查找执行器
        ITaskExecutor executor = findExecutor(plan);
        if (executor == null) {
            throw new TaskManagementException("No executor found for task: " + taskId);
        }
        
        // 执行任务
        String result;
        try {
            result = executor.execute(plan, statusCallback);
            
            // 更新状态为成功
            status.setStatus(TaskStatus.Status.SUCCEEDED);
            status.setEndTime(new Date());
            status.setProgress(100);
            status.setResult(result);
            status.setUpdateTime(new Date());
            
        } catch (ITaskExecutor.TaskExecutionException e) {
            // 更新状态为失败
            status.setStatus(TaskStatus.Status.FAILED);
            status.setEndTime(new Date());
            status.setErrorMessage(e.getMessage());
            status.setErrorStack(getStackTrace(e));
            status.setUpdateTime(new Date());
            
            if (statusCallback != null) {
                statusCallback.onError(taskId, e.getMessage(), getStackTrace(e));
            }
            
            throw new TaskManagementException("Task execution failed: " + e.getMessage(), e);
        }
        
        statuses.put(taskId, status);
        
        if (statusCallback != null) {
            statusCallback.onStatusUpdate(status);
        }
        
        if (persistenceService != null) {
            try {
                persistenceService.saveStatus(status);
                // 记录执行记录（如果支持）
                if (persistenceService instanceof com.chinacreator.gzcm.runtime.core.task.persistence.impl.DatabaseTaskPersistenceServiceImpl) {
                    recordTaskExecution(taskId, status, result);
                }
            } catch (Exception e) {
                logger.warn("持久化任务状态失败: taskId={}", taskId, e);
            }
        }
        
        logger.info("任务执行完成: taskId={}, status={}", taskId, status.getStatus());
        return result;
    }
    
    /**
     * 查找支持指定任务类型的解析器
     */
    private ITaskParser findParser(String taskType) {
        if (taskType == null) {
            return null;
        }
        
        // 直接查找
        ITaskParser parser = parsers.get(taskType);
        if (parser != null && parser.supports(taskType)) {
            return parser;
        }
        
        // 遍历所有解析器查找支持的
        for (ITaskParser p : parsers.values()) {
            if (p.supports(taskType)) {
                return p;
            }
        }
        
        return null;
    }
    
    /**
     * 查找执行器
     */
    private ITaskExecutor findExecutor(TaskExecutionPlan plan) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            return null;
        }
        
        // 从执行计划的第一个步骤中获取执行器类型
        TaskExecutionPlan.ExecutionStep step = plan.getSteps().get(0);
        String executorType = step.getExecutor();
        
        if (executorType != null) {
            ITaskExecutor executor = executors.get(executorType);
            if (executor != null) {
                return executor;
            }
        }
        
        // 如果没有找到，尝试使用步骤类型
        String stepType = step.getStepType();
        if (stepType != null) {
            ITaskExecutor executor = executors.get(stepType);
            if (executor != null) {
                return executor;
            }
        }
        
        return null;
    }
    
    /**
     * 获取异常堆栈信息
     */
    private String getStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    @Override
    public String submitAndExecute(TaskDescription taskDescription) throws TaskManagementException {
        String id = submitTask(taskDescription);
        parseTask(id);
        return executeTask(id);
    }

    @Override
    public TaskStatus getTaskStatus(String taskId) throws TaskManagementException {
        // 先从内存缓存读取
        TaskStatus status = statuses.get(taskId);
        if (status != null) {
            return status;
        }
        
        // 如果内存中没有，从持久化服务加载
        if (persistenceService != null) {
            try {
                status = persistenceService.getStatus(taskId);
                if (status != null) {
                    // 加载到内存缓存
                    statuses.put(taskId, status);
                    return status;
                }
            } catch (Exception e) {
                logger.warn("从持久化服务加载任务状态失败: taskId={}", taskId, e);
            }
        }
        
        return null;
    }

    @Override
    public void cancelTask(String taskId) throws TaskManagementException {
        setStatus(taskId, TaskStatus.Status.CANCELLED);
    }

    @Override
    public void pauseTask(String taskId) throws TaskManagementException {
        setStatus(taskId, TaskStatus.Status.PAUSED);
    }

    @Override
    public void resumeTask(String taskId) throws TaskManagementException {
        setStatus(taskId, TaskStatus.Status.RUNNING);
    }

    @Override
    public List<TaskDescription> queryTasks(Map<String, Object> condition, int offset, int limit)
            throws TaskManagementException {
        if (persistenceService != null) {
            try {
                Map<String, Object> taskCondition = removeStatusCondition(condition);
                List<TaskDescription> persisted = persistenceService.queryTasks(taskCondition, 0, Integer.MAX_VALUE);
                if (persisted != null) {
                    List<TaskDescription> filtered = applyTaskFilters(persisted, condition, true);
                    for (TaskDescription task : filtered) {
                        tasks.put(task.getTaskId(), task);
                    }
                    return paginate(filtered, offset, limit);
                }
            } catch (Exception e) {
                logger.warn("从持久化服务查询任务失败", e);
            }
        }

        List<TaskDescription> result = applyTaskFilters(new ArrayList<>(tasks.values()), condition, false);
        return paginate(result, offset, limit);
    }

    private Map<String, Object> removeStatusCondition(Map<String, Object> condition) {
        if (condition == null || condition.isEmpty()) {
            return condition;
        }
        Map<String, Object> result = new HashMap<>(condition);
        result.remove("status");
        return result;
    }

    private List<TaskDescription> applyTaskFilters(List<TaskDescription> source, Map<String, Object> condition,
            boolean usePersistenceStatusLookup) {
        List<TaskDescription> result = source;
        if (condition != null && !condition.isEmpty()) {
            result = result.stream()
                    .filter(task -> matchesTaskCondition(task, condition, usePersistenceStatusLookup))
                    .sorted((a, b) -> {
                        Date ca = a.getCreateTime();
                        Date cb = b.getCreateTime();
                        if (ca == null && cb == null) {
                            return 0;
                        }
                        if (ca == null) {
                            return 1;
                        }
                        if (cb == null) {
                            return -1;
                        }
                        return cb.compareTo(ca);
                    })
                    .collect(java.util.stream.Collectors.toList());
        }
        return result;
    }

    private boolean matchesTaskCondition(TaskDescription task, Map<String, Object> condition,
            boolean usePersistenceStatusLookup) {
        if (condition.containsKey("taskType")
                && (task.getTaskType() == null || !task.getTaskType().equals(condition.get("taskType")))) {
            return false;
        }
        if (condition.containsKey("status")) {
            TaskStatus status = resolveTaskStatus(task.getTaskId(), usePersistenceStatusLookup);
            if (status == null || status.getStatus() == null
                    || !status.getStatus().name().equals(String.valueOf(condition.get("status")))) {
                return false;
            }
        }
        if (condition.containsKey("tenantId")
                && (task.getTenantId() == null || !task.getTenantId().equals(condition.get("tenantId")))) {
            return false;
        }
        if (condition.containsKey("createdBy")
                && (task.getCreatedBy() == null || !task.getCreatedBy().equals(condition.get("createdBy")))) {
            return false;
        }
        return true;
    }

    private TaskStatus resolveTaskStatus(String taskId, boolean usePersistenceLookup) {
        TaskStatus status = statuses.get(taskId);
        if (status != null || !usePersistenceLookup || persistenceService == null) {
            return status;
        }
        try {
            status = persistenceService.getStatus(taskId);
            if (status != null) {
                statuses.put(taskId, status);
            }
            return status;
        } catch (Exception e) {
            logger.warn("浠庢寔涔呭寲鏈嶅姟鍔犺浇浠诲姟鐘舵€佸け璐? taskId={}", taskId, e);
            return null;
        }
    }

    private <T> List<T> paginate(List<T> source, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? Integer.MAX_VALUE : limit;
        int fromIndex = Math.min(safeOffset, source.size());
        int toIndex = Math.min(fromIndex + safeLimit, source.size());
        return new ArrayList<>(source.subList(fromIndex, toIndex));
    }

    @Override
    public Map<String, Object> getTaskStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        int running = 0, pending = 0, succeeded = 0, failed = 0, cancelled = 0;
        
        for (TaskStatus s : statuses.values()) {
            if (s.getStatus() == null) continue;
            switch (s.getStatus()) {
                case RUNNING: running++; break;
                case PENDING: pending++; break;
                case SUCCEEDED: succeeded++; break;
                case FAILED: failed++; break;
                case CANCELLED: cancelled++; break;
                default: break;
            }
        }
        
        stats.put("total", statuses.size());
        stats.put("running", running);
        stats.put("pending", pending);
        stats.put("succeeded", succeeded);
        stats.put("failed", failed);
        stats.put("cancelled", cancelled);
        return stats;
    }

    @Override
    public TaskDescription getTaskDescription(String taskId) throws TaskManagementException {
        // 先从内存缓存读取
        TaskDescription task = tasks.get(taskId);
        if (task != null) {
            return task;
        }
        
        // 如果内存中没有，从持久化服务加载
        if (persistenceService != null) {
            try {
                task = persistenceService.getTask(taskId);
                if (task != null) {
                    // 加载到内存缓存
                    tasks.put(taskId, task);
                    return task;
                }
            } catch (Exception e) {
                logger.warn("从持久化服务加载任务描述失败: taskId={}", taskId, e);
            }
        }
        
        return null;
    }

    @Override
    public TaskExecutionPlan getTaskExecutionPlan(String taskId) throws TaskManagementException {
        // 先从内存缓存读取
        TaskExecutionPlan plan = plans.get(taskId);
        if (plan != null) {
            return plan;
        }
        
        // 如果内存中没有，从持久化服务加载
        if (persistenceService != null) {
            try {
                plan = persistenceService.getPlan(taskId);
                if (plan != null) {
                    // 加载到内存缓存
                    plans.put(taskId, plan);
                    return plan;
                }
            } catch (Exception e) {
                logger.warn("从持久化服务加载执行计划失败: taskId={}", taskId, e);
            }
        }
        
        return null;
    }

    @Override
    public void registerParser(String taskType, ITaskParser parser) {
        parsers.put(taskType, parser);
    }

    @Override
    public void registerExecutor(String executorType, ITaskExecutor executor) {
        executors.put(executorType, executor);
    }

    public void setStatusCallback(ITaskStatusCallback callback) {
        this.statusCallback = callback;
    }
    
    public void setPersistenceService(ITaskPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    private void setStatus(String taskId, TaskStatus.Status s) {
        TaskStatus status = statuses.get(taskId);
        if (status == null) {
            // 尝试从持久化服务加载
            if (persistenceService != null) {
                try {
                    status = persistenceService.getStatus(taskId);
                } catch (Exception e) {
                    logger.warn("从持久化服务加载任务状态失败: taskId={}", taskId, e);
                }
            }
            
            if (status == null) {
                status = new TaskStatus();
                status.setTaskId(taskId);
            }
        }
        
        status.setStatus(s);
        status.setUpdateTime(new Date());
        statuses.put(taskId, status);
        
        // 持久化状态
        if (persistenceService != null) {
            try {
                persistenceService.saveStatus(status);
            } catch (Exception e) {
                logger.warn("持久化任务状态失败: taskId={}", taskId, e);
            }
        }
    }
    
    /**
     * 记录任务执行记录
     */
    private void recordTaskExecution(String taskId, TaskStatus status, String result) {
        try {
            // 使用 SystemDatabaseAccess 记录执行记录
            com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess databaseAccess = 
                new com.chinacreator.gzcm.runtime.core.database.impl.SystemDatabaseAccessImpl();
            
            String executionId = UUID.randomUUID().toString();
            String sql = "INSERT INTO TD_RUNTIME_TASK_EXECUTION (" +
                "EXECUTION_ID, TASK_ID, STATUS, START_TIME, END_TIME, DURATION, " +
                "RESULT, ERROR_MESSAGE, ERROR_STACK, EXECUTION_NODE_ID, CREATED_TIME" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
            
            long duration = 0;
            if (status.getStartTime() != null && status.getEndTime() != null) {
                duration = status.getEndTime().getTime() - status.getStartTime().getTime();
            }
            
            String executionNodeId = null;
            if (status.getMetrics() != null && status.getMetrics().containsKey("executionNodeId")) {
                executionNodeId = (String) status.getMetrics().get("executionNodeId");
            }
            
            databaseAccess.executeUpdate(sql,
                executionId,
                taskId,
                status.getStatus() != null ? status.getStatus().name() : null,
                status.getStartTime(),
                status.getEndTime(),
                duration,
                result,
                status.getErrorMessage(),
                status.getErrorStack(),
                executionNodeId
            );
            
            logger.debug("任务执行记录已保存: executionId={}, taskId={}", executionId, taskId);
        } catch (Exception e) {
            logger.warn("记录任务执行记录失败: taskId={}", taskId, e);
        }
    }
}

