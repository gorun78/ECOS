package com.chinacreator.gzcm.engine.ai.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.ai.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.engine.ai.agent.mesh.repository.AgentRegistryRepository;
import com.chinacreator.gzcm.engine.ai.agent.vo.PipelineNodeTraceVO;
import com.chinacreator.gzcm.runtime.llm.session.AgentSession;

/**
 * AI工作台统一服务入口 — Agent创建/测试/对比/流水线/编排。
 *
 * <h3>核心方法：</h3>
 * <ul>
 *   <li>{@link #createAndTest(Map, String)} — 创建Agent并测试</li>
 *   <li>{@link #compare(String, String, List)} — 对比两个Agent</li>
 *   <li>{@link #startPipeline(String, Map)} — 启动流水线</li>
 *   <li>{@link #getPipelineStatus(String)} — 查询流水线状态</li>
 *   <li>{@link #orchestrate(String, String)} — 多Agent编排</li>
 * </ul>
 *
 * <h3>注入点：</h3>
 * <ul>
 *   <li>{@link AgentLoopService} — Agent推理循环（required）</li>
 *   <li>{@link AgentTemplateService} — 模板实例化</li>
 *   <li>{@link AgentRegistryRepository} — Agent持久化</li>
 * </ul>
 */
@Service
public class AgentStudioService {

    private static final Logger log = LoggerFactory.getLogger(AgentStudioService.class);

    @Autowired(required = false)
    private AgentLoopService agentLoopService;

    @Autowired(required = false)
    private AgentTemplateService templateService;

    @Autowired(required = false)
    private AgentRegistryRepository agentRepo;

    /** 流水线执行记录 — 内存存储（后续迁移至 PG） */
    private final Map<String, PipelineExecution> pipelineStore = new ConcurrentHashMap<>();

    /** 流水线节点伪执行线程池（mock 演示用，非生产负载，daemon 线程不阻塞 JVM 退出） */
    private static final ExecutorService PIPELINE_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "pipeline-mock-executor");
                t.setDaemon(true);
                return t;
            });

    // ═══════════════ createAndTest ═══════════════════

    /**
     * 创建 Agent 并发送测试消息。
     *
     * @param agentDef    Agent 定义 Map（name, systemPrompt, model, temperature 等）
     * @param testMessage 测试消息文本
     * @return AgentTestResult 包含响应内容、轮次、token 消耗等
     */
    public AgentTestResult createAndTest(Map<String, Object> agentDef, String testMessage) {
        long startTime = System.currentTimeMillis();

        if (agentLoopService == null) {
            return AgentTestResult.error(null, "AgentLoopService 未就绪", 0, System.currentTimeMillis() - startTime);
        }

        try {
            String agentId = "aip_test_" + UUID.randomUUID().toString().substring(0, 8);

            // 构建 AgentLoopConfig
            AgentLoopConfig config = new AgentLoopConfig();
            config.setSystemPrompt(String.valueOf(agentDef.getOrDefault("systemPrompt", "")));
            config.setModel(String.valueOf(agentDef.getOrDefault("model", "deepseek-chat")));
            config.setTemperature(getDouble(agentDef, "temperature", 0.7));
            config.setMaxTokens(getInt(agentDef, "maxTokens", 4096));

            // 构建运行时 AgentSession
            AgentSession session = new AgentSession();
            session.setSessionId("sess-" + UUID.randomUUID().toString().replace("-", ""));
            session.setSubsystem("ai-engine");
            session.setProfileName(agentId);
            session.setSystemPrompt(config.getSystemPrompt());
            session.setStatus("active");

            // 执行推理循环
            AgentLoopResult loopResult = agentLoopService.run(config, testMessage, session);
            long duration = System.currentTimeMillis() - startTime;

            if (loopResult.isSuccess()) {
                return AgentTestResult.success(
                        agentId,
                        loopResult.getContent(),
                        loopResult.getTurns(),
                        loopResult.getTotalTokens(),
                        duration,
                        loopResult.getToolCalls()
                );
            } else {
                return AgentTestResult.error(
                        agentId,
                        loopResult.getErrorMsg() != null ? loopResult.getErrorMsg() : "Agent 推理未成功完成",
                        loopResult.getTotalTokens(),
                        duration
                );
            }
        } catch (Exception e) {
            log.error("[AgentStudio] createAndTest failed", e);
            return AgentTestResult.error(null, "Agent测试异常: " + e.getMessage(), 0,
                    System.currentTimeMillis() - startTime);
        }
    }

    // ═══════════════ compare ═══════════════════

    /**
     * 对比两个 Agent 在相同测试消息集上的表现。
     *
     * @param agentIdA     Agent A 的 ID
     * @param agentIdB     Agent B 的 ID
     * @param testMessages 测试消息列表
     * @return AgentCompareResult 包含双方结果和对比分析
     */
    public AgentCompareResult compare(String agentIdA, String agentIdB, List<String> testMessages) {
        if (agentRepo == null) {
            return AgentCompareResult.error("AgentRegistryRepository 未就绪");
        }
        if (agentLoopService == null) {
            return AgentCompareResult.error("AgentLoopService 未就绪");
        }

        AgentRegistryEntity agentA = agentRepo.findById(agentIdA);
        AgentRegistryEntity agentB = agentRepo.findById(agentIdB);

        if (agentA == null) {
            return AgentCompareResult.error("Agent A (" + agentIdA + ") 不存在");
        }
        if (agentB == null) {
            return AgentCompareResult.error("Agent B (" + agentIdB + ") 不存在");
        }

        // 选择第一条测试消息执行
        String message = (testMessages != null && !testMessages.isEmpty())
                ? testMessages.get(0) : "请介绍一下你自己";

        // 测试 Agent A
        AgentTestResult resultA = runAgentTest(agentA, message);
        // 测试 Agent B
        AgentTestResult resultB = runAgentTest(agentB, message);

        // 对比分析
        String comparison = buildComparison(resultA, resultB);
        String winner = determineWinner(resultA, resultB);

        log.info("[AgentStudio] Compare {} vs {}: winner={}", agentIdA, agentIdB, winner);

        return AgentCompareResult.of(resultA, resultB, comparison, winner);
    }

    /**
     * 对单个 Agent 执行测试。
     */
    private AgentTestResult runAgentTest(AgentRegistryEntity agent, String message) {
        long startTime = System.currentTimeMillis();
        try {
            AgentLoopConfig config = new AgentLoopConfig();
            config.setSystemPrompt(agent.getSystemPrompt());
            config.setModel(agent.getModel());
            config.setMaxTokens(4096);
            config.setTemperature(0.7);

            AgentSession session = new AgentSession();
            session.setSessionId("sess-" + UUID.randomUUID().toString().replace("-", ""));
            session.setSubsystem("ai-engine");
            session.setProfileName(agent.getId());
            session.setSystemPrompt(config.getSystemPrompt());
            session.setStatus("active");

            AgentLoopResult loopResult = agentLoopService.run(config, message, session);
            long duration = System.currentTimeMillis() - startTime;

            if (loopResult.isSuccess()) {
                return AgentTestResult.success(
                        agent.getId(),
                        loopResult.getContent(),
                        loopResult.getTurns(),
                        loopResult.getTotalTokens(),
                        duration,
                        loopResult.getToolCalls()
                );
            } else {
                return AgentTestResult.error(
                        agent.getId(),
                        loopResult.getErrorMsg() != null ? loopResult.getErrorMsg() : "推理失败",
                        loopResult.getTotalTokens(),
                        duration
                );
            }
        } catch (Exception e) {
            log.error("[AgentStudio] runAgentTest failed for agent={}", agent.getId(), e);
            return AgentTestResult.error(agent.getId(), "测试异常: " + e.getMessage(), 0,
                    System.currentTimeMillis() - startTime);
        }
    }

    private String buildComparison(AgentTestResult a, AgentTestResult b) {
        StringBuilder sb = new StringBuilder();
        sb.append("Agent A (").append(a.getAgentId()).append("): ")
                .append(a.isSuccess() ? "成功" : "失败")
                .append(", ").append(a.getTurns()).append("轮, ")
                .append(a.getTokens()).append("tokens, ")
                .append(a.getDurationMs()).append("ms. ");
        sb.append("Agent B (").append(b.getAgentId()).append("): ")
                .append(b.isSuccess() ? "成功" : "失败")
                .append(", ").append(b.getTurns()).append("轮, ")
                .append(b.getTokens()).append("tokens, ")
                .append(b.getDurationMs()).append("ms.");
        return sb.toString();
    }

    private String determineWinner(AgentTestResult a, AgentTestResult b) {
        if (a.isSuccess() && !b.isSuccess()) return a.getAgentId();
        if (!a.isSuccess() && b.isSuccess()) return b.getAgentId();
        // Both succeed or both fail — prefer fewer turns + tokens
        int scoreA = a.getTurns() * 10 + a.getTokens() / 100;
        int scoreB = b.getTurns() * 10 + b.getTokens() / 100;
        if (scoreA < scoreB) return a.getAgentId();
        if (scoreB < scoreA) return b.getAgentId();
        return "tie";
    }

    // ═══════════════ startPipeline ═══════════════════

    /**
     * 启动流水线执行（mock 拓扑伪执行）。
     *
     * <p>按拓扑序创建 per-node {@link PipelineNodeTraceVO}，逐节点串行伪执行：
     * 节点状态 PENDING → RUNNING → SUCCESS/FAILED，全部完成后置 overall status。
     * 失败注入规则：拓扑序 index % 7 == 3 的节点标记 FAILED（模拟确定性失败）。</p>
     *
     * <p>节点 ID 从 {@code params} 中的 {@code nodeIds} 列表获取拓扑序，
     * 无 {@code nodeIds} 时创建单个 {@code trigger-0} 节点（兼容旧调用方）。</p>
     *
     * @param pipelineId 流水线ID
     * @param params     执行参数（可含 nodeIds: List&lt;String&gt;）
     * @return PipelineExecution 包含执行ID和初始状态
     */
    public PipelineExecution startPipeline(String pipelineId, Map<String, Object> params) {
        String executionId = "exec_" + UUID.randomUUID().toString().substring(0, 8);

        PipelineExecution exec = new PipelineExecution();
        exec.setExecutionId(executionId);
        exec.setPipelineId(pipelineId);
        exec.setStatus("running");
        exec.setStartedAt(System.currentTimeMillis());
        pipelineStore.put(executionId, exec);

        // 从 params 解析节点列表（前端 canvas 拓扑序；为空时创建单 trigger 节点保底）
        List<String> nodeIds = new ArrayList<>();
        if (params != null) {
            Object nodeIdsObj = params.get("nodeIds");
            if (nodeIdsObj instanceof List<?> rawNodeIds && !rawNodeIds.isEmpty()) {
                for (Object item : rawNodeIds) {
                    if (item instanceof String s && !s.isBlank()) {
                        nodeIds.add(s);
                    }
                }
            }
        }
        List<String> effectiveNodeIds = nodeIds.isEmpty()
                ? List.of("trigger-0")
                : Collections.unmodifiableList(nodeIds);

        // 初始化 nodeTraces（LinkedHashMap 保拓扑序）
        for (String nodeId : effectiveNodeIds) {
            PipelineNodeTraceVO trace = new PipelineNodeTraceVO();
            trace.setNodeId(nodeId);
            trace.setNodeName(nodeId);
            trace.setStatus("pending");
            exec.getNodeTraces().put(nodeId, trace);
        }
        exec.setNodeIds(new ArrayList<>(effectiveNodeIds));

        log.info("[AgentStudio] Pipeline execution started (mock): {} for pipeline {}, nodes={}",
                executionId, pipelineId, effectiveNodeIds.size());

        // 异步伪执行：逐节点串行，CompletableFuture.runAsync 提交到 PIPELINE_EXECUTOR
        CompletableFuture.runAsync(() -> {
            try {
                int nodeCount = effectiveNodeIds.size();
                boolean hasFailure = false;

                for (int i = 0; i < nodeCount; i++) {
                    String nodeId = effectiveNodeIds.get(i);
                    PipelineNodeTraceVO trace = exec.getNodeTraces().get(nodeId);
                    if (trace == null) {
                        continue;
                    }

                    // PENDING → RUNNING
                    trace.setStatus("running");
                    long startEpoch = System.nanoTime();
                    trace.setStartedAt(LocalDateTime.now());

                    // mock 耗时：失败注入节点 index % 7 == 3 → FAILED；其余 SUCCESS
                    boolean shouldFail = (i % 7 == 3);
                    long sleepMs = shouldFail ? 100L : (150L + new Random().nextInt(151));
                    Thread.sleep(sleepMs);

                    long elapsedMs = (System.nanoTime() - startEpoch) / 1_000_000L;
                    LocalDateTime now = LocalDateTime.now();
                    trace.setCompletedAt(now);
                    trace.setLatencyMs(elapsedMs);

                    if (shouldFail) {
                        trace.setStatus("failed");
                        trace.setErrorMessage("mock failure for node " + nodeId + " (no real backend)");
                        hasFailure = true;
                        log.debug("[AgentStudio] Node {} FAILED (mock injection, index={})", nodeId, i);
                    } else {
                        trace.setStatus("success");
                        trace.setInputJson("{\"mock\":true,\"nodeId\":\"" + nodeId + "\"}");
                        trace.setOutputJson("{\"status\":\"ok\",\"latencyMs\":" + elapsedMs + "}");
                    }

                    // 前序节点失败 → 后继节点全部 skipped
                    if (hasFailure) {
                        for (int j = i + 1; j < nodeCount; j++) {
                            String skipId = effectiveNodeIds.get(j);
                            PipelineNodeTraceVO skipTrace = exec.getNodeTraces().get(skipId);
                            if (skipTrace != null && "pending".equals(skipTrace.getStatus())) {
                                skipTrace.setStatus("skipped");
                                skipTrace.setStartedAt(LocalDateTime.now());
                                skipTrace.setCompletedAt(LocalDateTime.now());
                                skipTrace.setLatencyMs(0L);
                            }
                        }
                        break;
                    }
                }

                // 汇总 overall status
                exec.setCompletedAt(System.currentTimeMillis());
                if (hasFailure) {
                    exec.setStatus("failed");
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("message", "Pipeline completed with node failure (mock)");
                    result.put("pipelineId", pipelineId);
                    exec.setResult(result);
                } else {
                    exec.setStatus("completed");
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("message", "Pipeline executed successfully");
                    result.put("pipelineId", pipelineId);
                    result.put("params", params);
                    exec.setResult(result);
                }
                log.info("[AgentStudio] Pipeline execution completed: {} status={}",
                        executionId, exec.getStatus());

            } catch (InterruptedException e) {
                exec.setStatus("failed");
                exec.setCompletedAt(System.currentTimeMillis());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("message", "Pipeline execution interrupted");
                exec.setResult(result);
                Thread.currentThread().interrupt();
                log.error("[AgentStudio] Pipeline execution interrupted: {}", executionId, e);
            }
        }, PIPELINE_EXECUTOR);

        return exec;
    }

    /**
     * 查询流水线执行状态。
     *
     * @param executionId 执行ID
     * @return PipelineExecution 或 null（不存在时）
     */
    public PipelineExecution getPipelineStatus(String executionId) {
        PipelineExecution exec = pipelineStore.get(executionId);
        if (exec == null) {
            log.warn("[AgentStudio] Pipeline execution {} not found", executionId);
        }
        return exec;
    }

    // ═══════════════ orchestrate ═══════════════════

    /**
     * 多 Agent 编排 — 根据用户消息智能路由到合适的 Agent。
     *
     * @param userMessage 用户消息
     * @param sessionId   会话ID（可选，不传则自动创建）
     * @return OrchestrationResult 包含调用链和最终回复
     */
    public OrchestrationResult orchestrate(String userMessage, String sessionId) {
        if (agentLoopService == null) {
            return OrchestrationResult.error(sessionId, "AgentLoopService 未就绪");
        }

        long startTime = System.currentTimeMillis();
        String effectiveSessionId = (sessionId != null && !sessionId.isBlank())
                ? sessionId
                : "sess-" + UUID.randomUUID().toString().replace("-", "");

        try {
            List<Map<String, Object>> agentCalls = new ArrayList<>();

            // 构建默认编排配置
            AgentLoopConfig config = new AgentLoopConfig();
            config.setSystemPrompt("你是一个智能编排助手，能够根据用户需求协调多个专业Agent完成任务。");
            config.setModel("deepseek-chat");
            config.setTemperature(0.7);
            config.setMaxTokens(4096);

            AgentSession session = new AgentSession();
            session.setSessionId(effectiveSessionId);
            session.setSubsystem("ai-engine");
            session.setProfileName("orchestrator");
            session.setSystemPrompt(config.getSystemPrompt());
            session.setStatus("active");

            AgentLoopResult result = agentLoopService.run(config, userMessage, session);

            // 记录编排调用
            Map<String, Object> callRecord = new LinkedHashMap<>();
            callRecord.put("agentId", "orchestrator");
            callRecord.put("turns", result.getTurns());
            callRecord.put("tokens", result.getTotalTokens());
            callRecord.put("success", result.isSuccess());
            agentCalls.add(callRecord);

            long duration = System.currentTimeMillis() - startTime;

            return OrchestrationResult.of(
                    effectiveSessionId,
                    agentCalls,
                    result.isSuccess() ? result.getContent() : (result.getErrorMsg() != null ? result.getErrorMsg() : "编排未成功完成"),
                    result.getTotalTokens(),
                    duration
            );
        } catch (Exception e) {
            log.error("[AgentStudio] orchestrate failed", e);
            return OrchestrationResult.error(effectiveSessionId, "编排异常: " + e.getMessage());
        }
    }

    // ═══════════════ 辅助方法 ═══════════════════

    private Double getDouble(Map<String, Object> map, String key, Double defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return defaultValue;
    }

    private Integer getInt(Map<String, Object> map, String key, Integer defaultValue) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return defaultValue;
    }

    // ═══════════════════════════════════════════════════════════════
    //  结果类型 (inner static classes)
    // ═══════════════════════════════════════════════════════════════

    /** Agent 测试结果 */
    public static class AgentTestResult {
        private boolean success;
        private String agentId;
        private String response;
        private int turns;
        private int tokens;
        private long durationMs;
        private String errorMsg;
        private List<Map<String, Object>> toolCalls = new ArrayList<>();

        public static AgentTestResult success(String agentId, String response, int turns, int tokens,
                                               long durationMs, List<Map<String, Object>> toolCalls) {
            AgentTestResult r = new AgentTestResult();
            r.success = true;
            r.agentId = agentId;
            r.response = response;
            r.turns = turns;
            r.tokens = tokens;
            r.durationMs = durationMs;
            if (toolCalls != null) r.toolCalls = toolCalls;
            return r;
        }

        public static AgentTestResult error(String agentId, String errorMsg, int tokens, long durationMs) {
            AgentTestResult r = new AgentTestResult();
            r.success = false;
            r.agentId = agentId;
            r.errorMsg = errorMsg;
            r.tokens = tokens;
            r.durationMs = durationMs;
            return r;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
        public String getResponse() { return response; }
        public void setResponse(String response) { this.response = response; }
        public int getTurns() { return turns; }
        public void setTurns(int turns) { this.turns = turns; }
        public int getTokens() { return tokens; }
        public void setTokens(int tokens) { this.tokens = tokens; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public String getErrorMsg() { return errorMsg; }
        public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
        public List<Map<String, Object>> getToolCalls() { return toolCalls; }
        public void setToolCalls(List<Map<String, Object>> toolCalls) { this.toolCalls = toolCalls; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", success);
            m.put("agentId", agentId);
            m.put("response", response);
            m.put("turns", turns);
            m.put("tokens", tokens);
            m.put("durationMs", durationMs);
            if (errorMsg != null) m.put("errorMsg", errorMsg);
            if (toolCalls != null && !toolCalls.isEmpty()) m.put("toolCalls", toolCalls);
            return m;
        }
    }

    /** Agent 对比结果 */
    public static class AgentCompareResult {
        private AgentTestResult agentAResult;
        private AgentTestResult agentBResult;
        private String comparison;
        private String winner;
        private boolean success;
        private String errorMsg;

        public static AgentCompareResult of(AgentTestResult a, AgentTestResult b,
                                             String comparison, String winner) {
            AgentCompareResult r = new AgentCompareResult();
            r.success = true;
            r.agentAResult = a;
            r.agentBResult = b;
            r.comparison = comparison;
            r.winner = winner;
            return r;
        }

        public static AgentCompareResult error(String errorMsg) {
            AgentCompareResult r = new AgentCompareResult();
            r.success = false;
            r.errorMsg = errorMsg;
            return r;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public AgentTestResult getAgentAResult() { return agentAResult; }
        public void setAgentAResult(AgentTestResult agentAResult) { this.agentAResult = agentAResult; }
        public AgentTestResult getAgentBResult() { return agentBResult; }
        public void setAgentBResult(AgentTestResult agentBResult) { this.agentBResult = agentBResult; }
        public String getComparison() { return comparison; }
        public void setComparison(String comparison) { this.comparison = comparison; }
        public String getWinner() { return winner; }
        public void setWinner(String winner) { this.winner = winner; }
        public String getErrorMsg() { return errorMsg; }
        public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", success);
            if (errorMsg != null) m.put("errorMsg", errorMsg);
            if (agentAResult != null) m.put("agentAResult", agentAResult.toMap());
            if (agentBResult != null) m.put("agentBResult", agentBResult.toMap());
            if (comparison != null) m.put("comparison", comparison);
            if (winner != null) m.put("winner", winner);
            return m;
        }
    }

    /** 流水线执行记录 */
    public static class PipelineExecution {
        private String executionId;
        private String pipelineId;
        private String status;
        private long startedAt;
        private Long completedAt;
        private Map<String, Object> result;
        /** per-node 执行轨迹（key=nodeId，LinkedHashMap 保拓扑序），新增字段，不影响旧契约 */
        private Map<String, PipelineNodeTraceVO> nodeTraces = new LinkedHashMap<>();
        /** 节点 ID 列表（拓扑序），新增字段 */
        private List<String> nodeIds;

        public String getExecutionId() { return executionId; }
        public void setExecutionId(String executionId) { this.executionId = executionId; }
        public String getPipelineId() { return pipelineId; }
        public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getStartedAt() { return startedAt; }
        public void setStartedAt(long startedAt) { this.startedAt = startedAt; }
        public Long getCompletedAt() { return completedAt; }
        public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }
        public Map<String, Object> getResult() { return result; }
        public void setResult(Map<String, Object> result) { this.result = result; }
        public Map<String, PipelineNodeTraceVO> getNodeTraces() { return nodeTraces; }
        public void setNodeTraces(Map<String, PipelineNodeTraceVO> nodeTraces) { this.nodeTraces = nodeTraces; }
        public List<String> getNodeIds() { return nodeIds; }
        public void setNodeIds(List<String> nodeIds) { this.nodeIds = nodeIds; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("executionId", executionId);
            m.put("pipelineId", pipelineId);
            m.put("status", status);
            m.put("startedAt", Instant.ofEpochMilli(startedAt).toString());
            if (completedAt != null) m.put("completedAt", Instant.ofEpochMilli(completedAt).toString());
            if (result != null) m.put("result", result);
            // 新增：per-node 执行轨迹（LinkedHashMap 保拓扑序，toMap 转 List<Map>）
            if (nodeTraces != null && !nodeTraces.isEmpty()) {
                List<Map<String, Object>> traceList = new ArrayList<>();
                for (PipelineNodeTraceVO trace : nodeTraces.values()) {
                    if (trace != null) {
                        traceList.add(trace.toMap());
                    }
                }
                m.put("nodeTraces", traceList);
            }
            if (nodeIds != null) m.put("nodeIds", nodeIds);
            return m;
        }
    }

    /** 编排结果 */
    public static class OrchestrationResult {
        private boolean success;
        private String sessionId;
        private List<Map<String, Object>> agentCalls = new ArrayList<>();
        private String finalResponse;
        private int totalTokens;
        private long durationMs;
        private String errorMsg;

        public static OrchestrationResult of(String sessionId, List<Map<String, Object>> agentCalls,
                                              String finalResponse, int totalTokens, long durationMs) {
            OrchestrationResult r = new OrchestrationResult();
            r.success = true;
            r.sessionId = sessionId;
            r.agentCalls = agentCalls != null ? agentCalls : new ArrayList<>();
            r.finalResponse = finalResponse;
            r.totalTokens = totalTokens;
            r.durationMs = durationMs;
            return r;
        }

        public static OrchestrationResult error(String sessionId, String errorMsg) {
            OrchestrationResult r = new OrchestrationResult();
            r.success = false;
            r.sessionId = sessionId;
            r.errorMsg = errorMsg;
            return r;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public List<Map<String, Object>> getAgentCalls() { return agentCalls; }
        public void setAgentCalls(List<Map<String, Object>> agentCalls) { this.agentCalls = agentCalls; }
        public String getFinalResponse() { return finalResponse; }
        public void setFinalResponse(String finalResponse) { this.finalResponse = finalResponse; }
        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
        public long getDurationMs() { return durationMs; }
        public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
        public String getErrorMsg() { return errorMsg; }
        public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", success);
            m.put("sessionId", sessionId);
            m.put("agentCalls", agentCalls);
            m.put("finalResponse", finalResponse);
            m.put("totalTokens", totalTokens);
            m.put("durationMs", durationMs);
            if (errorMsg != null) m.put("errorMsg", errorMsg);
            return m;
        }
    }
}
