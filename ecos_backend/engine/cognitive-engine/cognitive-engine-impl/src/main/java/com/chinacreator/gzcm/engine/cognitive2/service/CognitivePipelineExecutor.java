package com.chinacreator.gzcm.engine.cognitive2.service;

import com.chinacreator.gzcm.engine.cognitive2.model.CognitivePipeline;
import com.chinacreator.gzcm.engine.cognitive2.model.CognitivePipelineNode;
import com.chinacreator.gzcm.engine.cognitive2.model.NodeType;
import com.chinacreator.gzcm.engine.cognitive2.service.EngineCapabilityRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;

/**
 * KAG 认知管线执行引擎 — 四件：验证 + 重试 + 并行 + 状态机。
 *
 * <p>对齐 Semantica pipeline/，翻译成 Java。复用 data-engine Kahn 拓扑思路，
 * 补重试/并行/验证三件。编排的是 KAG 已有能力，不新建引擎。</p>
 */
@Service
public class CognitivePipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(CognitivePipelineExecutor.class);

    private final EngineCapabilityRegistry capabilityRegistry;
    private final ExecutorService parallelPool = Executors.newFixedThreadPool(4);

    /** 执行记录存储（内存，后续可持久化） */
    private final Map<String, PipelineExecution> executions = new ConcurrentHashMap<>();

    public CognitivePipelineExecutor(EngineCapabilityRegistry capabilityRegistry) {
        this.capabilityRegistry = capabilityRegistry;
    }

    // ════════════════════════════════════════════════════
    //  执行入口
    // ════════════════════════════════════════════════════

    public PipelineExecution execute(CognitivePipeline pipeline) {
        String execId = UUID.randomUUID().toString().replace("-", "");
        PipelineExecution exec = new PipelineExecution(execId, pipeline.getId());
        executions.put(execId, exec);
        exec.log("Pipeline execution started: " + pipeline.getName());

        // 1. 验证（三验）
        List<String> errors = PipelineValidator.validate(pipeline);
        if (!errors.isEmpty()) {
            exec.fail("Validation failed: " + String.join("; ", errors));
            return exec;
        }
        exec.log("Validation passed");

        // 2. Kahn 拓扑排序
        List<String> topoOrder = kahnTopologicalSort(pipeline);
        exec.log("Topological order: " + topoOrder);

        // 3. 按拓扑层级并行执行
        Map<String, Map<String, Object>> nodeOutputs = new ConcurrentHashMap<>();
        Map<String, String> nodeStatuses = exec.getNodeStatuses();

        // 按入度分层，同层并行
        List<List<String>> layers = computeLayers(pipeline, topoOrder);
        for (int i = 0; i < layers.size(); i++) {
            List<String> layer = layers.get(i);
            exec.log("Executing layer " + i + ": " + layer + (layer.size() > 1 ? " (parallel)" : ""));

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (String nodeId : layer) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    CognitivePipelineNode node = findNode(pipeline, nodeId);
                    nodeStatuses.put(nodeId, "RUNNING");
                    exec.log("Node " + nodeId + " (" + node.getNodeType() + ") -> RUNNING");

                    // 汇聚上游输出作为 context
                    Map<String, Object> context = new HashMap<>();
                    if (node.getDependsOn() != null) {
                        for (String depId : node.getDependsOn()) {
                            Map<String, Object> depOutput = nodeOutputs.get(depId);
                            if (depOutput != null) context.put(depId, depOutput);
                        }
                    }

                    // 重试执行
                    boolean success = executeWithRetry(node, context, exec);
                    if (success) {
                        nodeStatuses.put(nodeId, "SUCCEEDED");
                        exec.log("Node " + nodeId + " -> SUCCEEDED");
                    } else {
                        nodeStatuses.put(nodeId, "FAILED");
                        exec.log("Node " + nodeId + " -> FAILED (all retries exhausted)");
                    }
                }, parallelPool);

                futures.add(future);
            }

            // 等待本层全部完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 检查本层是否有失败节点 → 整体失败
            boolean layerFailed = layer.stream().anyMatch(id -> "FAILED".equals(nodeStatuses.get(id)));
            if (layerFailed) {
                exec.fail("Pipeline failed at layer " + i);
                return exec;
            }

            // 收集本层输出（在 executeWithRetry 中已存入 nodeOutputs）
        }

        exec.succeed();
        exec.log("Pipeline execution completed successfully");
        return exec;
    }

    public PipelineExecution getExecution(String execId) {
        return executions.get(execId);
    }

    // ════════════════════════════════════════════════════
    //  重试逻辑
    // ════════════════════════════════════════════════════

    private boolean executeWithRetry(CognitivePipelineNode node, Map<String, Object> context,
                                     PipelineExecution exec) {
        RetryPolicy policy = new RetryPolicy();
        for (int attempt = 1; attempt <= policy.maxRetries; attempt++) {
            try {
                Map<String, Object> result = capabilityRegistry.executeNode(node, context);
                // 存储输出供下游消费
                exec.getNodeOutputs().put(node.getNodeId(), result);
                return true;
            } catch (Exception e) {
                exec.log("Node " + node.getNodeId() + " attempt " + attempt + " failed: " + e.getMessage());
                if (attempt < policy.maxRetries) {
                    try { Thread.sleep(policy.getDelay(attempt)); } catch (InterruptedException ie) { break; }
                }
            }
        }
        return false;
    }

    // ════════════════════════════════════════════════════
    //  Kahn 拓扑排序 + 分层
    // ════════════════════════════════════════════════════

    private List<String> kahnTopologicalSort(CognitivePipeline pipeline) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();

        for (CognitivePipelineNode node : pipeline.getNodes()) {
            inDegree.putIfAbsent(node.getNodeId(), 0);
            adj.putIfAbsent(node.getNodeId(), new ArrayList<>());
            if (node.getDependsOn() != null) {
                for (String dep : node.getDependsOn()) {
                    adj.computeIfAbsent(dep, k -> new ArrayList<>()).add(node.getNodeId());
                    inDegree.merge(node.getNodeId(), 1, Integer::sum);
                }
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);
            for (String next : adj.getOrDefault(current, Collections.emptyList())) {
                int newDeg = inDegree.merge(next, -1, Integer::sum);
                if (newDeg == 0) queue.add(next);
            }
        }
        return result;
    }

    private List<List<String>> computeLayers(CognitivePipeline pipeline, List<String> topoOrder) {
        Map<String, Integer> inDegree = new HashMap<>();
        for (CognitivePipelineNode node : pipeline.getNodes()) {
            inDegree.put(node.getNodeId(), node.getDependsOn() != null ? node.getDependsOn().size() : 0);
        }

        List<List<String>> layers = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        while (processed.size() < topoOrder.size()) {
            List<String> currentLayer = new ArrayList<>();
            for (String nodeId : topoOrder) {
                if (!processed.contains(nodeId) && inDegree.get(nodeId) == 0) {
                    currentLayer.add(nodeId);
                }
            }
            if (currentLayer.isEmpty()) break;

            for (String nodeId : currentLayer) {
                processed.add(nodeId);
                for (CognitivePipelineNode node : pipeline.getNodes()) {
                    if (node.getDependsOn() != null && node.getDependsOn().contains(nodeId)) {
                        inDegree.merge(node.getNodeId(), -1, Integer::sum);
                    }
                }
            }
            layers.add(currentLayer);
        }
        return layers;
    }

    private CognitivePipelineNode findNode(CognitivePipeline pipeline, String nodeId) {
        return pipeline.getNodes().stream()
            .filter(n -> n.getNodeId().equals(nodeId))
            .findFirst().orElseThrow(() -> new IllegalStateException("Node not found: " + nodeId));
    }

    // ════════════════════════════════════════════════════
    //  内部类：验证器
    // ════════════════════════════════════════════════════

    static class PipelineValidator {
        static final int MAX_NODES = 50;

        static List<String> validate(CognitivePipeline pipeline) {
            List<String> errors = new ArrayList<>();
            // 1. 结构验证
            if (pipeline.getNodes() == null || pipeline.getNodes().isEmpty()) {
                errors.add("Pipeline has no nodes");
                return errors;
            }
            Set<String> nodeIds = new HashSet<>();
            for (CognitivePipelineNode node : pipeline.getNodes()) {
                if (node.getNodeId() == null || node.getNodeId().isEmpty()) {
                    errors.add("Node with null/empty nodeId");
                }
                if (!nodeIds.add(node.getNodeId())) {
                    errors.add("Duplicate nodeId: " + node.getNodeId());
                }
                if (node.getDependsOn() != null) {
                    for (String dep : node.getDependsOn()) {
                        if (!nodeIds.contains(dep) && !hasNode(pipeline, dep)) {
                            errors.add("Node " + node.getNodeId() + " depends on non-existent: " + dep);
                        }
                    }
                }
            }
            // 2. 循环检测（Kahn）
            if (hasCycle(pipeline)) {
                errors.add("Pipeline has a cycle (circular dependency)");
            }
            // 3. 性能上限
            if (pipeline.getNodes().size() > MAX_NODES) {
                errors.add("Pipeline exceeds max nodes: " + pipeline.getNodes().size() + " > " + MAX_NODES);
            }
            return errors;
        }

        private static boolean hasNode(CognitivePipeline pipeline, String nodeId) {
            return pipeline.getNodes().stream().anyMatch(n -> n.getNodeId().equals(nodeId));
        }

        private static boolean hasCycle(CognitivePipeline pipeline) {
            Map<String, Integer> inDegree = new HashMap<>();
            for (CognitivePipelineNode node : pipeline.getNodes()) {
                inDegree.put(node.getNodeId(), node.getDependsOn() != null ? node.getDependsOn().size() : 0);
            }
            Queue<String> queue = new LinkedList<>();
            for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
                if (e.getValue() == 0) queue.add(e.getKey());
            }
            int processed = 0;
            while (!queue.isEmpty()) {
                String current = queue.poll();
                processed++;
                for (CognitivePipelineNode node : pipeline.getNodes()) {
                    if (node.getDependsOn() != null && node.getDependsOn().contains(current)) {
                        int newDeg = inDegree.merge(node.getNodeId(), -1, Integer::sum);
                        if (newDeg == 0) queue.add(node.getNodeId());
                    }
                }
            }
            return processed < pipeline.getNodes().size();
        }
    }

    // ════════════════════════════════════════════════════
    //  内部类：重试策略
    // ════════════════════════════════════════════════════

    static class RetryPolicy {
        final int maxRetries = 3;
        private final long retryDelayMs = 1000;

        boolean shouldRetry(int attempt) { return attempt < maxRetries; }
        long getDelay(int attempt) { return retryDelayMs * attempt; }
    }

    // ════════════════════════════════════════════════════
    //  内部类：执行记录（状态机 + 日志）
    // ════════════════════════════════════════════════════

    public static class PipelineExecution {
        private final String execId;
        private final String pipelineId;
        private volatile String status = "PENDING"; // PENDING→RUNNING→SUCCEEDED/FAILED
        private final Map<String, String> nodeStatuses = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> nodeOutputs = new ConcurrentHashMap<>();
        private final List<String> logs = Collections.synchronizedList(new ArrayList<>());
        private final Timestamp startTime = new Timestamp(System.currentTimeMillis());
        private volatile Timestamp endTime;

        public PipelineExecution(String execId, String pipelineId) {
            this.execId = execId;
            this.pipelineId = pipelineId;
        }

        void log(String msg) { logs.add("[" + new Timestamp(System.currentTimeMillis()) + "] " + msg); }

        void fail(String reason) { this.status = "FAILED"; this.endTime = new Timestamp(System.currentTimeMillis()); log("FAILED: " + reason); }
        void succeed() { this.status = "SUCCEEDED"; this.endTime = new Timestamp(System.currentTimeMillis()); }

        public String getExecId() { return execId; }
        public String getPipelineId() { return pipelineId; }
        public String getStatus() { return status; }
        public Map<String, String> getNodeStatuses() { return nodeStatuses; }
        public Map<String, Map<String, Object>> getNodeOutputs() { return nodeOutputs; }
        public List<String> getLogs() { return logs; }
        public Timestamp getStartTime() { return startTime; }
        public Timestamp getEndTime() { return endTime; }
    }
}
