package com.chinacreator.gzcm.buszhi.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工作流实例管理服务 — 启动/挂起/恢复/终止流程实例。
 *
 * <p><b>#147CZ 行面强类型</b>：实例行改 record（10 键；启动腿在尾部追加 {@code definition} 引用，
 * 属性级 NON_NULL 保「列表行无该键」的旧形态）。</p>
 */
@Service
public class WorkflowInstanceService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowInstanceService.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(1000);

    private final WorkflowInstanceRepository instanceRepo;
    private final WorkflowLogRepository logRepo;
    private final WorkflowRepository workflowRepo;
    private final ObjectMapper objectMapper;
    private final WorkflowEngine workflowEngine;

    public WorkflowInstanceService(WorkflowInstanceRepository instanceRepo,
                                   WorkflowLogRepository logRepo,
                                   WorkflowRepository workflowRepo,
                                   ObjectMapper objectMapper,
                                   WorkflowEngine workflowEngine) {
        this.instanceRepo = instanceRepo;
        this.logRepo = logRepo;
        this.workflowRepo = workflowRepo;
        this.objectMapper = objectMapper;
        this.workflowEngine = workflowEngine;
    }

    /** 实例所属定义引用（3 键；仅启动腿出现）。 */
    public record WorkflowDefinitionRef(@JsonProperty("id") String id,
                                        @JsonProperty("name") String name,
                                        @JsonProperty("status") String status) {
    }

    /** 实例行（10 键 + 可选 definition）。 */
    public record WorkflowInstanceRow(@JsonProperty("id") String id,
                                      @JsonProperty("workflowId") String workflowId,
                                      @JsonProperty("workflowName") String workflowName,
                                      @JsonProperty("status") String status,
                                      @JsonProperty("triggerType") String triggerType,
                                      @JsonProperty("triggeredBy") String triggeredBy,
                                      @JsonProperty("startedAt") String startedAt,
                                      @JsonProperty("completedAt") String completedAt,
                                      @JsonProperty("errorMessage") String errorMessage,
                                      @JsonProperty("createdAt") String createdAt,
                                      @JsonInclude(JsonInclude.Include.NON_NULL)
                                      @JsonProperty("definition") WorkflowDefinitionRef definition) {
    }

    private String nextId() { return "pi" + ID_SEQ.incrementAndGet(); }

    /**
     * 启动工作流实例。
     */
    public Optional<WorkflowInstanceRow> startInstance(String workflowId, Map<String, Object> body) {
        Optional<WorkflowEntity> wfOpt = workflowRepo.findById(workflowId);
        if (wfOpt.isEmpty()) return Optional.empty();

        WorkflowEntity wf = wfOpt.get();
        String traceId = "trace-" + UUID.randomUUID().toString().substring(0, 8);
        String instanceId = nextId();

        // 创建实例
        WorkflowInstanceEntity instance = new WorkflowInstanceEntity();
        instance.setId(instanceId);
        instance.setWorkflowId(workflowId);
        instance.setWorkflowName(wf.getName());
        instance.setVersionNo("1.0.0");
        instance.setStatus("Running");
        instance.setTriggerType(body != null && body.containsKey("triggerType")
            ? String.valueOf(body.get("triggerType")) : "MANUAL");
        instance.setTriggeredBy(body != null && body.containsKey("userId")
            ? String.valueOf(body.get("userId")) : "system");
        instance.setTriggeredObjectId(body != null && body.containsKey("objectId")
            ? String.valueOf(body.get("objectId")) : null);
        instance.setVariables(body != null && body.containsKey("variables")
            ? toJson(body.get("variables")) : "{}");
        instance.setContext(body != null ? toJson(body) : "{}");

        // 计算起始节点
        List<Map<String, Object>> startNodeIds = findStartNodes(toJsonString(wf.getNodes()));
        instance.setCurrentNodeIds(toJson(startNodeIds));

        instanceRepo.insert(instance);

        // 记录日志
        logRepo.log(instanceId, null, "workflow", "InstanceStarted",
            "流程实例启动: " + wf.getName(), toJson(Map.of("definitionId", workflowId)), null, traceId);

        // 执行工作流
        try {
            WorkflowEngine.WorkflowExecutionResult result = workflowEngine.execute(wf, body != null ? body : Map.of());
            instanceRepo.updateCurrentNodes(instanceId, toJson(result.getActiveNodes() != null
                ? result.getActiveNodes() : Collections.emptyList()));

            if ("completed".equals(result.getStatus())) {
                instanceRepo.complete(instanceId);
                logRepo.log(instanceId, null, "workflow", "InstanceCompleted",
                    "流程实例完成", null, null, traceId);
            }
        } catch (Exception e) {
            log.error("Workflow instance execution failed: {}", instanceId, e);
            instanceRepo.updateStatus(instanceId, "Failed", e.getMessage());
            logRepo.log(instanceId, null, "workflow", "InstanceFailed",
                "执行失败: " + e.getMessage(), null, null, traceId);
        }

        return instanceRepo.findById(instanceId)
                .map(inst -> toRow(inst, new WorkflowDefinitionRef(wf.getId(), wf.getName(), wf.getStatus())));
    }

    public List<WorkflowInstanceRow> listInstances(int limit) {
        return instanceRepo.findAll(limit).stream().map(this::toRow)
                .collect(java.util.stream.Collectors.toList());
    }

    public Optional<WorkflowInstanceRow> getInstance(String instanceId) {
        return instanceRepo.findById(instanceId).map(this::toRow);
    }

    public Optional<WorkflowInstanceRow> suspendInstance(String instanceId) {
        instanceRepo.updateStatus(instanceId, "Suspended", null);
        logRepo.log(instanceId, null, "workflow", "InstanceSuspended", "实例已挂起", null, null, null);
        return instanceRepo.findById(instanceId).map(this::toRow);
    }

    public Optional<WorkflowInstanceRow> resumeInstance(String instanceId) {
        instanceRepo.updateStatus(instanceId, "Running", null);
        logRepo.log(instanceId, null, "workflow", "InstanceResumed", "实例已恢复", null, null, null);
        return instanceRepo.findById(instanceId).map(this::toRow);
    }

    public Optional<WorkflowInstanceRow> terminateInstance(String instanceId) {
        instanceRepo.updateStatus(instanceId, "Terminated", "Manual termination");
        logRepo.log(instanceId, null, "workflow", "InstanceTerminated", "实例已终止", null, null, null);
        return instanceRepo.findById(instanceId).map(this::toRow);
    }

    // ── helpers ──

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> findStartNodes(String nodesJson) {
        try {
            List<Map<String, Object>> nodes = objectMapper.readValue(nodesJson, List.class);
            List<Map<String, Object>> starts = new ArrayList<>();
            for (Map<String, Object> n : nodes) {
                if ("start".equals(n.get("type"))) starts.add(n);
            }
            return starts;
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private WorkflowInstanceRow toRow(WorkflowInstanceEntity inst) {
        return toRow(inst, null);
    }

    private WorkflowInstanceRow toRow(WorkflowInstanceEntity inst, WorkflowDefinitionRef definition) {
        return new WorkflowInstanceRow(inst.getId(), inst.getWorkflowId(), inst.getWorkflowName(),
                inst.getStatus(), inst.getTriggerType(), inst.getTriggeredBy(),
                inst.getStartedAt() != null ? inst.getStartedAt().toString() : null,
                inst.getCompletedAt() != null ? inst.getCompletedAt().toString() : null,
                inst.getErrorMessage(),
                inst.getCreatedAt() != null ? inst.getCreatedAt().toString() : null,
                definition);
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return String.valueOf(obj); }
    }

    private String toJsonString(String nodes) {
        return nodes; // already JSON string
    }
}
