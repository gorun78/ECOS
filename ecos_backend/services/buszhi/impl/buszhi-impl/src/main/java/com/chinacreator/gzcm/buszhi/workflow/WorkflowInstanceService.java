package com.chinacreator.gzcm.buszhi.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 工作流实例管理服务 — 启动/挂起/恢复/终止流程实例。
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

    private String nextId() { return "pi" + ID_SEQ.incrementAndGet(); }

    /**
     * 启动工作流实例。
     */
    public Optional<Map<String, Object>> startInstance(String workflowId, Map<String, Object> body) {
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

        return instanceRepo.findById(instanceId).map(inst -> toMap(inst, wf));
    }

    public List<Map<String, Object>> listInstances(int limit) {
        return instanceRepo.findAll(limit).stream().map(this::toMap).collect(java.util.stream.Collectors.toList());
    }

    public Optional<Map<String, Object>> getInstance(String instanceId) {
        return instanceRepo.findById(instanceId).map(this::toMap);
    }

    public Optional<Map<String, Object>> suspendInstance(String instanceId) {
        instanceRepo.updateStatus(instanceId, "Suspended", null);
        logRepo.log(instanceId, null, "workflow", "InstanceSuspended", "实例已挂起", null, null, null);
        return instanceRepo.findById(instanceId).map(this::toMap);
    }

    public Optional<Map<String, Object>> resumeInstance(String instanceId) {
        instanceRepo.updateStatus(instanceId, "Running", null);
        logRepo.log(instanceId, null, "workflow", "InstanceResumed", "实例已恢复", null, null, null);
        return instanceRepo.findById(instanceId).map(this::toMap);
    }

    public Optional<Map<String, Object>> terminateInstance(String instanceId) {
        instanceRepo.updateStatus(instanceId, "Terminated", "Manual termination");
        logRepo.log(instanceId, null, "workflow", "InstanceTerminated", "实例已终止", null, null, null);
        return instanceRepo.findById(instanceId).map(this::toMap);
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

    private Map<String, Object> toMap(WorkflowInstanceEntity inst) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", inst.getId());
        m.put("workflowId", inst.getWorkflowId());
        m.put("workflowName", inst.getWorkflowName());
        m.put("status", inst.getStatus());
        m.put("triggerType", inst.getTriggerType());
        m.put("triggeredBy", inst.getTriggeredBy());
        m.put("startedAt", inst.getStartedAt() != null ? inst.getStartedAt().toString() : null);
        m.put("completedAt", inst.getCompletedAt() != null ? inst.getCompletedAt().toString() : null);
        m.put("errorMessage", inst.getErrorMessage());
        m.put("createdAt", inst.getCreatedAt() != null ? inst.getCreatedAt().toString() : null);
        return m;
    }

    private Map<String, Object> toMap(WorkflowInstanceEntity inst, WorkflowEntity wf) {
        Map<String, Object> m = toMap(inst);
        m.put("definition", Map.of("id", wf.getId(), "name", wf.getName(), "status", wf.getStatus()));
        return m;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return String.valueOf(obj); }
    }

    private String toJsonString(String nodes) {
        return nodes; // already JSON string
    }
}
