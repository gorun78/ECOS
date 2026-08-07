package com.chinacreator.gzcm.engine.ai.oag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Node 8: 审计日志器 — 记录完整的管道执行轨迹。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>记录每个节点的输入/输出/耗时</li>
 *   <li>记录安全决策（通过/阻止+原因）</li>
 *   <li>持久化审计轨迹到日志/数据库</li>
 * </ul>
 */
@Component
public class AuditLoggerNode implements OagNode {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggerNode.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    @Override
    public OagPipelineContext execute(OagPipelineContext ctx) {
        ctx.setCurrentNode("AuditLogger");

        Map<String, Object> audit = buildAuditRecord(ctx);

        // 写入审计日志
        auditLog.info("[AUDIT] traceId={} userId={} tenantId={} intent={} status={} elapsedMs={} nodes={}",
                ctx.getTraceId(), ctx.getUserId(), ctx.getTenantId(),
                ctx.getIntent(), ctx.getStatus(), ctx.getElapsedMs(),
                audit.get("nodesVisited"));

        if (!ctx.isSecurityPassed()) {
            auditLog.warn("[AUDIT:BLOCKED] traceId={} reason={}",
                    ctx.getTraceId(), ctx.getSecurityBlockReason());
        }

        ctx.getMetadata().put("auditRecord", audit);
        ctx.setStatus("COMPLETED");

        log.info("[OAG:{}] 审计日志已记录 status={} elapsed={}ms",
                ctx.getTraceId(), ctx.getStatus(), ctx.getElapsedMs());

        return ctx;
    }

    /**
     * 构建审计记录。
     */
    private Map<String, Object> buildAuditRecord(OagPipelineContext ctx) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("traceId", ctx.getTraceId());
        record.put("sessionId", ctx.getSessionId());
        record.put("userId", ctx.getUserId());
        record.put("tenantId", ctx.getTenantId());
        record.put("intent", ctx.getIntent());
        record.put("status", ctx.getStatus());
        record.put("elapsedMs", ctx.getElapsedMs());
        record.put("nodesVisited", new String[]{
                "IntentClassifier", "ContextLoader", "QueryRewriter",
                "SecurityChecker", "KnowledgeRetriever", "ReasoningEngine",
                "ResponseCompiler", "AuditLogger"
        });
        record.put("securityPassed", ctx.isSecurityPassed());
        if (ctx.getSecurityBlockReason() != null) {
            record.put("securityBlockReason", ctx.getSecurityBlockReason());
        }
        record.put("userQuery", ctx.getUserQuery());
        record.put("finalResponseLength",
                ctx.getFinalResponse() != null ? ctx.getFinalResponse().length() : 0);
        return record;
    }
}
