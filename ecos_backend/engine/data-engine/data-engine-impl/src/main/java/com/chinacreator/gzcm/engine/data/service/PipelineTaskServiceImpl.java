package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.PipelineTaskService;
import com.chinacreator.gzcm.engine.data.dao.PipelineTaskDao;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Pipeline Task 服务实现 — 任务 CRUD + 执行管理。
 *
 * @author ECOS Pipeline 2.0 Team
 */
@Service
public class PipelineTaskServiceImpl implements PipelineTaskService {

    private static final Logger log = LoggerFactory.getLogger(PipelineTaskServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PipelineTaskDao taskDao;
    private final PipelineExecutionEngine executionEngine;

    public PipelineTaskServiceImpl(PipelineTaskDao taskDao, PipelineExecutionEngine executionEngine) {
        this.taskDao = taskDao;
        this.executionEngine = executionEngine;
    }

    // H8-T1: ecos_pipeline_task / ecos_pipeline_step / ecos_pipeline_run / ecos_pipeline_step_run
    // 建表 DDL 收编至 db/migration（V162），运行时不再内嵌 DDL。

    // ── CRUD ──

    @Override
    @Transactional
    public Map<String, Object> createTask(Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        String name = (String) body.get("name");
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name 不能为空");

        String yamlContent = (String) body.getOrDefault("yaml_content", "");
        taskDao.insertTask(
            id, name,
            body.getOrDefault("description", ""),
            yamlContent,
            body.getOrDefault("git_url", ""),
            body.getOrDefault("git_branch", "main"),
            body.getOrDefault("status", "DRAFT"),
            body.getOrDefault("cron_expression", ""),
            safeJson(body.get("config_json")),
            body.getOrDefault("task_type", "TRANSFORM"),
            body.getOrDefault("created_by", "system"));

        // 处理步骤
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) body.get("steps");
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                Map<String, Object> step = steps.get(i);
                taskDao.insertStep(
                    UUID.randomUUID().toString(), id, i + 1,
                    step.getOrDefault("node_id", ""),
                    step.getOrDefault("node_type", ""),
                    safeJson(step.get("config_json")),
                    safeJson(step.get("depends_on")),
                    step.getOrDefault("position_x", 0),
                    step.getOrDefault("position_y", 0));
            }
        }

        log.info("创建 Pipeline 任务: {} (id={})", name, id);
        return getTask(id);
    }

    @Override
    @Transactional
    public Map<String, Object> updateTask(String id, Map<String, Object> body) {
        getTask(id); // 校验存在
        taskDao.updateTask(
            id,
            body.get("name"), body.get("description"), body.get("yaml_content"),
            body.get("git_url"), body.get("git_branch"), body.get("status"),
            body.get("cron_expression"), body.get("task_type"));

        // 如果传入了 steps，替换步骤列表
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) body.get("steps");
        if (steps != null) {
            taskDao.deleteStepsByTask(id);
            for (int i = 0; i < steps.size(); i++) {
                Map<String, Object> step = steps.get(i);
                taskDao.insertStep(
                    UUID.randomUUID().toString(), id, i + 1,
                    step.getOrDefault("node_id", ""),
                    step.getOrDefault("node_type", ""),
                    safeJson(step.get("config_json")),
                    safeJson(step.get("depends_on")),
                    step.getOrDefault("position_x", 0),
                    step.getOrDefault("position_y", 0));
            }
        }

        log.info("更新 Pipeline 任务: id={}", id);
        return getTask(id);
    }

    @Override
    @Transactional
    public void deleteTask(String id) {
        getTask(id);
        taskDao.deleteStepsByTask(id);
        taskDao.deleteStepRunsByTask(id);
        taskDao.deleteRunsByTask(id);
        taskDao.deleteTask(id);
        log.info("删除 Pipeline 任务: id={}", id);
    }

    @Override
    public Map<String, Object> getTask(String id) {
        List<Map<String, Object>> rows = taskDao.queryTask(id);
        if (rows.isEmpty()) throw new IllegalArgumentException("任务不存在: " + id);

        Map<String, Object> task = rows.get(0);
        List<Map<String, Object>> steps = taskDao.queryStepsByTask(id);
        task.put("steps", steps);
        return task;
    }

    @Override
    public Map<String, Object> listTasks(int page, int pageSize) {
        int total = taskDao.countTasks();
        int offset = Math.max(0, page - 1) * pageSize;
        List<Map<String, Object>> list = taskDao.pagedTasks(pageSize, offset);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("list", list);
        return result;
    }

    // ── 执行管理 ──

    @Override
    @Transactional
    public Map<String, Object> triggerRun(String taskId, String triggeredBy) {
        getTask(taskId); // 校验存在

        String runId = UUID.randomUUID().toString();
        List<Map<String, Object>> steps = taskDao.queryStepsByTask(taskId);

        taskDao.insertRun(runId, taskId, triggeredBy != null ? triggeredBy : "manual", steps.size());

        // 创建步骤执行记录
        for (Map<String, Object> step : steps) {
            taskDao.insertStepRun(
                UUID.randomUUID().toString(), runId, step.get("id"), step.get("node_id"));
        }

        // 异步提交执行
        new Thread(() -> executionEngine.execute(runId), "pipeline-exec-" + runId).start();

        log.info("触发 Pipeline 执行: taskId={}, runId={}", taskId, runId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("taskId", taskId);
        result.put("status", "QUEUED");
        result.put("totalSteps", steps.size());
        return result;
    }

    @Override
    @Transactional
    public void cancelRun(String runId) {
        taskDao.cancelRun(runId);
        log.info("取消 Pipeline 执行: runId={}", runId);
    }

    @Override
    public List<Map<String, Object>> getRuns(String taskId) {
        return taskDao.queryRunsByTask(taskId);
    }

    @Override
    public Map<String, Object> getRun(String runId) {
        List<Map<String, Object>> rows = taskDao.queryRun(runId);
        if (rows.isEmpty()) throw new IllegalArgumentException("执行记录不存在: " + runId);
        return rows.get(0);
    }

    @Override
    public List<Map<String, Object>> getRunSteps(String runId) {
        return taskDao.queryStepRunsByRun(runId);
    }

    private String safeJson(Object obj) {
        if (obj == null) return "{}";
        if (obj instanceof String s) return s;
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
