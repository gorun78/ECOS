package com.chinacreator.gzcm.engine.data;

import java.util.List;
import java.util.Map;

/**
 * Pipeline Task 服务 — Pipeline 2.0 任务 CRUD 与执行管理。
 *
 * @author ECOS Pipeline 2.0 Team
 */
public interface PipelineTaskService {

    /** 创建 Pipeline 任务 */
    Map<String, Object> createTask(Map<String, Object> body);

    /** 更新 Pipeline 任务 */
    Map<String, Object> updateTask(String id, Map<String, Object> body);

    /** 删除 Pipeline 任务 */
    void deleteTask(String id);

    /** 获取任务详情 */
    Map<String, Object> getTask(String id);

    /** 列出任务 */
    Map<String, Object> listTasks(int page, int pageSize);

    /** 触发执行 */
    Map<String, Object> triggerRun(String taskId, String triggeredBy);

    /** 取消执行 */
    void cancelRun(String runId);

    /** 获取任务执行历史 */
    List<Map<String, Object>> getRuns(String taskId);

    /** 获取执行详情 */
    Map<String, Object> getRun(String runId);

    /** 获取执行步骤详情 */
    List<Map<String, Object>> getRunSteps(String runId);
}
