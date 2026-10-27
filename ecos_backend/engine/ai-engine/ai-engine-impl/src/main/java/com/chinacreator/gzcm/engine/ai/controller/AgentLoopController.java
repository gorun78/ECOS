package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.common.stream.SseFrameContract;
import com.chinacreator.gzcm.common.trace.AgentLoopTrace;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopConfig;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopResult;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopService;
import com.chinacreator.gzcm.engine.ai.service.AgentSessionService;
import com.chinacreator.gzcm.engine.ai.service.AgentCircuitBreaker;
import com.chinacreator.gzcm.engine.ai.service.ErrorCodeMapper;
import com.chinacreator.gzcm.engine.ai.stream.AgentLoopSseFrames;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.runtime.llm.session.AgentSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Agent Loop Controller — 非流式对话 + SSE流式 + 会话管理。
 *
 * <pre>
 *   POST /api/v1/agent-loop/chat              — 非流式对话
 *   POST /api/v1/agent-loop/chat              — SSE流式（produces=text/event-stream）
 *   POST /api/v1/agent-loop/sessions          — 创建会话
 *   GET  /api/v1/agent-loop/sessions          — 列会话（F06-21 死调用承接，只增不改）
 *   GET  /api/v1/agent-loop/sessions/{id}     — 查会话详情
 *   POST /api/v1/agent-loop/sessions/{id}/chat — 会话内对话
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/agent-loop")
public class AgentLoopController {

    private static final Logger log = LoggerFactory.getLogger(AgentLoopController.class);

    /** SSE 超时（毫秒） */
    private static final long SSE_TIMEOUT_MS = 300_000L;

    @Autowired
    private AgentLoopService agentLoopService;

    @Autowired
    private AgentSessionService sessionService;

    private final AgentCircuitBreaker circuitBreaker = new AgentCircuitBreaker();

    // ═══════════════════════════════════════════════════════════════
    //  1. POST /api/v1/agent-loop/chat — 非流式对话
    // ═══════════════════════════════════════════════════════════════

    /**
     * 非流式 Agent 对话 — 注入 {@link AgentLoopService#run}。
     *
     * <p>请求体示例：</p>
     * <pre>
     * {
     *   "message": "查询今天的天气",
     *   "model": "deepseek-chat",
     *   "temperature": 0.7,
     *   "maxTokens": 4096,
     *   "systemPrompt": "你是一个有用的助手",
     *   "sessionId": "sess-xxx"  // 可选，不传则自动创建运行时 session
     * }
     * </pre>
     *
     * @param thread 会话线程标识，默认 "main"，支持同一会话下多线程对话
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<Map<String, Object>>> chat(@RequestBody Map<String, Object> body,
                                                  @RequestParam(name = "thread", defaultValue = "main") String thread) {
        // F06-15：控制器不 catch(Exception) —— 未预期异常交由 AiErrorAdvice 分类渲染（errorCode + traceId）
        String message = (String) body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.ok(ApiResponse.badRequest("message 不能为空"));
        }

        // T0c-3: 熔断器检查（可重试的服务不可用态，D.5 就近取 LLM_TIMEOUT 504）
        String agentId = (String) body.getOrDefault("agentId", "default");
        if (!circuitBreaker.isAllowed(agentId)) {
            return ResponseEntity.status(HttpStatus.valueOf(AiErrorClass.LLM_TIMEOUT.httpStatus()))
                    .body(AiResponseSupport.serviceUnavailable(AiErrorClass.LLM_TIMEOUT,
                            "Agent 服务暂时不可用（熔断保护），请稍后重试"));
        }

        // 构建 AgentLoopConfig
        AgentLoopConfig config = buildConfig(body);

        // 构建运行时 AgentSession（用于 systemPrompt 兜底）
        AgentSession session = buildRuntimeSession(body);

        // 执行推理循环
        AgentLoopResult result = agentLoopService.run(config, message, session);

        // T0c-3: 记录结果
        if (result.isSuccess()) {
            circuitBreaker.recordSuccess(agentId);
        } else {
            circuitBreaker.recordFailure(agentId);
        }

        return AiResponseSupport.fromLoopResult(result, resultToMap(result));
    }

    // ═══════════════════════════════════════════════════════════════
    //  2. POST /api/v1/agent-loop/chat — SSE 流式
    // ═══════════════════════════════════════════════════════════════

    /**
     * SSE 流式 Agent 对话（详细设计-06 §9.3.3 七帧承流；帧名/帧序/键面单源
     * {@link SseFrameContract}，写出面 {@link AgentLoopSseFrames}）。
     *
     * <p>帧序 {@code meta → step → token → delta → citation → error → done}。
     * {@code meta} 在进入推理循环<b>之前</b>发出（首帧必发），故请求体非法与熔断两条
     * 早退分支也带帧序上下文，不再出现旧形态"一条裸 error"。</p>
     *
     * <p>旧帧名 {@code tool_call}/{@code tool_result} 不再是 event 名 —— §9.3.3 把它们
     * 定义为 {@code step} 帧的 {@code op} 取值，词形仍逐字出现在帧载荷里；正文增量的
     * 帧名由旧 {@code token} 改为 {@code delta}，{@code token} 归还给 §9.3.3 的用量帧
     * （同帧名两种载荷正是本差距的实测形态）。</p>
     *
     * <p>请求体格式同非流式端点。</p>
     *
     * @param thread 会话线程标识，默认 "main"
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> body,
                                  @RequestParam(name = "thread", defaultValue = "main") String thread) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // traceId 只能在请求线程取：CompletableFuture 的池线程不继承 MDC，异步内取会恒得缺失标记
        String traceId = AiTraceSupport.currentTraceId();
        AgentLoopSseFrames frames = new AgentLoopSseFrames(traceId,
                (event, data) -> emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON)));

        CompletableFuture.runAsync(() -> {
            try {
                runStream(body, frames);
                emitter.complete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[AgentLoop] SSE 流式对话被中断", e);
                failClosedTail(frames, e);
                emitter.completeWithError(e);
            } catch (RuntimeException | IOException e) {
                // F06-15：控制器不写 catch(Exception)；此处只兜 SSE 通道（异步线程内异常无人接管），
                // 且外泄面只有 D.5 分类码 + 诊断码
                AiError error = ErrorCodeMapper.classify(e, ErrorCodeMapper.Context.generic(), traceId);
                log.error("[AgentLoop] SSE 流式对话失败: class={} code={} traceId={}",
                        error.errorClass().name(), error.errorCode(), error.traceId(), e);
                failClosedTail(frames, error);
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> log.debug("[AgentLoop] SSE stream completed"));
        emitter.onTimeout(() -> log.warn("[AgentLoop] SSE stream timed out after {}ms", SSE_TIMEOUT_MS));
        emitter.onError(ex -> log.error("[AgentLoop] SSE stream error", ex));

        return emitter;
    }

    // ═══════════════════════════════════════════════════════════════
    //  3. POST /api/v1/agent-loop/sessions — 创建会话
    // ═══════════════════════════════════════════════════════════════

    /**
     * 创建 Agent 会话。
     *
     * <p>请求体示例：</p>
     * <pre>
     * {
     *   "agentId": "default",
     *   "userId": "user-001",
     *   "tenantId": "tenant-001"
     * }
     * </pre>
     */
    @PostMapping("/sessions")
    public ApiResponse<Map<String, Object>> createSession(@RequestBody Map<String, Object> body) {
        String agentId = (String) body.getOrDefault("agentId", "default");
        String userId = (String) body.getOrDefault("userId", "anonymous");
        String tenantId = (String) body.getOrDefault("tenantId", "default");

        AgentSessionService.AgentSession session = sessionService.createSession(agentId, userId, tenantId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", session.getId());
        data.put("agentId", session.getAgentId());
        data.put("userId", session.getUserId());
        data.put("tenantId", session.getTenantId());
        data.put("status", session.getStatus());
        data.put("messageCount", session.getMessageCount());
        data.put("createdAt", session.getCreatedAt());
        data.put("lastActiveAt", session.getLastActiveAt());

        return ApiResponse.success(data);
    }

    // ═══════════════════════════════════════════════════════════════
    //  3b. GET /api/v1/agent-loop/sessions — 列会话（F06-21 死调用改指真端点）
    // ═══════════════════════════════════════════════════════════════

    /**
     * F06-21 要点③处置①（AI 面死调用改指真端点，遵"只增不改"铁律）：
     * 前端 {@code ecosystem_frontend/src/pages/aiworkbench/api.ts} 已有的
     * {@code fetch('/api/v1/agent-loop/sessions?agentId=…')} 调用原来无后端承接（404），
     * 本方法<b>新增</b>（不改动任何既有签名 / 路径），承接该死调用。
     *
     * <p>参数（全部可选）：
     * <ul>
     *   <li>{@code agentId} — 按 Agent 过滤（前端传）；缺省 → 列出所有 ACTIVE 会话</li>
     *   <li>{@code status}  — Java 侧再过滤（ACTIVE/IDLE/EXPIRED/ARCHIVED）</li>
     *   <li>{@code limit}   — 只取前 N 条（DAO 已按 {@code last_active_at DESC} 排），默认 50</li>
     * </ul>
     * 不新增 DAO SQL：复用 {@code AgentSessionDao.listByAgent}/{@code listByStatus} 原语，
     * 命中"不重复 SQL / 单源节点"约束。
     */
    @GetMapping("/sessions")
    public ApiResponse<Map<String, Object>> listSessions(
            @RequestParam(name = "agentId", required = false) String agentId,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        List<AgentSessionService.AgentSession> rows = sessionService.listSessions(agentId, status, limit);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", rows.size());
        data.put("sessions", rows.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("agentId", s.getAgentId());
            m.put("userId", s.getUserId());
            m.put("tenantId", s.getTenantId());
            m.put("status", s.getStatus());
            m.put("messageCount", s.getMessageCount());
            m.put("createdAt", s.getCreatedAt());
            m.put("lastActiveAt", s.getLastActiveAt());
            return m;
        }).toList());
        return ApiResponse.success(data);
    }

    // ═══════════════════════════════════════════════════════════════
    //  4. GET /api/v1/agent-loop/sessions/{id} — 查会话详情
    // ═══════════════════════════════════════════════════════════════

    /**
     * 查询会话详情（含消息历史）。
     *
     * @param thread 可选：按线程过滤消息，默认 "main"；传 "*" 返回所有线程消息
     */
    @GetMapping("/sessions/{id}")
    public ApiResponse<Map<String, Object>> getSession(@PathVariable String id,
                                                        @RequestParam(name = "thread", defaultValue = "main") String thread) {
        AgentSessionService.AgentSession session = sessionService.getSession(id);

        if (session == null) {
            return ApiResponse.notFound("会话 " + id + " 不存在");
        }

        // 按线程加载消息
        List<AgentSessionService.AgentMessage> messages;
        if ("*".equals(thread)) {
            // 不按线程过滤 — 仍用默认 getMessages (回退到 main)
            messages = sessionService.getMessages(id);
        } else {
            messages = sessionService.getMessages(id, thread);
        }
        session.setMessages(messages);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", session.getId());
        data.put("agentId", session.getAgentId());
        data.put("userId", session.getUserId());
        data.put("tenantId", session.getTenantId());
        data.put("status", session.getStatus());
        data.put("messageCount", session.getMessageCount());
        data.put("createdAt", session.getCreatedAt());
        data.put("lastActiveAt", session.getLastActiveAt());

        // 消息列表
        if (session.getMessages() != null) {
            data.put("messages", session.getMessages().stream().map(m -> {
                Map<String, Object> msgMap = new LinkedHashMap<>();
                msgMap.put("id", m.getId());
                msgMap.put("role", m.getRole());
                msgMap.put("content", m.getContent());
                msgMap.put("toolCalls", m.getToolCalls());
                msgMap.put("toolResults", m.getToolResults());
                msgMap.put("tokens", m.getTokens());
                msgMap.put("threadId", m.getThreadId());
                msgMap.put("createdAt", m.getCreatedAt());
                return msgMap;
            }).toList());
        }

        return ApiResponse.success(data);
    }

    // ═══════════════════════════════════════════════════════════════
    //  4b. GET /sessions/{id}/messages — 历史消息分页（P1.3 #4 listSessionMessages）
    // ═══════════════════════════════════════════════════════════════

    /**
     * 历史消息分页（附录A #4：P1.3 端点缺口收口，承流不应语义化——详设-06 §9.3.1）。
     *
     * <p>读面单源 {@link AgentSessionService#getMessages}（{@code findContextMessages}：
     * V202 治理列就位 ⇒ 只查活消息（未归档、未逻辑删），未就位回落全量），不另建 SQL。
     * 分页 {@code page/size} 在 Java 侧切片（按 DAO 落库 id 正序，{@code items} 为当前页子集），
     * 遵循单源 DAO 原语不重复 SQL 的约束。</p>
     *
     * <p>会话不存在 ⇒ {@code ApiResponse.notFound}（与该 Controller 其余端点的 notFound 口径一致，
     * 不合成空消息列表伪数据）。</p>
     *
     * @param page 页码，默认 1（<b>1-based</b>，<=1 视作第 1 页）
     * @param size 每页条数，默认 50（默认值与 §9.3.2 分页口径一致；上限 500）
     */
    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<Map<String, Object>> listSessionMessages(@PathVariable String id,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        AgentSessionService.AgentSession session = sessionService.getSession(id);
        if (session == null) {
            return ApiResponse.notFound("会话 " + id + " 不存在");
        }
        List<AgentSessionService.AgentMessage> all = sessionService.getMessages(id);
        if (all == null) {
            all = java.util.Collections.emptyList();
        }
        int safePage = Math.max(1, page);
        int safeSize = size <= 0 ? 50 : Math.min(size, 500);
        int fromIndex = (safePage - 1) * safeSize;
        List<AgentSessionService.AgentMessage> slice = (fromIndex >= all.size())
                ? java.util.Collections.emptyList()
                : all.subList(fromIndex, Math.min(all.size(), fromIndex + safeSize));
        List<Map<String, Object>> items = slice.stream().map(m -> {
            Map<String, Object> msgMap = new LinkedHashMap<>();
            msgMap.put("id", m.getId());
            msgMap.put("role", m.getRole());
            msgMap.put("content", m.getContent());
            msgMap.put("toolCalls", m.getToolCalls());
            msgMap.put("toolResults", m.getToolResults());
            msgMap.put("tokens", m.getTokens());
            msgMap.put("threadId", m.getThreadId());
            msgMap.put("createdAt", m.getCreatedAt());
            return msgMap;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", all.size());
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("items", items);
        return ApiResponse.success(data);
    }

    // ═══════════════════════════════════════════════════════════════
    //  5. POST /api/v1/agent-loop/sessions/{id}/chat — 会话内对话
    // ═══════════════════════════════════════════════════════════════

    /**
     * 在已有会话内发起对话。
     *
     * <p>请求体示例：</p>
     * <pre>
     * {
     *   "message": "继续上次的话题",
     *   "model": "deepseek-chat",
     *   "temperature": 0.7,
     *   "maxTokens": 4096,
     *   "systemPrompt": "你是一个有用的助手"
     * }
     * </pre>
     *
     * @param thread 会话线程标识，默认 "main"
     */
    @PostMapping("/sessions/{id}/chat")
    public ResponseEntity<ApiResponse<Map<String, Object>>> chatInSession(@PathVariable String id,
                                                           @RequestBody Map<String, Object> body,
                                                           @RequestParam(name = "thread", defaultValue = "main") String thread) {
        // 校验会话存在
        AgentSessionService.AgentSession persistedSession = sessionService.getSession(id);
        if (persistedSession == null) {
            return ResponseEntity.ok(ApiResponse.notFound("会话 " + id + " 不存在"));
        }

        String message = (String) body.get("message");
        if (message == null || message.isBlank()) {
            return ResponseEntity.ok(ApiResponse.badRequest("message 不能为空"));
        }

        // T0c-3: 熔断器检查（可重试的服务不可用态，D.5 就近取 LLM_TIMEOUT 504）
        String agentId = (String) body.getOrDefault("agentId", "default");
        if (!circuitBreaker.isAllowed(agentId)) {
            return ResponseEntity.status(HttpStatus.valueOf(AiErrorClass.LLM_TIMEOUT.httpStatus()))
                    .body(AiResponseSupport.serviceUnavailable(AiErrorClass.LLM_TIMEOUT,
                            "Agent 服务暂时不可用（熔断保护），请稍后重试"));
        }

        // 构建配置（systemPrompt 优先使用请求参数，fallback 到持久化 session 的 agentId）
        AgentLoopConfig config = buildConfig(body);

        // 构建运行时 AgentSession，绑定 sessionId
        AgentSession runtimeSession = buildRuntimeSession(body);
        runtimeSession.setSessionId(id);

        // 执行推理循环
        AgentLoopResult result = agentLoopService.run(config, message, runtimeSession);

        // T0c-3: 记录结果
        if (result.isSuccess()) {
            circuitBreaker.recordSuccess(agentId);
        } else {
            circuitBreaker.recordFailure(agentId);
        }

        // 持久化消息（非流式场景简化：仅记录用户消息 + 最终回复），使用指定线程
        sessionService.appendMessage(id, "user", message, null, null, thread);
        if (result.isSuccess() && result.getContent() != null) {
            List<Map<String, Object>> tcJsons = null;
            if (result.getToolCalls() != null && !result.getToolCalls().isEmpty()) {
                tcJsons = result.getToolCalls();
            }
            sessionService.appendMessage(id, "assistant", result.getContent(), tcJsons, null, thread);
        }

        Map<String, Object> data = resultToMap(result);
        data.put("sessionId", id);
        return AiResponseSupport.fromLoopResult(result, data);
    }

    // ═══════════════════════════════════════════════════════════════
    //  私有工具方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 从请求体构建 {@link AgentLoopConfig}。
     */
    private AgentLoopConfig buildConfig(Map<String, Object> body) {
        AgentLoopConfig config = new AgentLoopConfig();
        config.setSystemPrompt((String) body.get("systemPrompt"));
        config.setModel((String) body.getOrDefault("model", "deepseek-chat"));
        config.setTemperature(getDouble(body, "temperature", 0.7));
        config.setMaxTokens(getInt(body, "maxTokens", 4096));
        return config;
    }

    /**
     * 构建运行时 {@link AgentSession}（用于 AgentLoopService.run 的上下文传递）。
     */
    private AgentSession buildRuntimeSession(Map<String, Object> body) {
        String sessionId = (String) body.getOrDefault("sessionId",
                "sess-" + UUID.randomUUID().toString().replace("-", ""));
        String systemPrompt = (String) body.get("systemPrompt");

        AgentSession session = new AgentSession();
        session.setSessionId(sessionId);
        session.setSubsystem("ai-engine");
        session.setProfileName((String) body.getOrDefault("agentId", "default"));
        session.setSystemPrompt(systemPrompt);
        session.setStatus("active");
        return session;
    }

    /**
     * 将 {@link AgentLoopResult} 转为响应 Map。
     */
    private Map<String, Object> resultToMap(AgentLoopResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", result.getSessionId());
        data.put("traceId", result.getTraceId());
        data.put("content", result.getContent());
        data.put("turns", result.getTurns());
        data.put("totalTokens", result.getTotalTokens());
        data.put("success", result.isSuccess());
        if (result.getErrorMsg() != null) {
            // F06-14：该文本恒为 D.5 模板渲染结果（含诊断码），异常原文已在 Service 层止步于日志
            data.put("errorMsg", result.getErrorMsg());
        }
        if (result.getErrorCode() != null) {
            data.put("errorCode", result.getErrorCode());
        }
        if (result.getErrorClass() != null) {
            data.put("errorClass", result.getErrorClass());
        }
        if (!result.isSuccess()) {
            data.put("retryable", AiResponseSupport.resolveClass(result).retryable());
        }
        if (result.getToolCalls() != null && !result.getToolCalls().isEmpty()) {
            data.put("toolCalls", result.getToolCalls());
        }
        return data;
    }

    /**
     * 七帧写出（可测入口）：{@link #chatStream} 只负责 emitter 装配与完成信号，
     * 帧序与载荷都在本方法，测试用记录型 sink 驱动即可断到真实出帧形态。
     *
     * <p>包内可见是刻意的：帧序属承流契约，必须能被入口级用例驱动，
     * 而不是"加个类再单测它自己"。</p>
     */
    void runStream(Map<String, Object> body, AgentLoopSseFrames frames) throws IOException, InterruptedException {
        AgentLoopConfig config = buildConfig(body);
        String message = (String) body.get("message");
        frames.meta((String) body.get("sessionId"), config.getModel(), config.getMaxContextTokens(), null);

        if (message == null || message.isBlank()) {
            failClosedTail(frames, AiError.of(AiErrorClass.INVALID_ARGUMENT, frames.traceId()));
            return;
        }

        // T0c-3: 熔断器检查
        String agentId = (String) body.getOrDefault("agentId", "default");
        if (!circuitBreaker.isAllowed(agentId)) {
            failClosedTail(frames, AiError.of(AiErrorClass.LLM_RATE_LIMITED, frames.traceId()));
            return;
        }

        AgentSession session = buildRuntimeSession(body);

        // 执行推理循环
        AgentLoopResult result = agentLoopService.run(config, message, session);

        // T0c-3: 记录结果
        if (result.isSuccess()) {
            circuitBreaker.recordSuccess(agentId);
        } else {
            circuitBreaker.recordFailure(agentId);
        }

        for (Map<String, Object> tc : toolRecordsOf(result)) {
            int turn = tc.get("turn") instanceof Number n ? n.intValue() : 0;
            String tool = tc.get("toolName") == null ? null : String.valueOf(tc.get("toolName"));
            String decision = tc.get("decision") == null ? null : String.valueOf(tc.get("decision"));
            frames.step(AgentLoopTrace.Op.TOOL_CALL, turn, tool, config.getModel(), decision);
            frames.step(AgentLoopTrace.Op.TOOL_RESULT, turn, tool, config.getModel(), decision);
        }

        // 失败分支不编正文：delta/citation 属"有据叙述"腿，失败时只出 error → done
        AiError failure = result.isSuccess()
                ? null
                : AiError.of(AiResponseSupport.resolveClass(result), frames.traceId());
        if (result.isSuccess()) {
            frames.step(AgentLoopTrace.Op.LLM_CALL, result.getTurns(), null, config.getModel(),
                    AgentLoopTrace.Decision.NA.name());
            // 分项用量未随 AgentLoopResult 传播 ⇒ 按 §9.3.3 写 -1，禁以 totalTokens 折算冒充分项
            frames.tokenUsage(SseFrameContract.unknownToken(), SseFrameContract.unknownToken(), false);
            for (String chunk : splitDeltaChunks(result.getContent())) {
                frames.delta(chunk);
                // 微小延迟保留原节奏（真流式增量待 llm-gateway stream 接入，非本批范围）
                Thread.sleep(10);
            }
            String block = citationBlockOf(result);
            if (block != null) {
                frames.citation(block);
            }
        }
        if (failure != null) {
            frames.error(failure);
        }
        frames.done(doneFields(result, failure));
    }

    /** 工具记录面归一（Service 可能回传 null 列表：出帧侧按空集处理，不另造记录）。 */
    private static List<Map<String, Object>> toolRecordsOf(AgentLoopResult result) {
        return result.getToolCalls() == null ? List.of() : result.getToolCalls();
    }

    /** 引用块来源：循环级确定性渲染优先，否则取工具记录里第一条带引用的返回体（禁在此另算口径）。 */
    private static String citationBlockOf(AgentLoopResult result) {
        if (result.getCitationBlock() != null && !result.getCitationBlock().isBlank()) {
            return result.getCitationBlock();
        }
        for (Map<String, Object> tc : toolRecordsOf(result)) {
            Object block = tc.get("citationBlock");
            if (block instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    /** 末帧载荷：压缩态未判定 ⇒ 键缺席（F06-09 ⑥「前端渲染未上报，禁伪造」）。 */
    private static Map<String, Object> doneFields(AgentLoopResult result, AiError failure) {
        Map<String, Object> done = new LinkedHashMap<>();
        done.put(SseFrameContract.KEY_SESSION_ID, result.getSessionId());
        done.put("turns", result.getTurns());
        done.put(SseFrameContract.KEY_TOTAL_TOKENS, result.getTotalTokens());
        done.put("success", result.isSuccess());
        if (failure != null) {
            // 与 error 帧同源同一次分类，禁两处各自再推一遍（旧形态是 done 自带一套码值）
            done.put(SseFrameContract.KEY_ERROR_CODE, failure.errorCode());
            done.put("errorClass", failure.errorClass().name());
            done.put(SseFrameContract.KEY_RETRYABLE, failure.retryable());
            // 专项文案（如配额已用/上限）由上游按 D.5 模板渲染后随结果携带，只走 done 帧；
            // error 帧按 §9.3.3 恒为分类模板文案，两帧因此既不同形也同不断源
            if (result.getErrorMsg() != null && !result.getErrorMsg().isBlank()) {
                done.put("message", result.getErrorMsg());
            }
        }
        if (result.isCompressionAssessed()) {
            done.put(SseFrameContract.KEY_COMPRESSION_MODE, result.getCompressionMode());
            done.put("compressedTurns", result.getCompressedTurns());
            done.put("compressSkipped", result.isCompressSkipped());
        }
        return done;
    }

    /** 兜底腿的异常形态：先按 D.5 分类再出帧，异常原文无入口。 */
    void failClosedTail(AgentLoopSseFrames frames, Throwable e) {
        failClosedTail(frames, ErrorCodeMapper.classify(e, ErrorCodeMapper.Context.generic(), frames.traceId()));
    }

    /**
     * 故障尾帧：{@code meta} 未及写出时先补一发（首帧必发），随后 {@code error → done}。
     * 连接可能已关闭，下发失败只记 debug，避免 SSE 线程二次异常。
     *
     * <p>包内可见与 {@link #runStream} 同理：尾帧是承流写出面的一部分，必须能被入口级用例驱动。</p>
     */
    void failClosedTail(AgentLoopSseFrames frames, AiError error) {
        try {
            if (frames.emittedNames().isEmpty()) {
                frames.meta(null, null, null, null);
            }
            frames.error(error);
            Map<String, Object> tail = new LinkedHashMap<>();
            tail.put("success", false);
            tail.put(SseFrameContract.KEY_ERROR_CODE, error.errorCode());
            tail.put("errorClass", error.errorClass().name());
            tail.put(SseFrameContract.KEY_RETRYABLE, error.retryable());
            frames.done(tail);
        } catch (IOException | RuntimeException ignored) {
            log.debug("[AgentLoop] SSE 故障尾帧下发失败（连接已关闭）");
        }
    }

    /**
     * 将终稿文本按语义边界分段，作为 {@code delta} 帧正文。
     *
     * <p>命名由 {@code splitIntoTokens} 改为 {@code splitDeltaChunks}：分段的帧名是
     * {@code delta}（§9.3.3 帧 4），{@code token} 帧另有用量语义（帧 3），一名两义即本差距病灶。</p>
     */
    private List<String> splitDeltaChunks(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }

        // 按段/句边界切分，每段作为一个 delta 帧下发
        String[] paragraphs = content.split("(?<=\\n\\n)");
        java.util.List<String> chunks = new java.util.ArrayList<>();
        for (String para : paragraphs) {
            if (para.length() <= 80) {
                chunks.add(para);
            } else {
                // 按句子切分
                String[] sentences = para.split("(?<=[。！？\\n.?!])");
                for (String sentence : sentences) {
                    if (!sentence.isBlank()) {
                        chunks.add(sentence);
                    }
                }
            }
        }
        return chunks;
    }

    private Double getDouble(Map<String, Object> body, String key, Double defaultValue) {
        Object val = body.get(key);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return defaultValue;
    }

    private Integer getInt(Map<String, Object> body, String key, Integer defaultValue) {
        Object val = body.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }
}
