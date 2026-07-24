package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.PipelineTaskService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
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

    private final JdbcTemplate jdbc;
    private final PipelineExecutionEngine executionEngine;

    public PipelineTaskServiceImpl(JdbcTemplate jdbc, PipelineExecutionEngine executionEngine) {
        this.jdbc = jdbc;
        this.executionEngine = executionEngine;
    }

    @PostConstruct
    public void init() {
        ensureSchema();
    }

    private void ensureSchema() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_pipeline_task (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(200) NOT NULL,
                    description TEXT,
                    yaml_content TEXT NOT NULL,
                    git_url VARCHAR(500),
                    git_branch VARCHAR(100) DEFAULT 'main',
                    git_commit_id VARCHAR(40),
                    status VARCHAR(20) DEFAULT 'DRAFT',
                    cron_expression VARCHAR(100),
                    config_json JSONB DEFAULT '{}',
                    enabled BOOLEAN DEFAULT true,
                    created_by VARCHAR(100),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            try {
                jdbc.execute("ALTER TABLE ecos_pipeline_task ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT true");
            } catch (Exception ignored) {}
            try {
                jdbc.execute("ALTER TABLE ecos_pipeline_task ADD COLUMN IF NOT EXISTS task_type VARCHAR(20) DEFAULT 'TRANSFORM'");
            } catch (Exception ignored) {}
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_pipeline_step (
                    id VARCHAR(36) PRIMARY KEY,
                    task_id VARCHAR(36) NOT NULL,
                    step_order INTEGER NOT NULL,
                    node_id VARCHAR(100) NOT NULL,
                    node_type VARCHAR(50) NOT NULL,
                    config_json JSONB DEFAULT '{}',
                    depends_on JSONB DEFAULT '[]',
                    position_x FLOAT DEFAULT 0,
                    position_y FLOAT DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_pipeline_run (
                    id VARCHAR(36) PRIMARY KEY,
                    task_id VARCHAR(36) NOT NULL,
                    status VARCHAR(20) DEFAULT 'QUEUED',
                    triggered_by VARCHAR(50) DEFAULT 'manual',
                    total_steps INTEGER DEFAULT 0,
                    completed_steps INTEGER DEFAULT 0,
                    started_at TIMESTAMP,
                    finished_at TIMESTAMP,
                    elapsed_ms INTEGER DEFAULT 0,
                    error_msg TEXT,
                    log_json JSONB DEFAULT '[]',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS ecos_pipeline_step_run (
                    id VARCHAR(36) PRIMARY KEY,
                    run_id VARCHAR(36) NOT NULL,
                    step_id VARCHAR(36) NOT NULL,
                    node_id VARCHAR(100) NOT NULL,
                    status VARCHAR(20) DEFAULT 'QUEUED',
                    rows_input INTEGER DEFAULT 0,
                    rows_output INTEGER DEFAULT 0,
                    started_at TIMESTAMP,
                    finished_at TIMESTAMP,
                    elapsed_ms INTEGER DEFAULT 0,
                    error_msg TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            log.info("Pipeline Task 表已就绪");
        } catch (Exception e) {
            log.warn("Pipeline Task 表初始化异常: {}", e.getMessage());
        }
    }

    // ── CRUD ──

    @Override
    @Transactional
    public Map<String, Object> createTask(Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        String name = (String) body.get("name");
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name 不能为空");

        String yamlContent = (String) body.getOrDefault("yaml_content", "");
        jdbc.update(
            "INSERT INTO ecos_pipeline_task (id, name, description, yaml_content, git_url, git_branch, status, cron_expression, config_json, task_type, created_by) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)",
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
                jdbc.update(
                    "INSERT INTO ecos_pipeline_step (id, task_id, step_order, node_id, node_type, config_json, depends_on, position_x, position_y) " +
                    "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)",
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
        jdbc.update(
            "UPDATE ecos_pipeline_task SET name = COALESCE(?, name), description = COALESCE(?, description), " +
            "yaml_content = COALESCE(?, yaml_content), git_url = COALESCE(?, git_url), " +
            "git_branch = COALESCE(?, git_branch), status = COALESCE(?, status), " +
            "cron_expression = COALESCE(?, cron_expression), task_type = COALESCE(?, task_type), " +
            "updated_at = NOW() WHERE id = ?",
            body.get("name"), body.get("description"), body.get("yaml_content"),
            body.get("git_url"), body.get("git_branch"), body.get("status"),
            body.get("cron_expression"), body.get("task_type"), id);

        // 如果传入了 steps，替换步骤列表
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) body.get("steps");
        if (steps != null) {
            jdbc.update("DELETE FROM ecos_pipeline_step WHERE task_id = ?", id);
            for (int i = 0; i < steps.size(); i++) {
                Map<String, Object> step = steps.get(i);
                jdbc.update(
                    "INSERT INTO ecos_pipeline_step (id, task_id, step_order, node_id, node_type, config_json, depends_on, position_x, position_y) " +
                    "VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)",
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
        jdbc.update("DELETE FROM ecos_pipeline_step WHERE task_id = ?", id);
        jdbc.update("DELETE FROM ecos_pipeline_step_run WHERE run_id IN (SELECT id FROM ecos_pipeline_run WHERE task_id = ?)", id);
        jdbc.update("DELETE FROM ecos_pipeline_run WHERE task_id = ?", id);
        jdbc.update("DELETE FROM ecos_pipeline_task WHERE id = ?", id);
        log.info("删除 Pipeline 任务: id={}", id);
    }

    @Override
    public Map<String, Object> getTask(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM ecos_pipeline_task WHERE id = ?", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("任务不存在: " + id);

        Map<String, Object> task = rows.get(0);
        List<Map<String, Object>> steps = jdbc.queryForList(
            "SELECT * FROM ecos_pipeline_step WHERE task_id = ? ORDER BY step_order", id);
        task.put("steps", steps);
        return task;
    }

    @Override
    public Map<String, Object> listTasks(int page, int pageSize) {
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM ecos_pipeline_task", Integer.class);
        int offset = Math.max(0, page - 1) * pageSize;
        List<Map<String, Object>> list = jdbc.queryForList(
            "SELECT * FROM ecos_pipeline_task ORDER BY updated_at DESC LIMIT ? OFFSET ?", pageSize, offset);

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
        List<Map<String, Object>> steps = jdbc.queryForList(
            "SELECT * FROM ecos_pipeline_step WHERE task_id = ? ORDER BY step_order", taskId);

        jdbc.update(
            "INSERT INTO ecos_pipeline_run (id, task_id, status, triggered_by, total_steps, started_at) " +
            "VALUES (?, ?, 'QUEUED', ?, ?, NOW())",
            runId, taskId, triggeredBy != null ? triggeredBy : "manual", steps.size());

        // 创建步骤执行记录
        for (Map<String, Object> step : steps) {
            jdbc.update(
                "INSERT INTO ecos_pipeline_step_run (id, run_id, step_id, node_id, status) VALUES (?, ?, ?, ?, 'QUEUED')",
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
        jdbc.update("UPDATE ecos_pipeline_run SET status = 'CANCELLED', finished_at = NOW() WHERE id = ? AND status IN ('QUEUED','RUNNING')", runId);
        jdbc.update("UPDATE ecos_pipeline_step_run SET status = 'CANCELLED', finished_at = NOW() WHERE run_id = ? AND status IN ('QUEUED','RUNNING')", runId);
        log.info("取消 Pipeline 执行: runId={}", runId);
    }

    @Override
    public List<Map<String, Object>> getRuns(String taskId) {
        return jdbc.queryForList(
            "SELECT * FROM ecos_pipeline_run WHERE task_id = ? ORDER BY created_at DESC LIMIT 50", taskId);
    }

    @Override
    public Map<String, Object> getRun(String runId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM ecos_pipeline_run WHERE id = ?", runId);
        if (rows.isEmpty()) throw new IllegalArgumentException("执行记录不存在: " + runId);
        return rows.get(0);
    }

    @Override
    public List<Map<String, Object>> getRunSteps(String runId) {
        return jdbc.queryForList(
            "SELECT * FROM ecos_pipeline_step_run WHERE run_id = ? ORDER BY created_at", runId);
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
