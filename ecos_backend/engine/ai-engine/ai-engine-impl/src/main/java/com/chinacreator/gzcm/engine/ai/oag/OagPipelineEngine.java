package com.chinacreator.gzcm.engine.ai.oag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * OAG Pipeline 引擎 — 8步 DAG 编排器。
 *
 * <pre>
 *   1. IntentClassifier   → 意图识别
 *   2. ContextLoader      → 上下文加载
 *   3. QueryRewriter      → 查询改写
 *   4. SecurityChecker    → 安全检查 (RLS/CLS)
 *   5. KnowledgeRetriever → 知识检索
 *   6. ReasoningEngine    → LLM 推理
 *   7. ResponseCompiler   → 响应编译
 *   8. AuditLogger        → 审计日志
 * </pre>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>每个节点独立可测试，通过 {@link OagNode} 接口统一</li>
 *   <li>节点间通过 {@link OagPipelineContext} 传递状态</li>
 *   <li>安全检查不通过时提前终止（短路）</li>
 *   <li>支持 SSE 事件回调（每完成一个节点推送事件）</li>
 * </ul>
 */
@Service
public class OagPipelineEngine {

    private static final Logger log = LoggerFactory.getLogger(OagPipelineEngine.class);

    @Autowired
    private IntentClassifierNode intentClassifier;

    @Autowired
    private ContextLoaderNode contextLoader;

    @Autowired
    private QueryRewriterNode queryRewriter;

    @Autowired
    private SecurityCheckerNode securityChecker;

    @Autowired
    private KnowledgeRetrieverNode knowledgeRetriever;

    @Autowired
    private ReasoningEngineNode reasoningEngine;

    @Autowired
    private ResponseCompilerNode responseCompiler;

    @Autowired
    private AuditLoggerNode auditLogger;

    /**
     * 同步执行完整管道。
     *
     * @param userQuery       用户输入
     * @param requestParams   请求参数 (model, temperature, maxTokens 等)
     * @param userId          用户标识
     * @param tenantId        租户标识
     * @return 管道上下文（含最终结果）
     */
    public OagPipelineContext run(String userQuery, Map<String, Object> requestParams,
                                   String userId, String tenantId) {
        return run(userQuery, requestParams, userId, tenantId, null);
    }

    /**
     * 执行管道（带 SSE 回调）。
     *
     * @param userQuery       用户输入
     * @param requestParams   请求参数
     * @param userId          用户标识
     * @param tenantId        租户标识
     * @param eventCallback   每个节点完成后的回调 (节点名, 摘要)→void，null 表示不推送
     * @return 管道上下文
     */
    public OagPipelineContext run(String userQuery, Map<String, Object> requestParams,
                                   String userId, String tenantId,
                                   Consumer<Map<String, Object>> eventCallback) {
        OagPipelineContext ctx = new OagPipelineContext(userId, tenantId);
        ctx.setUserQuery(userQuery);
        ctx.setRequestParams(requestParams != null ? requestParams : new LinkedHashMap<>());

        log.info("[OAG:{}] 管道启动 query='{}' userId={} tenantId={}",
                ctx.getTraceId(), truncate(userQuery), userId, tenantId);

        try {
            // ── Step 1: 意图分类 ──────────────────────
            if (!executeNode(ctx, intentClassifier, eventCallback)) return ctx;

            // ── Step 2: 上下文加载 ────────────────────
            if (!executeNode(ctx, contextLoader, eventCallback)) return ctx;

            // ── Step 3: 查询改写 ──────────────────────
            if (!executeNode(ctx, queryRewriter, eventCallback)) return ctx;

            // ── Step 4: 安全检查 ──────────────────────
            if (!executeNode(ctx, securityChecker, eventCallback)) return ctx;

            // 安全检查不通过 → 短路，直接跳到审计
            if (!ctx.isSecurityPassed()) {
                log.warn("[OAG:{}] 安全检查不通过，跳过后续节点", ctx.getTraceId());
                ctx.setStatus("BLOCKED");
                ctx.setFinalResponse("您的请求未通过安全检查: " + ctx.getSecurityBlockReason());
                // 仍然执行审计日志
                executeNode(ctx, auditLogger, eventCallback);
                return ctx;
            }

            // ── Step 5: 知识检索 ──────────────────────
            if (!executeNode(ctx, knowledgeRetriever, eventCallback)) return ctx;

            // ── Step 6: LLM 推理 ──────────────────────
            if (!executeNode(ctx, reasoningEngine, eventCallback)) return ctx;

            // ── Step 7: 响应编译 ──────────────────────
            if (!executeNode(ctx, responseCompiler, eventCallback)) return ctx;

            // ── Step 8: 审计日志 ──────────────────────
            if (!executeNode(ctx, auditLogger, eventCallback)) return ctx;

        } catch (Exception e) {
            log.error("[OAG:{}] 管道异常 at node={}", ctx.getTraceId(), ctx.getCurrentNode(), e);
            ctx.setStatus("FAILED");
            ctx.setErrorMessage(e.getMessage());
            ctx.setFinalResponse("管道执行异常: " + e.getMessage());
        }

        log.info("[OAG:{}] 管道完成 status={} elapsed={}ms",
                ctx.getTraceId(), ctx.getStatus(), ctx.getElapsedMs());

        return ctx;
    }

    /**
     * 执行单个节点，捕获异常并推送事件。
     *
     * @return true 继续执行，false 中断
     */
    private boolean executeNode(OagPipelineContext ctx, OagNode node,
                                 Consumer<Map<String, Object>> eventCallback) {
        try {
            long start = System.currentTimeMillis();
            node.execute(ctx);
            long elapsed = System.currentTimeMillis() - start;

            if (eventCallback != null) {
                Map<String, Object> event = ctx.toSummary();
                event.put("node", node.name());
                event.put("nodeElapsedMs", elapsed);
                eventCallback.accept(event);
            }

            log.debug("[OAG:{}] {} 完成 ({}ms)", ctx.getTraceId(), node.name(), elapsed);
            return true;
        } catch (Exception e) {
            log.error("[OAG:{}] {} 执行失败", ctx.getTraceId(), node.name(), e);
            ctx.setStatus("FAILED");
            ctx.setErrorMessage(node.name() + ": " + e.getMessage());
            return false;
        }
    }

    private String truncate(String s) {
        if (s == null) return "null";
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }
}
