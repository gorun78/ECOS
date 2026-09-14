package com.chinacreator.gzcm.buszhi.workflow;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 工作流业务服务 — 定义 CRUD + 验证/预览/克隆/导出。
 */
@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(200);

    private final WorkflowRepository repository;
    private final ObjectMapper objectMapper;
    private final WorkflowEngine workflowEngine;
    private final WorkflowValidationService validationService;

    public WorkflowService(WorkflowRepository repository, ObjectMapper objectMapper,
                           WorkflowEngine workflowEngine,
                           WorkflowValidationService validationService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.workflowEngine = workflowEngine;
        this.validationService = validationService;
    }

    private String nextId() {
        return "wf" + ID_SEQ.incrementAndGet();
    }

    public List<Map<String, Object>> listWorkflows(int pageSize) {
        return repository.findAll(pageSize).stream()
            .map(this::toMap)
            .collect(Collectors.toList());
    }

    public long totalCount() {
        return repository.count();
    }

    public Optional<Map<String, Object>> getWorkflow(String id) {
        return repository.findById(id).map(this::toMap);
    }

    public Map<String, Object> createWorkflow(Map<String, Object> body) {
        WorkflowEntity entity = new WorkflowEntity();
        String id = nextId();
        entity.setId(id);
        entity.setName(String.valueOf(body.getOrDefault("name", "新工作流")));
        entity.setDescription(String.valueOf(body.getOrDefault("description", "")));
        entity.setStatus("draft");
        entity.setMode(String.valueOf(body.getOrDefault("mode", "sequential")));
        entity.setNodes(String.valueOf(body.getOrDefault("nodes", "[]")));
        entity.setEdges(String.valueOf(body.getOrDefault("edges", "[]")));
        repository.insert(entity);
        log.info("Workflow created: {} [{}]", id, entity.getName());
        return toMap(entity);
    }

    public Optional<Map<String, Object>> updateWorkflow(String id, Map<String, Object> body) {
        Optional<WorkflowEntity> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();

        String name = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
        String description = body.containsKey("description") ? String.valueOf(body.get("description")) : null;
        String mode = body.containsKey("mode") ? String.valueOf(body.get("mode")) : null;
        String nodes = body.containsKey("nodes") ? toJsonString(body.get("nodes")) : null;
        String edges = body.containsKey("edges") ? toJsonString(body.get("edges")) : null;

        repository.update(id, name, description, mode, nodes, edges);
        return repository.findById(id).map(this::toMap);
    }

    public Optional<Map<String, Object>> publishWorkflow(String id) {
        Optional<WorkflowEntity> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();
        repository.publish(id);
        log.info("Workflow published: {}", id);
        return repository.findById(id).map(this::toMap);
    }

    /**
     * Execute a workflow test by running it through the WorkflowEngine state machine.
     *
     * @param id    workflow ID
     * @param body  test input payload
     * @return execution result with real step-by-step traces, or empty if workflow not found
     */
    public Optional<Map<String, Object>> testWorkflow(String id, Map<String, Object> body) {
        return repository.findById(id)
            .map(entity -> {
                log.info("WorkflowEngine starting test execution for: {}", id);
                WorkflowEngine.WorkflowExecutionResult result = workflowEngine.execute(entity, body);
                Map<String, Object> resultMap = result.toMap();
                log.info("WorkflowEngine test completed: {} — {} steps in {}",
                    id, ((List<?>) resultMap.get("steps")).size(), resultMap.get("executionTime"));
                return resultMap;
            });
    }

    public boolean deleteWorkflow(String id) {
        return repository.deleteById(id) > 0;
    }

    // ── 验证 ──────────────────────────────────────────

    /**
     * 验证工作流定义 JSON Schema。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> validateWorkflow(Map<String, Object> definition) {
        WorkflowValidationService.ValidationResult result = validationService.validate(definition);
        return result.toMap();
    }

    // ── 预览展开 ──────────────────────────────────────

    /**
     * 基于上下文预览展开工作流。
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> previewWorkflow(String id, Map<String, Object> context) {
        return repository.findById(id)
            .map(entity -> {
                // Run a quick dry-run with context to see what nodes would execute
                WorkflowEngine.WorkflowExecutionResult result = workflowEngine.execute(entity, context);
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("workflowId", id);
                map.put("name", entity.getName());
                map.put("expandedSteps", result.toMap().get("steps"));
                map.put("estimatedPath", result.getStatus());
                map.put("context", context);
                return map;
            });
    }

    // ── 克隆 ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> cloneWorkflow(String id) {
        Optional<WorkflowEntity> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();

        WorkflowEntity source = existing.get();
        WorkflowEntity clone = new WorkflowEntity();
        clone.setId(nextId());
        clone.setName(source.getName() + " (副本)");
        clone.setDescription(source.getDescription());
        clone.setStatus("draft");
        clone.setMode(source.getMode());
        clone.setNodes(source.getNodes());
        clone.setEdges(source.getEdges());
        repository.insert(clone);

        log.info("Workflow cloned: {} → {}", id, clone.getId());
        return Optional.of(toMap(clone));
    }

    // ── 导出 ──────────────────────────────────────────

    /**
     * 导出工作流定义为完整 JSON。
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> exportWorkflow(String id) {
        return repository.findById(id)
            .map(entity -> {
                Map<String, Object> export = new LinkedHashMap<>();
                export.put("$schema", "https://ecos.nousresearch.com/schemas/workflow-definition-v1.json");
                export.put("id", entity.getId());
                export.put("name", entity.getName());
                export.put("description", entity.getDescription());
                export.put("version", "1.0.0");
                export.put("mode", entity.getMode());
                export.put("status", entity.getStatus());
                try {
                    export.put("nodes", objectMapper.readValue(entity.getNodes(), List.class));
                    export.put("edges", objectMapper.readValue(entity.getEdges(), List.class));
                } catch (Exception e) {
                    export.put("nodes", entity.getNodes());
                    export.put("edges", entity.getEdges());
                }
                export.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
                export.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
                return export;
            });
    }

    private Map<String, Object> toMap(WorkflowEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId());
        map.put("name", entity.getName());
        map.put("description", entity.getDescription());
        map.put("status", entity.getStatus());
        map.put("mode", entity.getMode());
        map.put("nodes", entity.getNodes());
        map.put("edges", entity.getEdges());
        map.put("publishedAt", entity.getPublishedAt() != null ? entity.getPublishedAt().toString() : null);
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null);
        map.put("updatedAt", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return map;
    }

    private String toJsonString(Object obj) {
        if (obj instanceof String) return (String) obj;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", obj, e);
            return String.valueOf(obj);
        }
    }
}
