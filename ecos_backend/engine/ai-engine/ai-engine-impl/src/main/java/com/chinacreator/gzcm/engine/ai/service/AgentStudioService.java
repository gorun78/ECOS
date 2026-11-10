package com.chinacreator.gzcm.engine.ai.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.agent.mesh.entity.AgentRegistryEntity;
import com.chinacreator.gzcm.engine.ai.agent.mesh.repository.AgentRegistryRepository;
import com.chinacreator.gzcm.engine.ai.agent.vo.PipelineNodeTraceVO;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.runtime.access.endpoint.ServiceEndpointResolver;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.chinacreator.gzcm.runtime.llm.session.AgentSession;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    /** P0-3：LLM 节点真实执行（统一走 llm-gateway，禁本类直连 provider） */
    @Autowired(required = false)
    private LLMGateway llmGateway;

    /** P0-3：tool 节点真实执行（含安全策略咽喉与超时预算） */
    @Autowired(required = false)
    private ToolExecutorService toolExecutorService;

    /** P0-3：ontology / approval 节点属主端点的基址解析（route-manifest 双模寻址） */
    @Autowired(required = false)
    private ObjectProvider<ServiceEndpointResolver> endpointProvider;

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 流水线执行记录 — 内存存储（后续迁移至 PG） */
    private final Map<String, PipelineExecution> pipelineStore = new ConcurrentHashMap<>();

    /** 流水线节点真实执行线程池（daemon；节点内调 LLM/工具/属主端点，均为真实 IO） */
    private static final ExecutorService PIPELINE_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(4, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "pipeline-executor");
                t.setDaemon(true);
                return t;
            });

    /** 属主端点调用模板（短超时：节点执行不得拖垮轮询感知） */
    private final RestTemplate pipelineRestTemplate = createPipelineRestTemplate();

    private static RestTemplate createPipelineRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(8000);
        return new RestTemplate(factory);
    }

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
            return AgentTestResult.error(null, "AgentLoopService 未就绪", 0,
                            System.currentTimeMillis() - startTime)
                    .withError(dependency("AgentLoopService"));
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
                ).withError(fromLoopResult(loopResult, "agent-studio"));
            }
        } catch (Exception e) {
            // F06-15：Service 层 catch 必按 F06-14 分类；原文只入日志（X-49），禁拼进对外消息
            AiError error = classify(e, ErrorCodeMapper.Context.llm("agent-studio"));
            log.error("[AgentStudio] createAndTest failed: class={} code={} traceId={} raw={}",
                    error.errorClass().name(), error.errorCode(), error.traceId(), error.rawReason(), e);
            return AgentTestResult.error(null, error.userMessage(), 0,
                            System.currentTimeMillis() - startTime)
                    .withError(error);
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
            return AgentCompareResult.error("AgentRegistryRepository 未就绪")
                    .withError(dependency("AgentRegistryRepository"));
        }
        if (agentLoopService == null) {
            return AgentCompareResult.error("AgentLoopService 未就绪")
                    .withError(dependency("AgentLoopService"));
        }

        AgentRegistryEntity agentA = agentRepo.findById(agentIdA);
        AgentRegistryEntity agentB = agentRepo.findById(agentIdB);

        if (agentA == null) {
            return AgentCompareResult.error("Agent A (" + agentIdA + ") 不存在")
                    .withError(AiError.of(AiErrorClass.NOT_FOUND, AiTraceSupport.currentTraceId()));
        }
        if (agentB == null) {
            return AgentCompareResult.error("Agent B (" + agentIdB + ") 不存在")
                    .withError(AiError.of(AiErrorClass.NOT_FOUND, AiTraceSupport.currentTraceId()));
        }

        // 选择第一条测试消息执行
        String message = (testMessages != null && !testMessages.isEmpty())
                ? testMessages.get(0) : "请介绍一下你自己";

        // 测试 Agent A
        AgentTestResult resultA = runAgentTest(agentA, message);
        // 测试 Agent B
        AgentTestResult resultB = runAgentTest(agentB, message);

        // 双方都失败 ⇒ 整体失败：禁 success=true 的伪对比（F06-15 禁 log.warn 后返回语义成功值）
        if (!resultA.isSuccess() && !resultB.isSuccess()) {
            AiError error = errorOf(resultA, resultB);
            return AgentCompareResult.error(error.userMessage()).withError(error);
        }

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
                ).withError(fromLoopResult(loopResult, "agent-studio-compare"));
            }
        } catch (Exception e) {
            // F06-15：捕后必分类，原文只入日志（X-49）
            AiError error = classify(e, ErrorCodeMapper.Context.llm("agent-studio-compare"));
            log.error("[AgentStudio] runAgentTest failed for agent={}: class={} code={} traceId={} raw={}",
                    agent.getId(), error.errorClass().name(), error.errorCode(), error.traceId(),
                    error.rawReason(), e);
            return AgentTestResult.error(agent.getId(), error.userMessage(), 0,
                            System.currentTimeMillis() - startTime)
                    .withError(error);
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
     * 启动流水线执行（P0-3 真实节点执行器，替代 mock 伪执行）。
     *
     * <p>节点图来源：{@code params.nodes}（前端画布拓扑序，元素 {id,type,label,config}）；
     * 无节点图时回退为单 {@code trigger-0} 节点（兼容旧调用方，仅做手动触发标记，不产生副作用）。
     * 逐节点串行执行，任一节点失败 → 后继 skipped、overall failed。
     * 耗时/失败均为真实结果，禁随机失败注入与伪耗时。</p>
     *
     * <p>六类节点真实执行：llm → llm-gateway；tool → ToolExecutorService（含安全咽喉）；
     * ontology → buszhi 属主 GET /api/v1/ontology/objects；approval → buszhi 属主
     * POST /api/v1/ontology/proposals（生成待人工审批提案）；condition → 表达式求值；
     * trigger → 手动触发标记（cron 注册归调度侧）。</p>
     *
     * @param pipelineId 流水线ID
     * @param params     执行参数（可含 nodes: List&lt;Map&gt; 或 nodeIds: List&lt;String&gt;）
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

        // 解析节点图（前端画布拓扑序；无图时回退单 trigger 节点，仅手动触发标记）
        List<PipelineNodeDef> defs = parseNodeDefs(params);
        if (defs.isEmpty()) {
            defs = List.of(new PipelineNodeDef("trigger-0", "trigger", "trigger-0", Map.of()));
        }
        for (PipelineNodeDef def : defs) {
            PipelineNodeTraceVO trace = new PipelineNodeTraceVO();
            trace.setNodeId(def.id);
            trace.setNodeName(def.label == null ? def.id : def.label);
            trace.setStatus("pending");
            exec.getNodeTraces().put(def.id, trace);
        }
        exec.setNodeIds(defs.stream().map(d -> d.id).toList());

        log.info("[AgentStudio] Pipeline execution started: {} for pipeline {}, nodes={}",
                executionId, pipelineId, defs.size());

        Map<String, Object> inputParams = new LinkedHashMap<>();
        if (params != null) {
            inputParams.putAll(params);
            inputParams.remove("nodes");
        }
        List<PipelineNodeDef> orderedDefs = defs;

        CompletableFuture.runAsync(() -> {
            try {
                boolean hasFailure = false;
                String previousOutput = null;
                int executed = 0;
                String failedNode = null;

                for (int i = 0; i < orderedDefs.size(); i++) {
                    PipelineNodeDef def = orderedDefs.get(i);
                    PipelineNodeTraceVO trace = exec.getNodeTraces().get(def.id);
                    if (trace == null) {
                        continue;
                    }

                    trace.setStatus("running");
                    long startNs = System.nanoTime();
                    trace.setStartedAt(LocalDateTime.now());
                    trace.setInputJson(toJson(def.config));

                    NodeOutcome outcome = executeNode(def, inputParams, previousOutput);
                    long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
                    trace.setCompletedAt(LocalDateTime.now());
                    trace.setLatencyMs(elapsedMs);
                    executed++;

                    if (outcome.success) {
                        trace.setStatus("success");
                        trace.setOutputJson(outcome.outputJson);
                        previousOutput = outcome.outputText;
                    } else {
                        trace.setStatus("failed");
                        trace.setErrorMessage(outcome.error);
                        trace.setOutputJson(outcome.outputJson);
                        hasFailure = true;
                        failedNode = def.id;
                        log.warn("[AgentStudio] Node {} FAILED: {}", def.id, outcome.error);
                        for (int j = i + 1; j < orderedDefs.size(); j++) {
                            PipelineNodeTraceVO skipTrace = exec.getNodeTraces().get(orderedDefs.get(j).id);
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

                exec.setCompletedAt(System.currentTimeMillis());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("pipelineId", pipelineId);
                result.put("executedNodes", executed);
                if (hasFailure) {
                    exec.setStatus("failed");
                    result.put("message", "Pipeline failed at node " + failedNode);
                    result.put("failedNode", failedNode);
                } else {
                    exec.setStatus("completed");
                    result.put("message", "Pipeline executed successfully (" + executed + " nodes)");
                }
                result.put("params", inputParams);
                exec.setResult(result);
                log.info("[AgentStudio] Pipeline execution completed: {} status={}",
                        executionId, exec.getStatus());
            } catch (RuntimeException e) {
                exec.setStatus("failed");
                exec.setCompletedAt(System.currentTimeMillis());
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("message", "Pipeline execution error: " + e.getMessage());
                exec.setResult(result);
                log.error("[AgentStudio] Pipeline execution failed: {}", executionId, e);
            }
        }, PIPELINE_EXECUTOR);

        return exec;
    }

    // ═══════════════ Pipeline 节点执行（P0-3） ═══════════════

    /** 节点定义（前端画布透传，解析容忍 type/config 在 data 内的形态）。 */
    private static final class PipelineNodeDef {
        final String id;
        final String type;
        final String label;
        final Map<String, Object> config;

        PipelineNodeDef(String id, String type, String label, Map<String, Object> config) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.config = config;
        }
    }

    /** 节点执行结果。 */
    private static final class NodeOutcome {
        final boolean success;
        final String outputJson;
        final String outputText;
        final String error;

        private NodeOutcome(boolean success, String outputJson, String outputText, String error) {
            this.success = success;
            this.outputJson = outputJson;
            this.outputText = outputText;
            this.error = error;
        }

        static NodeOutcome ok(Map<String, Object> output, String text) {
            return new NodeOutcome(true, toJson(output), text, null);
        }

        static NodeOutcome fail(String error) {
            return new NodeOutcome(false, "{}", null, error);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<PipelineNodeDef> parseNodeDefs(Map<String, Object> params) {
        List<PipelineNodeDef> defs = new ArrayList<>();
        Object nodesObj = params == null ? null : params.get("nodes");
        if (nodesObj instanceof List<?> nodes) {
            for (Object item : nodes) {
                if (!(item instanceof Map<?, ?> node)) {
                    continue;
                }
                Map<String, Object> nodeMap = (Map<String, Object>) node;
                String id = text(nodeMap.get("id"));
                String type = text(nodeMap.get("type"));
                String label = text(nodeMap.get("label"));
                Map<String, Object> config = new LinkedHashMap<>();
                Object cfg = nodeMap.get("config");
                if (cfg instanceof Map<?, ?> cm) {
                    config.putAll((Map<String, Object>) cm);
                }
                Object dataObj = nodeMap.get("data");
                if (dataObj instanceof Map<?, ?> dm) {
                    Map<String, Object> data = (Map<String, Object>) dm;
                    if (isBlank(type)) {
                        type = text(data.get("type"));
                    }
                    if (isBlank(label)) {
                        label = text(data.get("label"));
                    }
                    Object dataCfg = data.get("config");
                    if (dataCfg instanceof Map<?, ?> dcm) {
                        config.putAll((Map<String, Object>) dcm);
                    }
                }
                if (isBlank(id)) {
                    id = (isBlank(type) ? "node" : type) + "-" + defs.size();
                }
                if (isBlank(type)) {
                    type = "trigger";
                }
                defs.add(new PipelineNodeDef(id, type, isBlank(label) ? id : label, config));
            }
        }
        return defs;
    }

    private NodeOutcome executeNode(PipelineNodeDef def, Map<String, Object> params,
                                    String previousOutput) {
        try {
            return switch (def.type) {
                case "llm" -> llmNode(def, params, previousOutput);
                case "tool" -> toolNode(def, params);
                case "ontology" -> ontologyNode(def);
                case "approval" -> approvalNode(def);
                case "condition" -> conditionNode(def, params, previousOutput);
                case "trigger" -> triggerNode(def);
                default -> NodeOutcome.fail("未知节点类型: " + def.type);
            };
        } catch (RuntimeException e) {
            return NodeOutcome.fail("节点执行异常: " + e.getMessage());
        }
    }

    private NodeOutcome llmNode(PipelineNodeDef def, Map<String, Object> params,
                                String previousOutput) {
        if (llmGateway == null) {
            return NodeOutcome.fail("LLMGateway 未装配，llm 节点无法真实执行");
        }
        String prompt = firstNonBlank(
                text(def.config.get("prompt")), text(params.get("prompt")),
                previousOutput, "请执行节点：" + def.label);
        String model = firstNonBlank(text(def.config.get("model")), "deepseek-chat");
        List<ChatMessage> messages = new ArrayList<>();
        String sysPrompt = text(def.config.get("systemPrompt"));
        if (!isBlank(sysPrompt)) {
            messages.add(new ChatMessage("system", sysPrompt));
        }
        messages.add(new ChatMessage("user", prompt));
        ChatRequest request = new ChatRequest(model, messages,
                numberOr(def.config.get("temperature"), 0.7),
                intOr(def.config.get("maxTokens"), 2048), false);
        request.setCallerId("service:ai-engine-aip-pipeline");
        ChatResponse response = llmGateway.call(request);
        if (!response.isSuccess()) {
            return NodeOutcome.fail("LLM 调用失败: "
                    + (response.getErrorCode() != null ? response.getErrorCode() + " " : "")
                    + String.valueOf(response.getErrorClass()));
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("content", response.getContent());
        output.put("model", response.getModel());
        output.put("usageReported", response.isUsageReported());
        return NodeOutcome.ok(output, response.getContent());
    }

    private NodeOutcome toolNode(PipelineNodeDef def, Map<String, Object> params) {
        if (toolExecutorService == null) {
            return NodeOutcome.fail("ToolExecutorService 未装配，tool 节点无法真实执行");
        }
        String toolName = text(def.config.get("toolName"));
        if (isBlank(toolName)) {
            return NodeOutcome.fail("tool 节点缺少 toolName");
        }
        Map<String, Object> args = parseJsonObject(text(def.config.get("parameters")), params);
        ToolExecutorService.ToolResult result = toolExecutorService.execute(toolName, args);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("toolName", toolName);
        output.put("success", result.isSuccess());
        output.put("content", result.getContent());
        if (!result.isSuccess()) {
            return NodeOutcome.fail("工具执行失败 [" + toolName + "]: " + result.getError());
        }
        return NodeOutcome.ok(output, result.getContent());
    }

    private NodeOutcome ontologyNode(PipelineNodeDef def) {
        String base = resolveOwnerBase("buszhi");
        if (base == null) {
            return NodeOutcome.fail("buszhi 属主基址不可解析（route-manifest），ontology 节点无法执行");
        }
        String objectType = text(def.config.get("objectType"));
        String url = base + "/api/v1/ontology/objects"
                + (isBlank(objectType) ? "" : "?objectType=" + urlEncode(objectType));
        Map<?, ?> resp = pipelineRestTemplate.getForObject(url, Map.class);
        if (resp == null) {
            return NodeOutcome.fail("ontology 查询无响应");
        }
        Object data = resp.get("data");
        int count = data instanceof java.util.List<?> list ? list.size()
                : (data instanceof Map<?, ?> m && m.get("items") instanceof java.util.List<?> l ? l.size() : 0);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("objectType", objectType);
        output.put("queryType", text(def.config.get("queryType")));
        output.put("count", count);
        return NodeOutcome.ok(output, "ontology 查询 " + objectType + " → " + count + " 条");
    }

    private NodeOutcome approvalNode(PipelineNodeDef def) {
        String base = resolveOwnerBase("buszhi");
        if (base == null) {
            return NodeOutcome.fail("buszhi 属主基址不可解析（route-manifest），approval 节点无法执行");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("proposalType", "PIPELINE_APPROVAL");
        body.put("domainCode", firstNonBlank(text(def.config.get("domainCode")), "default"));
        body.put("targetEntity", firstNonBlank(text(def.config.get("approver")), "admin"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("approver", text(def.config.get("approver")));
        payload.put("timeout", def.config.get("timeout"));
        payload.put("nodeId", def.id);
        body.put("payload", payload);
        Map<?, ?> resp = pipelineRestTemplate.postForObject(base + "/api/v1/ontology/proposals",
                body, Map.class);
        if (resp == null) {
            return NodeOutcome.fail("approval 提案创建无响应");
        }
        Object data = resp.get("data");
        String proposalId = data instanceof Map<?, ?> dm && dm.get("id") != null
                ? String.valueOf(dm.get("id")) : null;
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("proposalId", proposalId);
        output.put("approver", text(def.config.get("approver")));
        output.put("status", "PENDING_APPROVAL");
        return NodeOutcome.ok(output, "审批提案已创建: " + proposalId);
    }

    private NodeOutcome conditionNode(PipelineNodeDef def, Map<String, Object> params,
                                      String previousOutput) {
        String expr = text(def.config.get("conditionExpr"));
        if (isBlank(expr)) {
            return NodeOutcome.fail("condition 节点缺少 conditionExpr");
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^\\s*(?:\\$\\.)?([A-Za-z0-9_.-]+)\\s*(==|!=|>=|<=|>|<|contains)\\s*(.+)\\s*$")
                .matcher(expr);
        if (!m.matches()) {
            return NodeOutcome.fail("conditionExpr 无法解析: " + expr);
        }
        String key = m.group(1);
        String operator = m.group(2);
        String rhs = stripQuotes(m.group(3).trim());
        Object lhsRaw = params.containsKey(key) ? params.get(key) : previousOutput;
        String lhs = lhsRaw == null ? "" : String.valueOf(lhsRaw);
        boolean result = switch (operator) {
            case "==" -> lhs.equals(rhs);
            case "!=" -> !lhs.equals(rhs);
            case "contains" -> lhs.contains(rhs);
            case ">" -> compareNumber(lhs, rhs) > 0;
            case "<" -> compareNumber(lhs, rhs) < 0;
            case ">=" -> compareNumber(lhs, rhs) >= 0;
            case "<=" -> compareNumber(lhs, rhs) <= 0;
            default -> false;
        };
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("expression", expr);
        output.put("left", lhs);
        output.put("result", result);
        output.put("branch", result
                ? firstNonBlank(text(def.config.get("thenBranch")), "then")
                : firstNonBlank(text(def.config.get("elseBranch")), "else"));
        return NodeOutcome.ok(output, "condition " + expr + " → " + result);
    }

    private NodeOutcome triggerNode(PipelineNodeDef def) {
        String cron = text(def.config.get("cronExpr"));
        String timezone = text(def.config.get("timezone"));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("mode", "manual");
        output.put("cronExpr", cron);
        output.put("timezone", timezone);
        output.put("note", "手动触发执行；周期注册归调度侧（runtime-task）");
        return NodeOutcome.ok(output, "trigger（手动）cron=" + cron);
    }

    /** buszhi 属主基址解析；解析失败返回 null（调用方如实报失败，禁回落硬编码地址）。 */
    private String resolveOwnerBase(String owner) {
        ServiceEndpointResolver resolver = endpointProvider == null ? null : endpointProvider.getIfAvailable();
        if (resolver == null) {
            return null;
        }
        Optional<String> host = resolver.resolve(owner, ServiceEndpointResolver.RouteMode.SERVICE);
        if (host.isEmpty()) {
            return null;
        }
        String base = host.get();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** parameters（JSON 字符串或 Map）与执行参数合并（执行参数优先）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJsonObject(String json, Map<String, Object> overrides) {
        Map<String, Object> args = new LinkedHashMap<>();
        if (!isBlank(json)) {
            try {
                Object parsed = JSON.readValue(json, Object.class);
                if (parsed instanceof Map<?, ?> m) {
                    args.putAll((Map<String, Object>) m);
                }
            } catch (Exception e) {
                log.warn("[AgentStudio] tool 节点 parameters 不是合法 JSON，忽略: {}", json);
            }
        }
        if (overrides != null) {
            args.putAll(overrides);
        }
        return args;
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String stripQuotes(String value) {
        if (value != null && value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static double compareNumber(String left, String right) {
        try {
            return Double.compare(Double.parseDouble(left.trim()), Double.parseDouble(right.trim()));
        } catch (NumberFormatException e) {
            return left.compareTo(right);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) {
                return v;
            }
        }
        return null;
    }

    private static Double numberOr(Object value, double fallback) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static Integer intOr(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ═══════════════ getPipelineStatus ═══════════════

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
            return OrchestrationResult.error(sessionId, "AgentLoopService 未就绪")
                    .withError(dependency("AgentLoopService"));
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

            // Loop 失败 ⇒ 整体失败：禁把 errorMsg 塞进 finalResponse 后报 success=true（F06-15 伪成功）
            if (!result.isSuccess()) {
                AiError error = fromLoopResult(result, "agent-studio-orchestrate");
                return OrchestrationResult.error(effectiveSessionId, error.userMessage())
                        .withError(error);
            }

            return OrchestrationResult.of(
                    effectiveSessionId,
                    agentCalls,
                    result.getContent(),
                    result.getTotalTokens(),
                    duration
            );
        } catch (Exception e) {
            // F06-15：捕后必分类，原文只入日志（X-49）
            AiError error = classify(e, ErrorCodeMapper.Context.llm("agent-studio-orchestrate"));
            log.error("[AgentStudio] orchestrate failed: class={} code={} traceId={} raw={}",
                    error.errorClass().name(), error.errorCode(), error.traceId(), error.rawReason(), e);
            return OrchestrationResult.error(effectiveSessionId, error.userMessage()).withError(error);
        }
    }

    // ═══════════════ 辅助方法 ═══════════════════

    /** 异常 → D.5 分类（F06-14 单源；traceId 与 MDC 同源）。 */
    private static AiError classify(Throwable e, ErrorCodeMapper.Context ctx) {
        return ErrorCodeMapper.classify(e, ctx, AiTraceSupport.currentTraceId());
    }

    /** 依赖未装配：D.5 无专用类，就近 {@code TOOL_BACKEND_ERROR}（502，可重试），禁 200 伪成功。 */
    private static AiError dependency(String service) {
        return AiError.toolBackend(AiTraceSupport.currentTraceId(), service, service + " 未装配");
    }

    /** Loop 失败态 → 分类（沿用 Loop 已上报的分类，禁二次猜测）。 */
    private static AiError fromLoopResult(AgentLoopResult loopResult, String service) {
        AiErrorClass errorClass = ErrorCodeMapper.aiErrorClassOf(loopResult.getErrorClass());
        if (errorClass != null) {
            return new AiError(errorClass, loopResult.getTraceId(), 0, loopResult.getErrorMsg(), service);
        }
        return new AiError(AiErrorClass.TOOL_BACKEND_ERROR, loopResult.getTraceId(), 0,
                "Loop 未上报错误分类: " + loopResult.getErrorMsg(), service);
    }

    /** 取第一个已分类的失败态（两 Agent 都失败时的整体分类）。 */
    private static AiError errorOf(AgentTestResult a, AgentTestResult b) {
        AiErrorClass cls = ErrorCodeMapper.aiErrorClassOf(a.getErrorClass());
        if (cls != null) {
            return new AiError(cls, a.getErrorTraceId(), 0, a.getErrorMsg(), "agent-studio-compare");
        }
        cls = ErrorCodeMapper.aiErrorClassOf(b.getErrorClass());
        if (cls != null) {
            return new AiError(cls, b.getErrorTraceId(), 0, b.getErrorMsg(), "agent-studio-compare");
        }
        return dependency("agent-studio-compare");
    }

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
        /** D.5 分类（F06-14）：失败态必填，控制器据此渲染状态，禁 200 伪成功 */
        private String errorClass;
        private String errorCode;
        private String errorTraceId;
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
            // 失败态必带分类：未显式归类时兜底 TOOL_BACKEND_ERROR，禁控制器回落 200
            r.errorClass = AiErrorClass.TOOL_BACKEND_ERROR.name();
            r.errorCode = AiErrorClass.TOOL_BACKEND_ERROR.errorCode();
            r.errorTraceId = AiTraceSupport.currentTraceId();
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
        public String getErrorClass() { return errorClass; }
        public String getErrorCode() { return errorCode; }
        public String getErrorTraceId() { return errorTraceId; }

        /** 附 D.5 分类（链式返回本类型，供控制器按分类渲染状态）。 */
        public AgentTestResult withError(AiError error) {
            this.errorClass = error.errorClass().name();
            this.errorCode = error.errorCode();
            this.errorTraceId = error.traceId();
            return this;
        }

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
            if (errorClass != null) {
                m.put("errorCode", errorCode);
                m.put("errorClass", errorClass);
                m.put("traceId", errorTraceId == null ? "-" : errorTraceId);
            }
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
        private String errorClass;
        private String errorCode;
        private String errorTraceId;

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
            r.errorClass = AiErrorClass.TOOL_BACKEND_ERROR.name();
            r.errorCode = AiErrorClass.TOOL_BACKEND_ERROR.errorCode();
            r.errorTraceId = AiTraceSupport.currentTraceId();
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
        public String getErrorClass() { return errorClass; }
        public String getErrorCode() { return errorCode; }
        public String getErrorTraceId() { return errorTraceId; }

        public AgentCompareResult withError(AiError error) {
            this.errorClass = error.errorClass().name();
            this.errorCode = error.errorCode();
            this.errorTraceId = error.traceId();
            return this;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", success);
            if (errorMsg != null) m.put("errorMsg", errorMsg);
            if (errorClass != null) {
                m.put("errorCode", errorCode);
                m.put("errorClass", errorClass);
                m.put("traceId", errorTraceId == null ? "-" : errorTraceId);
            }
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
        private String errorClass;
        private String errorCode;
        private String errorTraceId;

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
            r.errorClass = AiErrorClass.TOOL_BACKEND_ERROR.name();
            r.errorCode = AiErrorClass.TOOL_BACKEND_ERROR.errorCode();
            r.errorTraceId = AiTraceSupport.currentTraceId();
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
        public String getErrorClass() { return errorClass; }
        public String getErrorCode() { return errorCode; }
        public String getErrorTraceId() { return errorTraceId; }

        public OrchestrationResult withError(AiError error) {
            this.errorClass = error.errorClass().name();
            this.errorCode = error.errorCode();
            this.errorTraceId = error.traceId();
            return this;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("success", success);
            m.put("sessionId", sessionId);
            m.put("agentCalls", agentCalls);
            m.put("finalResponse", finalResponse);
            m.put("totalTokens", totalTokens);
            m.put("durationMs", durationMs);
            if (errorMsg != null) m.put("errorMsg", errorMsg);
            if (errorClass != null) {
                m.put("errorCode", errorCode);
                m.put("errorClass", errorClass);
                m.put("traceId", errorTraceId == null ? "-" : errorTraceId);
            }
            return m;
        }
    }
}
