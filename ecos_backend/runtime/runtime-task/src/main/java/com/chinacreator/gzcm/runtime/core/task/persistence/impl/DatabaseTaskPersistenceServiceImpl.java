package com.chinacreator.gzcm.runtime.core.task.persistence.impl;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.runtime.core.database.ISystemDatabaseAccess;
import com.chinacreator.gzcm.runtime.core.task.model.TaskDescription;
import com.chinacreator.gzcm.runtime.core.task.model.TaskExecutionPlan;
import com.chinacreator.gzcm.runtime.core.task.model.TaskStatus;
import com.chinacreator.gzcm.runtime.core.task.persistence.ITaskPersistenceService;
import com.chinacreator.gzcm.runtime.core.task.persistence.TaskPersistenceException;
import com.chinacreator.gzcm.runtime.core.task.persistence.entity.TaskEntity;
import com.chinacreator.gzcm.runtime.core.task.persistence.entity.TaskPlanEntity;
import com.chinacreator.gzcm.runtime.core.task.persistence.entity.TaskStatusEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 鏁版嵁搴撲换鍔℃寔涔呭寲鏈嶅姟瀹炵幇
 * 浣跨敤 ISystemDatabaseAccess 璁块棶鏁版嵁搴?
 * 
 * @author CDRC Runtime Team
 */
public class DatabaseTaskPersistenceServiceImpl implements ITaskPersistenceService {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseTaskPersistenceServiceImpl.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final String TASK_TABLE = "TD_RUNTIME_TASK";
    private static final String PLAN_TABLE = "TD_RUNTIME_TASK_PLAN";
    private static final String STATUS_TABLE = "TD_RUNTIME_TASK_STATUS";
    
    private final ISystemDatabaseAccess databaseAccess;
    
    public DatabaseTaskPersistenceServiceImpl(ISystemDatabaseAccess databaseAccess) {
        this.databaseAccess = databaseAccess;
    }
    
    @Override
    public void saveTask(TaskDescription task) throws TaskPersistenceException {
        if (task == null) {
            throw new TaskPersistenceException("TaskDescription cannot be null");
        }
        if (task.getTaskId() == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        
        try {
            TaskEntity entity = convertToTaskEntity(task);
            Map<String, Object> condition = new HashMap<>();
            condition.put("taskId", task.getTaskId());
            
            // 妫€鏌ユ槸鍚﹀瓨鍦?
            TaskEntity existing = databaseAccess.findOne(TASK_TABLE, TaskEntity.class, condition);
            if (existing != null) {
                // 鏇存柊
                databaseAccess.update(TASK_TABLE, entity);
            } else {
                // 鎻掑叆
                databaseAccess.insert(TASK_TABLE, entity);
            }
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("淇濆瓨浠诲姟澶辫触: taskId={}", task.getTaskId(), e);
            throw new TaskPersistenceException("淇濆瓨浠诲姟澶辫触: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("淇濆瓨浠诲姟澶辫触: taskId={}", task.getTaskId(), e);
            throw new TaskPersistenceException("淇濆瓨浠诲姟澶辫触: " + e.getMessage(), e);
        }
    }
    
    @Override
    public TaskDescription getTask(String taskId) throws TaskPersistenceException {
        if (taskId == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("taskId", taskId);
            TaskEntity entity = databaseAccess.findOne(TASK_TABLE, TaskEntity.class, condition);
            if (entity == null) {
                return null;
            }
            return convertToTaskDescription(entity);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("鑾峰彇浠诲姟澶辫触: taskId={}", taskId, e);
            throw new TaskPersistenceException("鑾峰彇浠诲姟澶辫触: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("鑾峰彇浠诲姟澶辫触: taskId={}", taskId, e);
            throw new TaskPersistenceException("鑾峰彇浠诲姟澶辫触: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void savePlan(TaskExecutionPlan plan) throws TaskPersistenceException {
        if (plan == null) {
            throw new TaskPersistenceException("TaskExecutionPlan cannot be null");
        }
        if (plan.getTaskId() == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        
        try {
            TaskPlanEntity entity = convertToTaskPlanEntity(plan);
            Map<String, Object> condition = new HashMap<>();
            condition.put("taskId", plan.getTaskId());
            
            // 妫€鏌ユ槸鍚﹀瓨鍦?
            TaskPlanEntity existing = databaseAccess.findOne(PLAN_TABLE, TaskPlanEntity.class, condition);
            if (existing != null) {
                // 鏇存柊
                databaseAccess.update(PLAN_TABLE, entity);
            } else {
                // 鎻掑叆
                databaseAccess.insert(PLAN_TABLE, entity);
            }
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("淇濆瓨鎵ц璁″垝澶辫触: taskId={}", plan.getTaskId(), e);
            throw new TaskPersistenceException("淇濆瓨鎵ц璁″垝澶辫触: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("淇濆瓨鎵ц璁″垝澶辫触: taskId={}", plan.getTaskId(), e);
            throw new TaskPersistenceException("淇濆瓨鎵ц璁″垝澶辫触: " + e.getMessage(), e);
        }
    }
    
    @Override
    public TaskExecutionPlan getPlan(String taskId) throws TaskPersistenceException {
        if (taskId == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("taskId", taskId);
            TaskPlanEntity entity = databaseAccess.findOne(PLAN_TABLE, TaskPlanEntity.class, condition);
            if (entity == null) {
                return null;
            }
            return convertToTaskExecutionPlan(entity);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("鑾峰彇鎵ц璁″垝澶辫触: taskId={}", taskId, e);
            throw new TaskPersistenceException("鑾峰彇鎵ц璁″垝澶辫触: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("鑾峰彇鎵ц璁″垝澶辫触: taskId={}", taskId, e);
            throw new TaskPersistenceException("鑾峰彇鎵ц璁″垝澶辫触: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void saveStatus(TaskStatus status) throws TaskPersistenceException {
        if (status == null) {
            throw new TaskPersistenceException("TaskStatus cannot be null");
        }
        if (status.getTaskId() == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        
        try {
            TaskStatusEntity entity = convertToTaskStatusEntity(status);
            Map<String, Object> condition = new HashMap<>();
            condition.put("taskId", status.getTaskId());
            
            // 妫€鏌ユ槸鍚﹀瓨鍦?
            TaskStatusEntity existing = databaseAccess.findOne(STATUS_TABLE, TaskStatusEntity.class, condition);
            if (existing != null) {
                // 鏇存柊
                databaseAccess.update(STATUS_TABLE, entity);
            } else {
                // 鎻掑叆
                databaseAccess.insert(STATUS_TABLE, entity);
            }
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("淇濆瓨浠诲姟鐘舵€佸け璐? taskId={}", status.getTaskId(), e);
            throw new TaskPersistenceException("淇濆瓨浠诲姟鐘舵€佸け璐? " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("淇濆瓨浠诲姟鐘舵€佸け璐? taskId={}", status.getTaskId(), e);
            throw new TaskPersistenceException("淇濆瓨浠诲姟鐘舵€佸け璐? " + e.getMessage(), e);
        }
    }
    
    @Override
    public TaskStatus getStatus(String taskId) throws TaskPersistenceException {
        if (taskId == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        
        try {
            Map<String, Object> condition = new HashMap<>();
            condition.put("taskId", taskId);
            TaskStatusEntity entity = databaseAccess.findOne(STATUS_TABLE, TaskStatusEntity.class, condition);
            if (entity == null) {
                return null;
            }
            return convertToTaskStatus(entity);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("鑾峰彇浠诲姟鐘舵€佸け璐? taskId={}", taskId, e);
            throw new TaskPersistenceException("鑾峰彇浠诲姟鐘舵€佸け璐? " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("鑾峰彇浠诲姟鐘舵€佸け璐? taskId={}", taskId, e);
            throw new TaskPersistenceException("鑾峰彇浠诲姟鐘舵€佸け璐? " + e.getMessage(), e);
        }
    }
    
    @Override
    public void deleteTask(String taskId) throws TaskPersistenceException {
        if (taskId == null) {
            throw new TaskPersistenceException("Task ID cannot be null");
        }
        
        try {
            // 鍒犻櫎浠诲姟锛堢骇鑱斿垹闄よ鍒掑拰鐘舵€侊級
            databaseAccess.delete(TASK_TABLE, taskId);
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("鍒犻櫎浠诲姟澶辫触: taskId={}", taskId, e);
            throw new TaskPersistenceException("鍒犻櫎浠诲姟澶辫触: " + e.getMessage(), e);
        }
    }
    
    // ========== 杞崲鏂规硶 ==========

    @Override
    public List<TaskDescription> queryTasks(Map<String, Object> condition, int offset, int limit)
            throws TaskPersistenceException {
        try {
            Map<String, Object> dbCondition = new HashMap<>();
            if (condition != null) {
                if (condition.get("taskId") != null) {
                    dbCondition.put("taskId", condition.get("taskId"));
                }
                if (condition.get("taskType") != null) {
                    dbCondition.put("taskType", condition.get("taskType"));
                }
                if (condition.get("tenantId") != null) {
                    dbCondition.put("tenantId", condition.get("tenantId"));
                }
                if (condition.get("createdBy") != null) {
                    dbCondition.put("createdBy", condition.get("createdBy"));
                }
            }

            int safeOffset = Math.max(0, offset);
            int safeLimit = limit <= 0 ? Integer.MAX_VALUE : limit;
            List<TaskEntity> entities = databaseAccess.query(TASK_TABLE, TaskEntity.class, dbCondition, safeOffset, safeLimit);
            List<TaskDescription> tasks = new ArrayList<>();
            for (TaskEntity entity : entities) {
                TaskDescription task = convertToTaskDescription(entity);
                if (task != null) {
                    tasks.add(task);
                }
            }
            tasks.sort(Comparator.comparing(TaskDescription::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
            return tasks;
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("查询任务失败", e);
            throw new TaskPersistenceException("查询任务失败: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("查询任务失败", e);
            throw new TaskPersistenceException("查询任务失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TaskStatus> queryTaskStatuses(Map<String, Object> condition, int offset, int limit)
            throws TaskPersistenceException {
        try {
            Map<String, Object> dbCondition = new HashMap<>();
            if (condition != null) {
                if (condition.get("taskId") != null) {
                    dbCondition.put("taskId", condition.get("taskId"));
                }
                if (condition.get("status") != null) {
                    dbCondition.put("status", String.valueOf(condition.get("status")));
                }
            }

            int safeOffset = Math.max(0, offset);
            int safeLimit = limit <= 0 ? Integer.MAX_VALUE : limit;
            List<TaskStatusEntity> entities = databaseAccess.query(STATUS_TABLE, TaskStatusEntity.class, dbCondition, safeOffset, safeLimit);
            List<TaskStatus> statuses = new ArrayList<>();
            for (TaskStatusEntity entity : entities) {
                TaskStatus status = convertToTaskStatus(entity);
                if (status != null) {
                    statuses.add(status);
                }
            }
            statuses.sort(Comparator.comparing(TaskStatus::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder())));
            return statuses;
        } catch (ISystemDatabaseAccess.DatabaseAccessException e) {
            logger.error("查询任务状态失败", e);
            throw new TaskPersistenceException("查询任务状态失败: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("查询任务状态失败", e);
            throw new TaskPersistenceException("查询任务状态失败: " + e.getMessage(), e);
        }
    }
    private TaskEntity convertToTaskEntity(TaskDescription task) throws Exception {
        TaskEntity entity = new TaskEntity();
        entity.setTaskId(task.getTaskId());
        entity.setTaskName(task.getTaskName());
        entity.setTaskType(task.getTaskType());
        entity.setDescription(task.getDescription());
        entity.setTaskConfig(task.getTaskConfig());
        
        // 搴忓垪鍖?parameters
        if (task.getParameters() != null) {
            entity.setParameters(objectMapper.writeValueAsString(task.getParameters()));
        }
        
        entity.setPriority(task.getPriority());
        entity.setTimeout(task.getTimeout());
        entity.setRetryCount(task.getRetryCount());
        entity.setAsyncFlag(task.getAsync() ? "1" : "0");
        
        // 搴忓垪鍖?dependencies
        if (task.getDependencies() != null) {
            entity.setDependencies(objectMapper.writeValueAsString(task.getDependencies()));
        }
        
        // 浠?extensions 涓彁鍙?scheduleId, nodeId, executionMode
        if (task.getExtensions() != null) {
            entity.setScheduleId((String) task.getExtensions().get("scheduleId"));
            entity.setNodeId((String) task.getExtensions().get("nodeId"));
            entity.setExecutionMode((String) task.getExtensions().get("executionMode"));
        }
        
        // 浠?parameters 涓彁鍙?executionMode 鍜?nodeId锛堝鏋?extensions 涓病鏈夛級
        if (task.getParameters() != null) {
            if (entity.getExecutionMode() == null) {
                entity.setExecutionMode((String) task.getParameters().get("executionMode"));
            }
            if (entity.getNodeId() == null) {
                entity.setNodeId((String) task.getParameters().get("targetNodeId"));
            }
        }
        
        entity.setCreatedBy(task.getCreatedBy());
        entity.setTenantId(task.getTenantId());
        
        // 搴忓垪鍖?tags
        if (task.getTags() != null) {
            entity.setTags(objectMapper.writeValueAsString(task.getTags()));
        }
        
        // 搴忓垪鍖?extensions
        if (task.getExtensions() != null) {
            entity.setExtensions(objectMapper.writeValueAsString(task.getExtensions()));
        }
        
        if (task.getCreateTime() != null) {
            entity.setCreatedTime(new Timestamp(task.getCreateTime().getTime()));
        }
        entity.setUpdatedTime(new Timestamp(System.currentTimeMillis()));
        
        return entity;
    }
    
    private TaskDescription convertToTaskDescription(TaskEntity entity) throws Exception {
        TaskDescription task = new TaskDescription();
        task.setTaskId(entity.getTaskId());
        task.setTaskName(entity.getTaskName());
        task.setTaskType(entity.getTaskType());
        task.setDescription(entity.getDescription());
        task.setTaskConfig(entity.getTaskConfig());
        
        // 鍙嶅簭鍒楀寲 parameters
        if (entity.getParameters() != null && !entity.getParameters().trim().isEmpty()) {
            Map<String, Object> params = objectMapper.readValue(
                entity.getParameters(), 
                new TypeReference<Map<String, Object>>() {}
            );
            task.setParameters(params);
        }
        
        task.setPriority(entity.getPriority());
        task.setTimeout(entity.getTimeout());
        task.setRetryCount(entity.getRetryCount());
        task.setAsync("1".equals(entity.getAsyncFlag()));
        
        // 鍙嶅簭鍒楀寲 dependencies
        if (entity.getDependencies() != null && !entity.getDependencies().trim().isEmpty()) {
            List<String> deps = objectMapper.readValue(
                entity.getDependencies(),
                new TypeReference<List<String>>() {}
            );
            task.setDependencies(deps);
        }
        
        // 璁剧疆 extensions锛堝寘鍚?scheduleId, nodeId, executionMode锛?
        Map<String, Object> extensions = new HashMap<>();
        if (entity.getScheduleId() != null) {
            extensions.put("scheduleId", entity.getScheduleId());
        }
        if (entity.getNodeId() != null) {
            extensions.put("nodeId", entity.getNodeId());
        }
        if (entity.getExecutionMode() != null) {
            extensions.put("executionMode", entity.getExecutionMode());
        }
        
        // 鍙嶅簭鍒楀寲 extensions
        if (entity.getExtensions() != null && !entity.getExtensions().trim().isEmpty()) {
            Map<String, Object> ext = objectMapper.readValue(
                entity.getExtensions(),
                new TypeReference<Map<String, Object>>() {}
            );
            extensions.putAll(ext);
        }
        if (!extensions.isEmpty()) {
            task.setExtensions(extensions);
        }
        
        task.setCreatedBy(entity.getCreatedBy());
        task.setTenantId(entity.getTenantId());
        
        // 鍙嶅簭鍒楀寲 tags
        if (entity.getTags() != null && !entity.getTags().trim().isEmpty()) {
            List<String> tags = objectMapper.readValue(
                entity.getTags(),
                new TypeReference<List<String>>() {}
            );
            task.setTags(tags);
        }
        
        if (entity.getCreatedTime() != null) {
            task.setCreateTime(new Date(entity.getCreatedTime().getTime()));
        }
        
        return task;
    }
    
    private TaskPlanEntity convertToTaskPlanEntity(TaskExecutionPlan plan) throws Exception {
        TaskPlanEntity entity = new TaskPlanEntity();
        entity.setTaskId(plan.getTaskId());
        
        // 搴忓垪鍖栨墽琛岃鍒?
        entity.setPlanContent(objectMapper.writeValueAsString(plan));
        
        // 浠?metadata 涓彁鍙?executionMode 鍜?targetNodeId
        if (plan.getMetadata() != null) {
            entity.setExecutionMode((String) plan.getMetadata().get("executionMode"));
            entity.setTargetNodeId((String) plan.getMetadata().get("targetNodeId"));
        }
        
        entity.setCreatedTime(new Timestamp(System.currentTimeMillis()));
        entity.setUpdatedTime(new Timestamp(System.currentTimeMillis()));
        
        return entity;
    }
    
    private TaskExecutionPlan convertToTaskExecutionPlan(TaskPlanEntity entity) throws Exception {
        if (entity.getPlanContent() == null || entity.getPlanContent().trim().isEmpty()) {
            return null;
        }
        
        TaskExecutionPlan plan = objectMapper.readValue(entity.getPlanContent(), TaskExecutionPlan.class);
        
        // 璁剧疆 metadata锛堝寘鍚?executionMode 鍜?targetNodeId锛?
        if (plan.getMetadata() == null) {
            plan.setMetadata(new HashMap<>());
        }
        if (entity.getExecutionMode() != null) {
            plan.getMetadata().put("executionMode", entity.getExecutionMode());
        }
        if (entity.getTargetNodeId() != null) {
            plan.getMetadata().put("targetNodeId", entity.getTargetNodeId());
        }
        
        return plan;
    }
    
    private TaskStatusEntity convertToTaskStatusEntity(TaskStatus status) throws Exception {
        TaskStatusEntity entity = new TaskStatusEntity();
        entity.setTaskId(status.getTaskId());
        entity.setStatus(status.getStatus() != null ? status.getStatus().name() : null);
        entity.setStatusMessage(status.getStatusMessage());
        entity.setProgress(status.getProgress());
        entity.setCurrentStepId(status.getCurrentStepId());
        
        if (status.getStartTime() != null) {
            entity.setStartTime(new Timestamp(status.getStartTime().getTime()));
        }
        if (status.getEndTime() != null) {
            entity.setEndTime(new Timestamp(status.getEndTime().getTime()));
        }
        
        entity.setEstimatedRemainingTime(status.getEstimatedRemainingTime());
        entity.setProcessedCount(status.getProcessedRecords());
        entity.setTotalCount(status.getTotalRecords());
        entity.setErrorMessage(status.getErrorMessage());
        entity.setErrorStack(status.getErrorStack());
        entity.setResult(status.getResult());
        
        // 搴忓垪鍖?metrics
        if (status.getMetrics() != null) {
            entity.setMetrics(objectMapper.writeValueAsString(status.getMetrics()));
        }
        
        // 浠?metrics 涓彁鍙?executionNodeId
        if (status.getMetrics() != null && status.getMetrics().containsKey("executionNodeId")) {
            entity.setExecutionNodeId((String) status.getMetrics().get("executionNodeId"));
        }
        
        entity.setUpdatedTime(new Timestamp(System.currentTimeMillis()));
        
        return entity;
    }
    
    private TaskStatus convertToTaskStatus(TaskStatusEntity entity) throws Exception {
        TaskStatus status = new TaskStatus();
        status.setTaskId(entity.getTaskId());
        
        if (entity.getStatus() != null) {
            try {
                status.setStatus(TaskStatus.Status.valueOf(entity.getStatus()));
            } catch (IllegalArgumentException e) {
                logger.warn("鏃犳晥鐨勪换鍔＄姸鎬? {}", entity.getStatus());
                status.setStatus(TaskStatus.Status.PENDING);
            }
        }
        
        status.setStatusMessage(entity.getStatusMessage());
        status.setProgress(entity.getProgress());
        status.setCurrentStepId(entity.getCurrentStepId());
        
        if (entity.getStartTime() != null) {
            status.setStartTime(new Date(entity.getStartTime().getTime()));
        }
        if (entity.getEndTime() != null) {
            status.setEndTime(new Date(entity.getEndTime().getTime()));
        }
        
        status.setEstimatedRemainingTime(entity.getEstimatedRemainingTime());
        status.setProcessedRecords(entity.getProcessedCount());
        status.setTotalRecords(entity.getTotalCount());
        status.setErrorMessage(entity.getErrorMessage());
        status.setErrorStack(entity.getErrorStack());
        status.setResult(entity.getResult());
        
        // 鍙嶅簭鍒楀寲 metrics
        if (entity.getMetrics() != null && !entity.getMetrics().trim().isEmpty()) {
            Map<String, Object> metrics = objectMapper.readValue(
                entity.getMetrics(),
                new TypeReference<Map<String, Object>>() {}
            );
            status.setMetrics(metrics);
        }
        
        // 璁剧疆 executionNodeId 鍒?metrics
        if (entity.getExecutionNodeId() != null) {
            if (status.getMetrics() == null) {
                status.setMetrics(new HashMap<>());
            }
            status.getMetrics().put("executionNodeId", entity.getExecutionNodeId());
        }
        
        if (entity.getUpdatedTime() != null) {
            status.setUpdateTime(new Date(entity.getUpdatedTime().getTime()));
        }
        
        return status;
    }
}



