package com.chinacreator.gzcm.engine.ai.oag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * OAG Pipeline 上下文 — 流经8个节点的共享状态。
 *
 * <pre>
 *   IntentClassifier(1) → ContextLoader(2) → QueryRewriter(3)
 *   → SecurityChecker(4) → KnowledgeRetriever(5) → Reasoning(6)
 *   → ResponseCompiler(7) → AuditLogger(8)
 * </pre>
 */
public class OagPipelineContext {

    // ── 请求元数据 ──────────────────────────────
    private final String traceId;
    private final String sessionId;
    private final String userId;
    private final String tenantId;

    // ── 输入 ────────────────────────────────────
    private String userQuery;
    private Map<String, Object> requestParams;

    // ── 节点输出（中间产物） ─────────────────────
    private String intent;                // Node1: 意图
    private Map<String, Object> context;  // Node2: 上下文
    private String rewrittenQuery;        // Node3: 改写后的查询
    private boolean securityPassed;       // Node4: 安全检查通过
    private String securityBlockReason;   // Node4: 阻止原因
    private Map<String, Object> rlsFilters;  // Node4: RLS 过滤条件
    private Map<String, Object> clsColumns;  // Node4: CLS 列限制
    private Map<String, Object> knowledgeResult; // Node5: 知识检索结果
    private Map<String, Object> reasoningResult; // Node6: 推理结果
    private String finalResponse;         // Node7: 最终响应

    // ── 管道元数据 ──────────────────────────────
    private Map<String, Object> metadata;
    private long startTime;
    private String currentNode;
    private String status;      // RUNNING, COMPLETED, BLOCKED, FAILED
    private String errorMessage;

    public OagPipelineContext(String userId, String tenantId) {
        this.traceId = "oag-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        this.sessionId = "oag-sess-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.userId = userId;
        this.tenantId = tenantId;
        this.metadata = new LinkedHashMap<>();
        this.startTime = System.currentTimeMillis();
        this.status = "RUNNING";
    }

    // ── getter / setter ──────────────────────────

    public String getTraceId() { return traceId; }
    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getTenantId() { return tenantId; }

    public String getUserQuery() { return userQuery; }
    public void setUserQuery(String userQuery) { this.userQuery = userQuery; }

    public Map<String, Object> getRequestParams() { return requestParams; }
    public void setRequestParams(Map<String, Object> requestParams) { this.requestParams = requestParams; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    public String getRewrittenQuery() { return rewrittenQuery; }
    public void setRewrittenQuery(String rewrittenQuery) { this.rewrittenQuery = rewrittenQuery; }

    public boolean isSecurityPassed() { return securityPassed; }
    public void setSecurityPassed(boolean securityPassed) { this.securityPassed = securityPassed; }

    public String getSecurityBlockReason() { return securityBlockReason; }
    public void setSecurityBlockReason(String securityBlockReason) { this.securityBlockReason = securityBlockReason; }

    public Map<String, Object> getRlsFilters() { return rlsFilters; }
    public void setRlsFilters(Map<String, Object> rlsFilters) { this.rlsFilters = rlsFilters; }

    public Map<String, Object> getClsColumns() { return clsColumns; }
    public void setClsColumns(Map<String, Object> clsColumns) { this.clsColumns = clsColumns; }

    public Map<String, Object> getKnowledgeResult() { return knowledgeResult; }
    public void setKnowledgeResult(Map<String, Object> knowledgeResult) { this.knowledgeResult = knowledgeResult; }

    public Map<String, Object> getReasoningResult() { return reasoningResult; }
    public void setReasoningResult(Map<String, Object> reasoningResult) { this.reasoningResult = reasoningResult; }

    public String getFinalResponse() { return finalResponse; }
    public void setFinalResponse(String finalResponse) { this.finalResponse = finalResponse; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public long getStartTime() { return startTime; }

    public String getCurrentNode() { return currentNode; }
    public void setCurrentNode(String currentNode) { this.currentNode = currentNode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    /**
     * 计算管道总耗时（ms）。
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 将上下文转为可序列化的 Map（用于 SSE 事件）。
     */
    public Map<String, Object> toSummary() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("traceId", traceId);
        m.put("sessionId", sessionId);
        m.put("userId", userId);
        m.put("tenantId", tenantId);
        m.put("status", status);
        m.put("currentNode", currentNode);
        m.put("intent", intent);
        m.put("securityPassed", securityPassed);
        m.put("elapsedMs", getElapsedMs());
        if (errorMessage != null) m.put("errorMessage", errorMessage);
        return m;
    }
}
