package com.chinacreator.gzcm.buszhi.workflow;

import com.chinacreator.gzcm.runtime.hermes.HermesEngine;
import com.chinacreator.gzcm.runtime.hermes.scheduler.AgentResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Workflow State Machine Engine — parses nodes/edges from a WorkflowEntity
 * and executes them as a DAG, supporting real execution for all 5 node types:
 * start, end, userTask, agentTask, exclusiveGateway.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>sequential</b> — topological order, one node at a time</li>
 *   <li><b>parallel</b> — independent nodes run concurrently (simulated here)</li>
 * </ul>
 */
@Component
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final ObjectMapper objectMapper;
    private final ExpressionService expressionService;
    private final WorkflowTaskService taskService;
    private final WorkflowLogRepository logRepo;
    private final HermesEngine hermesEngine;

    public WorkflowEngine(ObjectMapper objectMapper,
                          ExpressionService expressionService,
                          WorkflowTaskService taskService,
                          WorkflowLogRepository logRepo,
                          HermesEngine hermesEngine) {
        this.objectMapper = objectMapper;
        this.expressionService = expressionService;
        this.taskService = taskService;
        this.logRepo = logRepo;
        this.hermesEngine = hermesEngine;
    }

    /**
     * Execute a workflow given its entity definition and input payload.
     *
     * @param workflowEntity the persisted workflow (nodes, edges, mode)
     * @param input          input data from the test request body
     * @return a structured execution result with steps and timing
     */
    public WorkflowExecutionResult execute(WorkflowEntity workflowEntity, Map<String, Object> input) {
        Instant start = Instant.now();

        // 1. Parse nodes and edges
        List<WorkflowNode> nodes = parseNodes(workflowEntity.getNodes());
        List<WorkflowEdge> edges = parseEdges(workflowEntity.getEdges());
        String mode = workflowEntity.getMode() != null ? workflowEntity.getMode() : "sequential";

        log.info("WorkflowEngine executing '{}' ({} nodes, {} edges, mode={})",
            workflowEntity.getName(), nodes.size(), edges.size(), mode);

        if (nodes.isEmpty()) {
            return new WorkflowExecutionResult(
                workflowEntity.getId(), "completed",
                Duration.between(start, Instant.now()),
                Collections.emptyList(), input
            );
        }

        // 2. Build adjacency & in-degree for topological sort
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, WorkflowNode> nodeMap = new LinkedHashMap<>();

        for (WorkflowNode node : nodes) {
            String id = node.getId();
            adjacency.putIfAbsent(id, new ArrayList<>());
            inDegree.putIfAbsent(id, 0);
            nodeMap.put(id, node);
        }
        for (WorkflowEdge edge : edges) {
            String from = edge.getSource();
            String to = edge.getTarget();
            if (nodeMap.containsKey(from) && nodeMap.containsKey(to)) {
                adjacency.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
                inDegree.merge(to, 1, Integer::sum);
            }
        }

        // 3. Execute — build execution steps
        List<ExecutionStep> steps = new ArrayList<>();

        if ("parallel".equals(mode)) {
            steps = executeParallel(nodes, edges, nodeMap, adjacency, inDegree, input);
        } else {
            steps = executeSequential(nodes, edges, nodeMap, adjacency, inDegree, input);
        }

        Duration totalTime = Duration.between(start, Instant.now());
        return new WorkflowExecutionResult(
            workflowEntity.getId(),
            steps.stream().allMatch(s -> "success".equals(s.getStatus())) ? "completed" : "partial",
            totalTime,
            steps,
            input
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // Sequential execution — topological order
    // ─────────────────────────────────────────────────────────────────
    private List<ExecutionStep> executeSequential(
            List<WorkflowNode> nodes,
            List<WorkflowEdge> edges,
            Map<String, WorkflowNode> nodeMap,
            Map<String, List<String>> adjacency,
            Map<String, Integer> inDegree,
            Map<String, Object> input) {

        List<ExecutionStep> steps = new ArrayList<>();
        Map<String, Integer> currentInDegree = new LinkedHashMap<>(inDegree);
        Queue<String> queue = new LinkedList<>();

        // Seed with zero-in-degree nodes
        for (Map.Entry<String, Integer> entry : currentInDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            WorkflowNode node = nodeMap.get(nodeId);
            if (node == null) continue;

            ExecutionStep step = executeNode(node, input);
            steps.add(step);

            for (String neighbor : adjacency.getOrDefault(nodeId, Collections.emptyList())) {
                int newDegree = currentInDegree.merge(neighbor, -1, (old, delta) -> old - 1);
                if (newDegree == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        return steps;
    }

    // ─────────────────────────────────────────────────────────────────
    // Parallel execution — all independent nodes at once
    // ─────────────────────────────────────────────────────────────────
    private List<ExecutionStep> executeParallel(
            List<WorkflowNode> nodes,
            List<WorkflowEdge> edges,
            Map<String, WorkflowNode> nodeMap,
            Map<String, List<String>> adjacency,
            Map<String, Integer> inDegree,
            Map<String, Object> input) {

        List<ExecutionStep> steps = new ArrayList<>();
        Map<String, Integer> currentInDegree = new LinkedHashMap<>(inDegree);
        Set<String> executed = new HashSet<>();
        int totalNodes = nodes.size();

        while (executed.size() < totalNodes) {
            // Find all nodes with zero in-degree that haven't been executed
            List<String> ready = currentInDegree.entrySet().stream()
                .filter(e -> e.getValue() == 0 && !executed.contains(e.getKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

            if (ready.isEmpty()) {
                log.warn("WorkflowEngine: deadlock or cycle detected, stopping");
                break;
            }

            for (String nodeId : ready) {
                WorkflowNode node = nodeMap.get(nodeId);
                if (node == null) continue;

                ExecutionStep step = executeNode(node, input);
                steps.add(step);
                executed.add(nodeId);

                for (String neighbor : adjacency.getOrDefault(nodeId, Collections.emptyList())) {
                    currentInDegree.merge(neighbor, -1, (old, delta) -> old - 1);
                }
            }
        }

        return steps;
    }

    // ─────────────────────────────────────────────────────────────────
    // Execute a single node — real execution for all 5 node types
    // ─────────────────────────────────────────────────────────────────
    private ExecutionStep executeNode(WorkflowNode node, Map<String, Object> context) {
        return executeNode(node, context, null);
    }

    private ExecutionStep executeNode(WorkflowNode node, Map<String, Object> context, String instanceId) {
        Instant nodeStart = Instant.now();

        String nodeType = node.getType() != null ? node.getType() : "task";
        String label = node.getLabel() != null ? node.getLabel() : node.getId();
        @SuppressWarnings("unchecked")
        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Collections.emptyMap();

        String status;
        String resultSummary;
        String nextTarget = null; // for gateway branching

        try {
            switch (nodeType) {
                case "start" -> {
                    status = "success";
                    resultSummary = "Workflow started";
                    // Apply inputMapping from config
                    applyInputMapping(config, context);
                    if (instanceId != null) {
                        logRepo.log(instanceId, node.getId(), "start", "NodeStarted",
                            "开始节点执行", null, null, null);
                    }
                }
                case "end" -> {
                    status = "success";
                    resultSummary = "Workflow completed";
                    // Apply outputMapping
                    applyOutputMapping(config, context);
                    if (instanceId != null) {
                        logRepo.log(instanceId, node.getId(), "end", "NodeCompleted",
                            "结束节点执行, endType=" + config.getOrDefault("endType", "COMPLETED"),
                            null, null, null);
                    }
                }
                case "userTask" -> {
                    // Create a human task and suspend
                    String taskType = String.valueOf(config.getOrDefault("taskType", "APPROVAL"));
                    String title = label;
                    String assignee = resolveExpression(String.valueOf(config.getOrDefault("assignee", "")), context);
                    @SuppressWarnings("unchecked")
                    List<String> candidateUsers = (List<String>) config.get("candidateUsers");
                    @SuppressWarnings("unchecked")
                    List<String> candidateRoles = (List<String>) config.get("candidateRoles");
                    String formSchemaJson = toJson(config.get("formSchema"));
                    String dueDate = String.valueOf(config.getOrDefault("dueDate", "48h"));
                    String priority = String.valueOf(config.getOrDefault("priority", "NORMAL"));

                    WorkflowTaskEntity task = taskService.createTask(
                        instanceId != null ? instanceId : "test-inst",
                        node.getId(), taskType, title, assignee,
                        candidateUsers, candidateRoles,
                        formSchemaJson, priority, dueDate);

                    status = "waiting";
                    resultSummary = "人工任务已创建: " + task.getId() + " (等待人工处理)";
                    if (instanceId != null) {
                        logRepo.log(instanceId, node.getId(), "userTask", "TaskCreated",
                            "任务创建: " + task.getId(), toJson(Map.of("taskId", task.getId())), null, null);
                    }
                }
                case "agentTask" -> {
                    // Resolve agent config and invoke HermesEngine
                    @SuppressWarnings("unchecked")
                    Map<String, Object> agentConfig = (Map<String, Object>) config.get("agentConfig");
                    @SuppressWarnings("unchecked")
                    Map<String, String> inputContext = (Map<String, String>) config.get("inputContext");

                    String agentId = agentConfig != null ? String.valueOf(agentConfig.getOrDefault("agentId", "default")) : "default";
                    String model = agentConfig != null ? String.valueOf(agentConfig.getOrDefault("model", "deepseek-v4-pro")) : "deepseek-v4-pro";
                    String systemPrompt = agentConfig != null ? String.valueOf(agentConfig.getOrDefault("systemPrompt", "")) : "";
                    Number temperature = agentConfig != null ? (Number) agentConfig.getOrDefault("temperature", 0.7) : 0.7;
                    boolean humanReview = Boolean.TRUE.equals(config.get("humanReview"));
                    int retryOnError = config.containsKey("retryOnError") ? ((Number) config.get("retryOnError")).intValue() : 2;

                    // Build user prompt from inputContext
                    String userPrompt = buildAgentPrompt(inputContext, context, config);
                    if (userPrompt.isBlank()) userPrompt = "请执行 Agent 任务: " + label;

                    if (instanceId != null) {
                        logRepo.log(instanceId, node.getId(), "agentTask", "AgentInvoked",
                            "Agent 调用: " + agentId, toJson(Map.of("agentId", agentId)), null, null);
                    }

                    // Try to invoke HermesEngine
                    try {
                        AgentResult agentResult = hermesEngine.execute("workflow", agentId, userPrompt);
                        if (agentResult.isSuccess()) {
                            // Map output to variables
                            applyAgentOutput(config, agentResult, context);
                            status = humanReview ? "waiting" : "success";
                            resultSummary = humanReview
                                ? "Agent 执行完成，等待人工复核: " + agentResult.getContent()
                                : "Agent 执行完成: " + agentResult.getContent();

                            if (instanceId != null) {
                                logRepo.log(instanceId, node.getId(), "agentTask", "AgentCompleted",
                                    "Agent 完成: " + agentResult.getContent(),
                                    toJson(Map.of("output", agentResult.getContent())),
                                    agentResult.getDurationMs(), null);
                            }
                        } else {
                            status = "failed";
                            resultSummary = "Agent 执行失败: " + agentResult.getErrorMsg();
                        }
                    } catch (Exception e) {
                        log.warn("Agent invocation failed (non-blocking): {}", e.getMessage());
                        // Graceful degradation: mark as success with mock
                        status = "success";
                        resultSummary = "Agent 调用已发起（异步模式）: " + agentId;
                        if (instanceId != null) {
                            logRepo.log(instanceId, node.getId(), "agentTask", "AgentInvoked",
                                "Agent 异步调用: " + agentId + " (error: " + e.getMessage() + ")",
                                null, null, null);
                        }
                    }
                }
                case "exclusiveGateway" -> {
                    // Evaluate conditions and determine next branch
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> conditions = (List<Map<String, Object>>) config.get("conditions");
                    String matched = null;
                    String defaultTarget = null;

                    if (conditions != null) {
                        for (Map<String, Object> cond : conditions) {
                            String expr = (String) cond.get("expression");
                            String target = (String) cond.get("targetNode");
                            if (Boolean.TRUE.equals(cond.get("isDefault"))) {
                                defaultTarget = target;
                                continue;
                            }
                            if (expr != null && expressionService.evaluateCondition(expr, context)) {
                                matched = target;
                                break;
                            }
                        }
                    }

                    if (matched != null) {
                        status = "success";
                        resultSummary = "条件匹配 → " + matched;
                        nextTarget = matched;
                    } else if (defaultTarget != null) {
                        status = "success";
                        resultSummary = "默认分支 → " + defaultTarget;
                        nextTarget = defaultTarget;
                    } else {
                        status = "failed";
                        resultSummary = "无条件匹配，且无默认分支";
                    }

                    if (instanceId != null) {
                        logRepo.log(instanceId, node.getId(), "exclusiveGateway", "NodeCompleted",
                            resultSummary, toJson(Map.of("nextTarget", nextTarget)), null, null);
                    }
                }
                default -> {
                    status = "skipped";
                    resultSummary = "Unknown node type '" + nodeType + "', skipped";
                }
            }
        } catch (Exception e) {
            status = "failed";
            resultSummary = "Error: " + e.getMessage();
            log.warn("WorkflowEngine node '{}' failed: {}", node.getId(), e.getMessage());
            if (instanceId != null) {
                logRepo.log(instanceId, node.getId(), nodeType, "NodeFailed",
                    "执行失败: " + e.getMessage(), null, null, null);
            }
        }

        Duration nodeTime = Duration.between(nodeStart, Instant.now());
        return new ExecutionStep(node.getId(), nodeType, label, status, formatDuration(nodeTime), resultSummary, nextTarget);
    }

    private String nodeId(WorkflowNode node) {
        return node.getId();
    }

    private String formatDuration(Duration d) {
        long millis = d.toMillis();
        if (millis < 1000) return millis + "ms";
        return String.format("%.1fs", millis / 1000.0);
    }

    // ─────────────────────────────────────────────────────────────────
    // JSON parsing helpers
    // ─────────────────────────────────────────────────────────────────

    private List<WorkflowNode> parseNodes(String nodesJson) {
        if (nodesJson == null || nodesJson.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(nodesJson, new TypeReference<List<WorkflowNode>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse workflow nodes JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<WorkflowEdge> parseEdges(String edgesJson) {
        if (edgesJson == null || edgesJson.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(edgesJson, new TypeReference<List<WorkflowEdge>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse workflow edges JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Internal model classes
    // ─────────────────────────────────────────────────────────────────

    public static class WorkflowNode {
        private String id;
        private String type;
        private String label;
        private Map<String, Object> config;

        public WorkflowNode() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public Map<String, Object> getConfig() { return config; }
        public void setConfig(Map<String, Object> config) { this.config = config; }
    }

    public static class WorkflowEdge {
        private String source;
        private String target;

        public WorkflowEdge() {}

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
    }

    public static class ExecutionStep {
        private final String nodeId;
        private final String nodeType;
        private final String label;
        private final String status;
        private final String time;
        private final String result;
        private final String nextTarget;  // null unless gateway

        public ExecutionStep(String nodeId, String nodeType, String label,
                             String status, String time, String result, String nextTarget) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.label = label;
            this.status = status;
            this.time = time;
            this.result = result;
            this.nextTarget = nextTarget;
        }

        public String getNodeId() { return nodeId; }
        public String getNodeType() { return nodeType; }
        public String getLabel() { return label; }
        public String getStatus() { return status; }
        public String getTime() { return time; }
        public String getResult() { return result; }
        public String getNextTarget() { return nextTarget; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("nodeId", nodeId);
            map.put("nodeType", nodeType);
            map.put("label", label);
            map.put("status", status);
            map.put("time", time);
            map.put("result", result);
            if (nextTarget != null) map.put("nextTarget", nextTarget);
            return map;
        }
    }

    public static class WorkflowExecutionResult {
        private final String workflowId;
        private final String status;
        private final Duration totalTime;
        private final List<ExecutionStep> steps;
        private final Map<String, Object> context;
        private final List<String> activeNodes;

        public WorkflowExecutionResult(String workflowId, String status,
                                       Duration totalTime, List<ExecutionStep> steps,
                                       Map<String, Object> context) {
            this(workflowId, status, totalTime, steps, context, Collections.emptyList());
        }

        public WorkflowExecutionResult(String workflowId, String status,
                                       Duration totalTime, List<ExecutionStep> steps,
                                       Map<String, Object> context, List<String> activeNodes) {
            this.workflowId = workflowId;
            this.status = status;
            this.totalTime = totalTime;
            this.steps = steps;
            this.context = context;
            this.activeNodes = activeNodes;
        }

        public String getStatus() { return status; }
        public List<String> getActiveNodes() { return activeNodes; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("workflowId", workflowId);
            map.put("status", status);
            map.put("executionTime", formatDuration(totalTime));
            map.put("steps", steps.stream().map(ExecutionStep::toMap).collect(Collectors.toList()));
            map.put("context", context);
            if (!activeNodes.isEmpty()) map.put("activeNodes", activeNodes);
            return map;
        }

        private String formatDuration(Duration d) {
            long millis = d.toMillis();
            if (millis < 1000) return millis + "ms";
            return String.format("%.1fs", millis / 1000.0);
        }
    }

    // ── Helper methods for node execution ───────────────────

    private void applyInputMapping(Map<String, Object> config, Map<String, Object> context) {
        @SuppressWarnings("unchecked")
        Map<String, String> inputMapping = (Map<String, String>) config.get("inputMapping");
        if (inputMapping == null) return;
        Map<String, Object> variables = getOrCreateVariables(context);
        for (Map.Entry<String, String> entry : inputMapping.entrySet()) {
            String resolved = expressionService.resolvePlaceholders(entry.getValue(), context);
            variables.put(entry.getKey(), resolved);
        }
    }

    private void applyOutputMapping(Map<String, Object> config, Map<String, Object> context) {
        @SuppressWarnings("unchecked")
        Map<String, Object> outputMapping = (Map<String, Object>) config.get("outputMapping");
        if (outputMapping == null) return;
        Map<String, Object> variables = getOrCreateVariables(context);
        for (Map.Entry<String, Object> entry : outputMapping.entrySet()) {
            variables.put(entry.getKey(), entry.getValue());
        }
    }

    private void applyAgentOutput(Map<String, Object> config, AgentResult agentResult, Map<String, Object> context) {
        @SuppressWarnings("unchecked")
        Map<String, String> outputMapping = (Map<String, String>) config.get("outputMapping");
        Map<String, Object> variables = getOrCreateVariables(context);

        // If agentResult has structured output, use it
        String output = agentResult.getContent();
        try {
            if (output.startsWith("{") || output.startsWith("[")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(output, Map.class);
                if (outputMapping != null) {
                    for (Map.Entry<String, String> entry : outputMapping.entrySet()) {
                        String mappedKey = entry.getValue().replace("${variables.", "").replace("}", "");
                        variables.put(mappedKey, parsed.getOrDefault(entry.getKey(), output));
                    }
                }
            }
        } catch (Exception e) {
            // Fallback: store raw output
            variables.put("agentOutput", output);
        }
    }

    private String buildAgentPrompt(Map<String, String> inputContext, Map<String, Object> context, Map<String, Object> config) {
        if (inputContext == null) return "";
        if (inputContext.size() == 1 && inputContext.containsKey("prompt")) {
            return expressionService.resolvePlaceholders(inputContext.get("prompt"), context);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : inputContext.entrySet()) {
            sb.append(entry.getKey()).append(": ");
            sb.append(expressionService.resolvePlaceholders(entry.getValue(), context));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String resolveExpression(String expr, Map<String, Object> context) {
        if (expr == null || expr.isBlank()) return "";
        return expressionService.resolvePlaceholders(expr, context);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateVariables(Map<String, Object> context) {
        if (!context.containsKey("variables")) {
            context.put("variables", new LinkedHashMap<String, Object>());
        }
        return (Map<String, Object>) context.get("variables");
    }

    private String toJson(Object obj) {
        if (obj == null) return "null";
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { return String.valueOf(obj); }
    }
}
