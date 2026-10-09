package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.error.AiError;
import com.chinacreator.gzcm.common.error.AiErrorClass;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopConfig;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopResult;
import com.chinacreator.gzcm.engine.ai.service.AgentLoopService;
import com.chinacreator.gzcm.engine.ai.service.AgentSessionService;
import com.chinacreator.gzcm.engine.ai.service.AgentCircuitBreaker;
import com.chinacreator.gzcm.engine.ai.service.ErrorCodeMapper;
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
     * SSE 流式 Agent 对话。
     *
     * <p>SSE 事件类型：</p>
     * <ul>
     *   <li>{@code event:token} — 逐 token 推送回复内容</li>
     *   <li>{@code event:tool_call} — 工具调用通知</li>
     *   <li>{@code event:tool_result} — 工具执行结果</li>
     *   <li>{@code event:done} — 推理完成</li>
     *   <li>{@code event:error} — 错误信息</li>
     * </ul>
     *
     * <p>请求体格式同非流式端点。</p>
     *
     * @param thread 会话线程标识，默认 "main"
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody Map<String, Object> body,
                                  @RequestParam(name = "thread", defaultValue = "main") String thread) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        CompletableFuture.runAsync(() -> {
            try {
                String message = (String) body.get("message");
                if (message == null || message.isBlank()) {
                    sendEvent(emitter, "error", Map.of("message", "message 不能为空"));
                    emitter.complete();
                    return;
                }

                // T0c-3: 熔断器检查
                String agentId = (String) body.getOrDefault("agentId", "default");
                if (!circuitBreaker.isAllowed(agentId)) {
                    sendEvent(emitter, "error", Map.of("message", "Agent 服务暂时不可用（熔断保护），请稍后重试"));
                    emitter.complete();
                    return;
                }

                AgentLoopConfig config = buildConfig(body);
                AgentSession session = buildRuntimeSession(body);

                // 执行推理循环
                AgentLoopResult result = agentLoopService.run(config, message, session);

                // T0c-3: 记录结果
                if (result.isSuccess()) {
                    circuitBreaker.recordSuccess(agentId);
                } else {
                    circuitBreaker.recordFailure(agentId);
                }

                // 推送 tool_call 事件
                if (result.getToolCalls() != null && !result.getToolCalls().isEmpty()) {
                    for (Map<String, Object> tc : result.getToolCalls()) {
                        Map<String, Object> event = new LinkedHashMap<>();
                        event.put("toolName", tc.get("toolName"));
                        event.put("toolCallId", tc.get("toolCallId"));
                        event.put("durationMs", tc.get("durationMs"));
                        event.put("success", tc.get("success"));
                        event.put("turn", tc.get("turn"));

                        sendEvent(emitter, "tool_call", event);

                        // 工具结果
                        Map<String, Object> resultEvent = new LinkedHashMap<>();
                        resultEvent.put("toolName", tc.get("toolName"));
                        resultEvent.put("resultSnippet", tc.get("resultSnippet"));
                        sendEvent(emitter, "tool_result", resultEvent);
                    }
                }

                // 推送 token 事件 — 将最终回复按 token 粒度分批发送
                if (result.isSuccess() && result.getContent() != null) {
                    String content = result.getContent();
                    List<String> tokens = splitIntoTokens(content);
                    for (String token : tokens) {
                        sendEvent(emitter, "token", Map.of("content", token));
                        // 微小延迟模拟逐 token 输出
                        Thread.sleep(10);
                    }
                }

                // 推送 done 事件
                Map<String, Object> doneEvent = new LinkedHashMap<>();
                doneEvent.put("sessionId", result.getSessionId());
                doneEvent.put("turns", result.getTurns());
                doneEvent.put("totalTokens", result.getTotalTokens());
                doneEvent.put("success", result.isSuccess());
                if (!result.isSuccess()) {
                    // F06-14/15：失败帧只出分类码 + 渲染消息 + 诊断码，异常原文不外泄（X-49）
                    AiErrorClass errorClass = AiResponseSupport.resolveClass(result);
                    doneEvent.put("errorCode", result.getErrorCode() == null
                            ? errorClass.errorCode() : result.getErrorCode());
                    doneEvent.put("errorClass", errorClass.name());
                    doneEvent.put("retryable", errorClass.retryable());
                    doneEvent.put("message", result.getErrorMsg() == null
                            ? errorClass.renderMessage(result.getTraceId()) : result.getErrorMsg());
                }
                // F06-09 ⑥：压缩状态只在上游已判定时下发，未判定 ⇒ 键缺席（前端渲染"未上报"，禁伪造）
                if (result.isCompressionAssessed()) {
                    doneEvent.put("compressionMode", result.getCompressionMode());
                    doneEvent.put("compressedTurns", result.getCompressedTurns());
                    doneEvent.put("compressSkipped", result.isCompressSkipped());
                }
                sendEvent(emitter, "done", doneEvent);

                emitter.complete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[AgentLoop] SSE 流式对话被中断", e);
                sendErrorQuietly(emitter, Map.of("message", "推理被中断，请稍后重试"));
                emitter.completeWithError(e);
            } catch (RuntimeException | IOException e) {
                // F06-15：控制器不写 catch(Exception)；此处只兜 SSE 通道（异步线程内异常无人接管），
                // 且外泄面只有 D.5 分类码 + 诊断码
                AiError error = ErrorCodeMapper.classify(e, ErrorCodeMapper.Context.generic(),
                        AiTraceSupport.currentTraceId());
                log.error("[AgentLoop] SSE 流式对话失败: class={} code={} traceId={}",
                        error.errorClass().name(), error.errorCode(), error.traceId(), e);
                sendErrorQuietly(emitter, error.toSseErrorFrame());
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

    /** 错误帧尽力下发：连接可能已关闭，下发失败只记 debug（不再向外抛，避免 SSE 线程二次异常）。 */
    private void sendErrorQuietly(SseEmitter emitter, Map<String, Object> frame) {
        try {
            sendEvent(emitter, "error", frame);
        } catch (IOException | RuntimeException ignored) {
            log.debug("[AgentLoop] SSE 错误帧下发失败（连接已关闭）");
        }
    }

    /**
     * 发送 SSE 事件。
     */
    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(data, MediaType.APPLICATION_JSON));
    }

    /**
     * 将文本按 token 粒度切分（简化版：按字符边界切块）。
     * <p>真实场景应使用 tokenizer，此处按语义边界分段发送。</p>
     */
    private List<String> splitIntoTokens(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }

        // 按段/句边界切分，每段作为一个 "token" 发送
        // 优先按段落切，再按句号切
        String[] paragraphs = content.split("(?<=\\n\\n)");
        java.util.List<String> tokens = new java.util.ArrayList<>();
        for (String para : paragraphs) {
            if (para.length() <= 80) {
                tokens.add(para);
            } else {
                // 按句子切分
                String[] sentences = para.split("(?<=[。！？\\n.?!])");
                for (String sentence : sentences) {
                    if (!sentence.isBlank()) {
                        tokens.add(sentence);
                    }
                }
            }
        }
        return tokens;
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
