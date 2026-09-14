package com.chinacreator.gzcm.common.engine;

import java.util.List;
import java.util.Map;

/**
 * 支持异步任务的引擎接口 — 扩展自 {@link IEngine}，
 * 为需要提交、查询异步任务的引擎（如数据引擎、认知引擎）提供统一契约。
 *
 * @author ECOS PMO
 * @since 1.0.0
 */
public interface ITaskAwareEngine extends IEngine {

    /**
     * 获取当前活跃任务列表。
     */
    List<Task> getActiveTasks();

    /**
     * 提交一个异步任务。
     */
    Task submitTask(TaskRequest req);

    /**
     * 按任务ID查询任务状态。
     */
    TaskStatus queryTask(String taskId);

    /**
     * 异步任务实体。
     */
    class Task {
        private String taskId;
        private String type;
        private TaskStatus status;
        private Map<String, Object> params;
        private long createdAt;

        public Task() {}

        public String getTaskId() { return taskId; }
        public void setTaskId(String taskId) { this.taskId = taskId; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public TaskStatus getStatus() { return status; }
        public void setStatus(TaskStatus status) { this.status = status; }

        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }

        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

        @Override
        public String toString() {
            return "Task{taskId='" + taskId + "', type='" + type
                    + "', status=" + status + ", createdAt=" + createdAt + '}';
        }
    }

    /**
     * 任务提交请求。
     */
    class TaskRequest {
        private String type;
        private Map<String, Object> params;

        public TaskRequest() {}

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public Map<String, Object> getParams() { return params; }
        public void setParams(Map<String, Object> params) { this.params = params; }
    }

    /**
     * 任务状态枚举。
     */
    enum TaskStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
