package com.chinacreator.gzcm.buszhi.workflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 工作流业务服务 — 定义 CRUD + 验证/预览/克隆/导出。
 *
 * <p><b>#147CZ 行面强类型</b>：定义行/预览/导出/校验四类结果改 record（键名与旧 Map 逐键一致），
 * 供 {@code WorkflowController} 与 {@code OntologyWorkflowApiController} 双宿主直接消费。
 * 仍返 {@code Map} 的只剩执行结果 {@code testWorkflow}——其 steps/context/activeNodes 由
 * {@link WorkflowEngine} 按定义动态生成，属运行时动态结构。</p>
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

    /** 工作流定义行（10 键；nodes/edges 仍为原样 JSON 文本，与旧 Map 同值）。 */
    public record WorkflowRow(@JsonProperty("id") String id,
                              @JsonProperty("name") String name,
                              @JsonProperty("description") String description,
                              @JsonProperty("status") String status,
                              @JsonProperty("mode") String mode,
                              @JsonProperty("nodes") String nodes,
                              @JsonProperty("edges") String edges,
                              @JsonProperty("publishedAt") String publishedAt,
                              @JsonProperty("createdAt") String createdAt,
                              @JsonProperty("updatedAt") String updatedAt) {
    }

    /** 预览展开结果（5 键；expandedSteps/context 为运行时动态结构）。 */
    public record WorkflowPreviewRow(@JsonProperty("workflowId") String workflowId,
                                     @JsonProperty("name") String name,
                                     @JsonProperty("expandedSteps") Object expandedSteps,
                                     @JsonProperty("estimatedPath") String estimatedPath,
                                     @JsonProperty("context") Object context) {
    }

    /** 导出结果（11 键；{@code $schema} 键名由注解钉住）。 */
    public record WorkflowExportRow(@JsonProperty("$schema") String schema,
                                    @JsonProperty("id") String id,
                                    @JsonProperty("name") String name,
                                    @JsonProperty("description") String description,
                                    @JsonProperty("version") String version,
                                    @JsonProperty("mode") String mode,
                                    @JsonProperty("status") String status,
                                    @JsonProperty("nodes") Object nodes,
                                    @JsonProperty("edges") Object edges,
                                    @JsonProperty("createdAt") String createdAt,
                                    @JsonProperty("updatedAt") String updatedAt) {
    }

    /** 校验结果（4 键；条目为 {code,nodeId,message}/{message} 动态集合）。 */
    public record WorkflowValidationRow(@JsonProperty("valid") boolean valid,
                                        @JsonProperty("errors") List<Map<String, String>> errors,
                                        @JsonProperty("warnings") List<Map<String, String>> warnings,
                                        @JsonProperty("suggestions") List<Map<String, String>> suggestions) {
    }

    private String nextId() {
        return "wf" + ID_SEQ.incrementAndGet();
    }

    public List<WorkflowRow> listWorkflows(int pageSize) {
        return repository.findAll(pageSize).stream()
            .map(this::toRow)
            .collect(Collectors.toList());
    }

    public long totalCount() {
        return repository.count();
    }

    public Optional<WorkflowRow> getWorkflow(String id) {
        return repository.findById(id).map(this::toRow);
    }

    public WorkflowRow createWorkflow(Map<String, Object> body) {
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
        return toRow(entity);
    }

    public Optional<WorkflowRow> updateWorkflow(String id, Map<String, Object> body) {
        Optional<WorkflowEntity> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();

        String name = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
        String description = body.containsKey("description") ? String.valueOf(body.get("description")) : null;
        String mode = body.containsKey("mode") ? String.valueOf(body.get("mode")) : null;
        String nodes = body.containsKey("nodes") ? toJsonString(body.get("nodes")) : null;
        String edges = body.containsKey("edges") ? toJsonString(body.get("edges")) : null;

        repository.update(id, name, description, mode, nodes, edges);
        return repository.findById(id).map(this::toRow);
    }

    public Optional<WorkflowRow> publishWorkflow(String id) {
        Optional<WorkflowEntity> existing = repository.findById(id);
        if (existing.isEmpty()) return Optional.empty();
        repository.publish(id);
        log.info("Workflow published: {}", id);
        return repository.findById(id).map(this::toRow);
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
    public WorkflowValidationRow validateWorkflow(Map<String, Object> definition) {
        WorkflowValidationService.ValidationResult result = validationService.validate(definition);
        return new WorkflowValidationRow(result.isValid(), result.getErrors(),
                result.getWarnings(), result.getSuggestions());
    }

    // ── 预览展开 ──────────────────────────────────────

    /**
     * 基于上下文预览展开工作流。
     */
    public Optional<WorkflowPreviewRow> previewWorkflow(String id, Map<String, Object> context) {
        return repository.findById(id)
            .map(entity -> {
                // Run a quick dry-run with context to see what nodes would execute
                WorkflowEngine.WorkflowExecutionResult result = workflowEngine.execute(entity, context);
                return new WorkflowPreviewRow(id, entity.getName(),
                        result.toMap().get("steps"), result.getStatus(), context);
            });
    }

    // ── 克隆 ──────────────────────────────────────────

    public Optional<WorkflowRow> cloneWorkflow(String id) {
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
        return Optional.of(toRow(clone));
    }

    // ── 导出 ──────────────────────────────────────────

    /**
     * 导出工作流定义为完整 JSON。
     */
    public Optional<WorkflowExportRow> exportWorkflow(String id) {
        return repository.findById(id)
            .map(entity -> {
                Object nodes;
                Object edges;
                try {
                    nodes = objectMapper.readValue(entity.getNodes(), List.class);
                    edges = objectMapper.readValue(entity.getEdges(), List.class);
                } catch (Exception e) {
                    nodes = entity.getNodes();
                    edges = entity.getEdges();
                }
                return new WorkflowExportRow(
                        "https://ecos.nousresearch.com/schemas/workflow-definition-v1.json",
                        entity.getId(), entity.getName(), entity.getDescription(), "1.0.0",
                        entity.getMode(), entity.getStatus(), nodes, edges,
                        entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null,
                        entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
            });
    }

    private WorkflowRow toRow(WorkflowEntity entity) {
        return new WorkflowRow(entity.getId(), entity.getName(), entity.getDescription(),
                entity.getStatus(), entity.getMode(), entity.getNodes(), entity.getEdges(),
                entity.getPublishedAt() != null ? entity.getPublishedAt().toString() : null,
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null,
                entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
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
