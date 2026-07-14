package com.chinacreator.gzcm.buszhi.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 工作流任务管理服务 — 任务CRUD、签收、完成、转签、驳回。
 */
@Service
public class WorkflowTaskService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTaskService.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(3000);

    private final WorkflowTaskRepository taskRepo;
    private final WorkflowLogRepository logRepo;
    private final ObjectMapper objectMapper;

    public WorkflowTaskService(WorkflowTaskRepository taskRepo,
                                WorkflowLogRepository logRepo,
                                ObjectMapper objectMapper) {
        this.taskRepo = taskRepo;
        this.logRepo = logRepo;
        this.objectMapper = objectMapper;
    }

    private String nextId() { return "task" + ID_SEQ.incrementAndGet(); }

    // ── 我的任务列表 ──

    public List<Map<String, Object>> listTasks(String assignee, int limit) {
        if (assignee != null && !assignee.isBlank()) {
            return taskRepo.findByAssignee(assignee, limit).stream().map(this::toMap).collect(Collectors.toList());
        }
        return taskRepo.findUnassigned(limit).stream().map(this::toMap).collect(Collectors.toList());
    }

    public Optional<Map<String, Object>> getTask(String taskId) {
        return taskRepo.findById(taskId).map(this::toMap);
    }

    // ── 创建任务（引擎内部调用） ──

    public WorkflowTaskEntity createTask(String instanceId, String nodeId, String taskType,
                                          String title, String assignee, List<String> candidateUsers,
                                          List<String> candidateRoles, String formSchemaJson,
                                          String priority, String dueDate) {
        WorkflowTaskEntity entity = new WorkflowTaskEntity();
        entity.setId(nextId());
        entity.setInstanceId(instanceId);
        entity.setNodeId(nodeId);
        entity.setTaskType(taskType);
        entity.setTitle(title);
        entity.setAssignee(assignee);
        entity.setCandidateUsers(toJson(candidateUsers));
        entity.setCandidateRoles(toJson(candidateRoles));
        entity.setStatus(assignee != null && !assignee.isBlank() ? "Assigned" : "New");
        entity.setPriority(priority != null ? priority : "NORMAL");
        entity.setFormSchema(formSchemaJson);
        entity.setDueDate(parseDueDate(dueDate));
        taskRepo.insert(entity);
        log.info("Workflow task created: {} [{}]", entity.getId(), title);
        return entity;
    }

    // ── 签收 ──

    public Optional<Map<String, Object>> claimTask(String taskId, String userId) {
        Optional<WorkflowTaskEntity> taskOpt = taskRepo.findById(taskId);
        if (taskOpt.isEmpty()) return Optional.empty();
        if (!"New".equals(taskOpt.get().getStatus())) return Optional.empty();

        taskRepo.claim(taskId, userId);
        logRepo.log(taskOpt.get().getInstanceId(), taskOpt.get().getNodeId(), "userTask",
            "TaskClaimed", "任务被签收: " + taskOpt.get().getTitle(), toJson(Map.of("userId", userId)), null, null);
        return taskRepo.findById(taskId).map(this::toMap);
    }

    // ── 完成 ──

    public Optional<Map<String, Object>> completeTask(String taskId, Map<String, Object> body) {
        Optional<WorkflowTaskEntity> taskOpt = taskRepo.findById(taskId);
        if (taskOpt.isEmpty()) return Optional.empty();

        WorkflowTaskEntity task = taskOpt.get();
        String result = body != null ? toJson(body) : "{}";
        String completedBy = body != null && body.containsKey("userId")
            ? String.valueOf(body.get("userId")) : task.getAssignee();

        taskRepo.complete(taskId, result, completedBy);
        logRepo.log(task.getInstanceId(), task.getNodeId(), "userTask",
            "TaskCompleted", "任务完成: " + task.getTitle(), result, null, null);
        return taskRepo.findById(taskId).map(this::toMap);
    }

    // ── 转签 ──

    public Optional<Map<String, Object>> transferTask(String taskId, String newAssignee) {
        Optional<WorkflowTaskEntity> taskOpt = taskRepo.findById(taskId);
        if (taskOpt.isEmpty()) return Optional.empty();

        taskRepo.transfer(taskId, newAssignee);
        logRepo.log(taskOpt.get().getInstanceId(), taskOpt.get().getNodeId(), "userTask",
            "TaskTransferred", "任务转签 → " + newAssignee, null, null, null);
        return taskRepo.findById(taskId).map(this::toMap);
    }

    // ── 驳回 ──

    public Optional<Map<String, Object>> rejectTask(String taskId, String reason) {
        Optional<WorkflowTaskEntity> taskOpt = taskRepo.findById(taskId);
        if (taskOpt.isEmpty()) return Optional.empty();

        taskRepo.reject(taskId, toJson(Map.of("reason", reason != null ? reason : "")));
        logRepo.log(taskOpt.get().getInstanceId(), taskOpt.get().getNodeId(), "userTask",
            "TaskRejected", "任务被驳回: " + reason, null, null, null);
        return taskRepo.findById(taskId).map(this::toMap);
    }

    // ── 统计 ──

    public Map<String, Object> getStatistics(String userId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 简化统计
        stats.put("total", 0);
        stats.put("pending", 0);
        stats.put("inProgress", 0);
        stats.put("completed", 0);
        return stats;
    }

    // ── helpers ──

    private Map<String, Object> toMap(WorkflowTaskEntity entity) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", entity.getId());
        m.put("instanceId", entity.getInstanceId());
        m.put("nodeId", entity.getNodeId());
        m.put("taskType", entity.getTaskType());
        m.put("title", entity.getTitle());
        m.put("assignee", entity.getAssignee());
        m.put("status", entity.getStatus());
        m.put("priority", entity.getPriority());
        m.put("dueDate", entity.getDueDate() != null ? entity.getDueDate().toString() : null);
        m.put("completedAt", entity.getCompletedAt() != null ? entity.getCompletedAt().toString() : null);
        m.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        return m;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return String.valueOf(obj); }
    }

    private java.time.LocalDateTime parseDueDate(String dueDateStr) {
        if (dueDateStr == null) return null;
        try {
            // Simple duration parsing: "48h" → now + 48 hours
            if (dueDateStr.endsWith("h")) {
                long hours = Long.parseLong(dueDateStr.replace("h", ""));
                return java.time.LocalDateTime.now().plusHours(hours);
            }
            if (dueDateStr.endsWith("d")) {
                long days = Long.parseLong(dueDateStr.replace("d", ""));
                return java.time.LocalDateTime.now().plusDays(days);
            }
            if (dueDateStr.endsWith("m")) {
                long minutes = Long.parseLong(dueDateStr.replace("m", ""));
                return java.time.LocalDateTime.now().plusMinutes(minutes);
            }
            return java.time.LocalDateTime.parse(dueDateStr);
        } catch (Exception e) {
            log.warn("Failed to parse dueDate: {}", dueDateStr);
            return null;
        }
    }
}
