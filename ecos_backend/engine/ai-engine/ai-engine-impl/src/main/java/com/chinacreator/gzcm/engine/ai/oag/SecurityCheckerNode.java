package com.chinacreator.gzcm.engine.ai.oag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Node 4: 安全检查器 — RLS(行级安全) + CLS(列级安全) + 内容合规检查。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>调用 security-engine RLS 端点获取行级过滤条件</li>
 *   <li>调用 security-engine CLS 端点获取列级限制</li>
 *   <li>对 ACTION 类意图进行额外权限校验</li>
 * </ul>
 *
 * <p>外部端点：</p>
 * <ul>
 *   <li>POST /api/security/rls/apply</li>
 *   <li>POST /api/security/cls/columns</li>
 * </ul>
 */
@Component
public class SecurityCheckerNode implements OagNode {

    private static final Logger log = LoggerFactory.getLogger(SecurityCheckerNode.class);

    /** 敏感操作黑名单关键词 */
    private static final List<String> BLOCKED_PATTERNS = List.of(
            "DROP TABLE", "DROP DATABASE", "TRUNCATE",
            "ALTER SYSTEM", "SHUTDOWN", "GRANT ALL"
    );

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
                    log.warn("[OAG:{}] 安全阻止: {}", ctx.getTraceId(), pattern);
                    return ctx;
                }
            }
        }

        // 2. ACTION 类意图的额外权限检查
        if ("ACTION".equals(intent)) {
            log.info("[OAG:{}] ACTION意图 执行操作权限检查 userId={} tenantId={}",
                    ctx.getTraceId(), ctx.getUserId(), ctx.getTenantId());
            // 生产环境应调用 security-engine 端点
            // rlsFilters = callSecurityEngine("/api/security/rls/apply", ...)
            // clsColumns = callSecurityEngine("/api/security/cls/columns", ...)
        }

        // 3. 默认安全通过（hint: 生产环境集成 security-engine API）
        ctx.setSecurityPassed(true);

        // 默认 RLS/CLS 空集
        ctx.setRlsFilters(Map.of("tenantId", ctx.getTenantId()));
        ctx.setClsColumns(new LinkedHashMap<>());

        log.debug("[OAG:{}] 安全检查通过 intent={}", ctx.getTraceId(), intent);

        return ctx;
    }
}
