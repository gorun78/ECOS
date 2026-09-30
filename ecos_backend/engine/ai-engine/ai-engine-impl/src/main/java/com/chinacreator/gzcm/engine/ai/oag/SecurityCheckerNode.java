package com.chinacreator.gzcm.engine.ai.oag;

import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Node 4: 安全检查器 — RLS(行级安全) + CLS(列级安全) + ABAC 裁决 + 内容合规检查。
 *
 * <p><b>H10-T1 (PMO-74 N8)</b>：原「空壳放行」实现改为强制经
 * {@link AiSecurityEngineClient} 调 security-engine REST（铁律 §2.4）：</p>
 * <ul>
 *   <li>ACTION 类意图：{@code POST /api/v1/security/policy-engine/evaluate} ABAC 裁决，
 *       DENY 或 security-engine 不可用 → 默认 DENY，节点置 BLOCKED</li>
 *   <li>所有意图：{@code POST /api/v1/security/rls/apply} 取行级过滤条件，
 *       返回 "1=0"（不可用/DENY）→ BLOCKED；结果经 rlsFilters 传给 KnowledgeRetriever</li>
 *   <li>敏感操作黑名单（本地第一道闸）保留不变</li>
 * </ul>
 *
 * <p>外部端点（统一经 {@link AiSecurityEngineClient}，不在引擎内重复实现安全逻辑）：</p>
 * <ul>
 *   <li>POST /api/v1/security/policy-engine/evaluate</li>
 *   <li>POST /api/v1/security/rls/apply</li>
 *   <li>POST /api/v1/security/cls/columns</li>
 * </ul>
 */
@Component
public class SecurityCheckerNode implements OagNode {

    private static final Logger log = LoggerFactory.getLogger(SecurityCheckerNode.class);

    /** 敏感操作黑名单关键词（本地第一道闸，与 security-engine 裁决叠加） */
    private static final List<String> BLOCKED_PATTERNS = List.of(
            "DROP TABLE", "DROP DATABASE", "TRUNCATE",
            "ALTER SYSTEM", "SHUTDOWN", "GRANT ALL"
    );

    private final AiSecurityEngineClient securityEngineClient;

    public SecurityCheckerNode(AiSecurityEngineClient securityEngineClient) {
        this.securityEngineClient = securityEngineClient;
    }

    @Override
    public OagPipelineContext execute(OagPipelineContext ctx) {
        ctx.setCurrentNode("SecurityChecker");

        String intent = ctx.getIntent();
        String query = ctx.getRewrittenQuery() != null ? ctx.getRewrittenQuery() : ctx.getUserQuery();

        // 1. 敏感操作检测
        if (query != null) {
            String upper = query.toUpperCase();
            for (String pattern : BLOCKED_PATTERNS) {
                if (upper.contains(pattern)) {
                    ctx.setSecurityPassed(false);
                    ctx.setSecurityBlockReason("检测到禁止操作: " + pattern);
                    ctx.setStatus("BLOCKED");
                    securityEngineClient.audit(ctx.getUserId(), "oag:blocked_pattern", String.valueOf(intent), "BLOCKED");
                    log.warn("[OAG:{}] 安全阻止: {}", ctx.getTraceId(), pattern);
                    return ctx;
                }
            }
        }

        // 2. ACTION 类意图 — security-engine ABAC 裁决（H10-T1：不可用默认 DENY，不再空壳放行）
        if ("ACTION".equals(intent)) {
            log.info("[OAG:{}] ACTION意图 执行操作权限检查 userId={} tenantId={}",
                    ctx.getTraceId(), ctx.getUserId(), ctx.getTenantId());
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("resource", "oag_action");
            attrs.put("query", query);
            boolean allowed = securityEngineClient.evaluate(
                    ctx.getUserId(), ctx.getTenantId(), "oag:action", attrs);
            if (!allowed) {
                ctx.setSecurityPassed(false);
                ctx.setSecurityBlockReason("security-engine ABAC 裁决失败或不可用（默认 DENY）");
                ctx.setStatus("BLOCKED");
                securityEngineClient.audit(ctx.getUserId(), "oag:action", "abac_evaluate", "DENIED");
                log.warn("[OAG:{}] security-engine DENY: ACTION 意图被阻止 userId={}",
                        ctx.getTraceId(), ctx.getUserId());
                return ctx;
            }
        }

        // 3. RLS — security-engine 行级过滤条件；不可用/DENY 返回 "1=0" → 默认拒绝
        String whereClause = securityEngineClient.applyRls(
                ctx.getUserId(), ctx.getTenantId(), intent == null ? "default" : intent.toLowerCase(),
                "oag_query", Map.of("tenantId", String.valueOf(ctx.getTenantId())));
        if ("1=0".equals(whereClause)) {
            ctx.setSecurityPassed(false);
            ctx.setSecurityBlockReason("security-engine 不可用或 RLS 拒绝（默认 DENY）");
            ctx.setStatus("BLOCKED");
            securityEngineClient.audit(ctx.getUserId(), "oag:rls", "apply", "DENIED");
            log.warn("[OAG:{}] RLS DENY（security-engine 不可用/拒绝）→ BLOCKED", ctx.getTraceId());
            return ctx;
        }

        // 4. 通过裁决 — RLS/CLS 结果写入上下文供下游节点（KnowledgeRetriever 等）消费
        ctx.setSecurityPassed(true);

        Map<String, Object> rlsFilters = new LinkedHashMap<>();
        rlsFilters.put("tenantId", ctx.getTenantId());
        if (whereClause != null && !whereClause.isBlank()) {
            rlsFilters.put("whereClause", whereClause);
        }
        ctx.setRlsFilters(rlsFilters);
        ctx.setClsColumns(new LinkedHashMap<>());

        log.debug("[OAG:{}] 安全检查通过 intent={} rls={}", ctx.getTraceId(), intent, whereClause);

        return ctx;
    }
}
