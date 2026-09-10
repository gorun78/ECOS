package com.chinacreator.gzcm.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * 版本前缀重写过滤器。
 * <p>
 * 将 {@code /api/v1/<prefix>/**} 请求路径重写为 {@code /api/<prefix>/**}，
 * 使无 {@code /v1/} 前缀的旧端点也能通过 {@code /v1/} 路径访问。
 * <p>
 * 使用 {@link HttpServletRequestWrapper} 修改请求 URI，不改变请求分发流程。
 *
 * @author ECOS Sprint 架构债清理 T5
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class VersionPrefixRewriteFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(VersionPrefixRewriteFilter.class);

    /**
     * v1 路径前缀 → 实际映射路径前缀。
     * key 必须以 "/" 结尾以确保完整前缀匹配。
     */
    private static final Map<String, String> V1_REWRITE_MAP = Map.ofEntries(
        // PMO-48-A T3: /api/v1/dq/ KEEP（对齐 pipeline 模式）— 删除原 v1→去v1 REMOVE 条目；
        // 新 DqGovernanceController 直接映射 /api/v1/dq/**，若 rewrite 成 /api/dq/* 将 404。
        Map.entry("/api/v1/agent-mesh/",   "/api/agent-mesh/"),
        // PMO-3J: /api/v1/pipeline/ rewrite removed — new PipelineController
        // is mapped at /api/v1/pipeline directly (no legacy /api/pipeline controller
        // exists), so rewriting v1→no-v1 caused 404 on /api/v1/pipeline/definitions.
        // Removed /api/v1/knowledge/ rewrite — new KnowledgeApiController handles /api/v1/knowledge/** directly
        Map.entry("/api/v1/twins/",        "/api/twins/"),
        Map.entry("/api/v1/alerts/",       "/api/alerts/"),
        Map.entry("/api/v1/portal/",       "/api/portal/"),
        Map.entry("/api/v1/agent-call/",   "/api/agent-call/"),
        Map.entry("/api/v1/glossary/",     "/api/glossary/"),
        // KEEP /api/v1/pipeline/ — PipelineController 直接映射 /api/v1/pipeline/**，
        // 无 legacy /api/pipeline 副本，故不做 v1→去v1 重写（与上方 PMO-3J 注释一致）。
        // KEEP /api/v1/pipeline/debug — PipelineDebugController/PipelineDebugLogController
        // 直接映射 /api/v1/pipeline/debug/**（断点调试 Panel 双路径，与 pipeline 同规则）；
        // /api/pipeline/debug 当前无对应 Controller，仅在三滤波器豁免清单中出现以
        // 与 PipelineController 同粒度（防止未来新增裸路径 Controller 时漏配三滤波器）。
        // ── PMO-45 T5: /api/v1/datasource/ → /datasource/ (PMO45DataSourceController 裸路径) ──
        Map.entry("/api/v1/datasource/",   "/datasource/"),
        // ── PMO-38 T5: 新增两条 v1 前缀正/反向重写 ──
        // /api/v1/knowledge-bases → /api/knowledge-bases (Controller 双路径)
        Map.entry("/api/v1/knowledge-bases",  "/api/knowledge-bases"),
        // /api/v1/sysconfig/ → /api/sysconfig/ (T4 双路径)
        Map.entry("/api/v1/sysconfig/",     "/api/sysconfig/"),
        Map.entry("/api/v1/marketplace/",   "/api/marketplace/")
    );

    /**
     * 反向重写映射：无 v1 前缀路径 → v1 路径。
     * 使前端 /api/agent/* 能到达后端 /api/v1/agent/* 控制器。
     * 分两层：精确匹配优先于前缀匹配（Map.ofEntries 不保证迭代顺序）。
     */
    private static final Map<String, String> REVERSE_EXACT_MAP = Map.ofEntries(
        Map.entry("/api/agent/agents",     "/api/v1/agents"),
        Map.entry("/api/agent/models",     "/api/v1/agents/models"),
        Map.entry("/api/agent/prompts",    "/api/v1/agents/prompts"),
        Map.entry("/api/alerts",           "/api/v1/alerts"),
        // ── PMO-38 T5: 新增反向重写（/api/ → /api/v1/）──
        Map.entry("/api/knowledge-bases",  "/api/v1/knowledge-bases"),
        Map.entry("/api/sysconfig/",       "/api/v1/sysconfig/"),
        // ── PMO-45 T5: /datasource/ → /api/v1/datasource/ 反向重写 ──
        Map.entry("/datasource/",          "/api/v1/datasource/")
    );

    private static final Map<String, String> REVERSE_PREFIX_MAP = Map.ofEntries(
        Map.entry("/api/agents/",          "/api/v1/agents/"),
        Map.entry("/api/agent/",           "/api/v1/agent/"),
        Map.entry("/api/agent-call/",      "/api/v1/agent-call/"),
        Map.entry("/api/alerts/",          "/api/v1/alerts/"),
        Map.entry("/api/portal/",          "/api/v1/portal/"),
        // ── PMO-48-A T2: /api/dq/ → /api/v1/dq/ 反向重写 (DQ 三滤波器, 与 pipeline 同粒度) ──
        Map.entry("/api/dq/",              "/api/v1/dq/")
    );

    /** 检查给定路径是否需要反向重写 */
    private static boolean needsReverseRewrite(String path) {
        for (String prefix : REVERSE_EXACT_MAP.keySet()) {
            if (path.startsWith(prefix)) return true;
        }
        for (String prefix : REVERSE_PREFIX_MAP.keySet()) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1) v1 → 去v1 正向重写: /api/v1/X/ → /api/X/
        for (Map.Entry<String, String> entry : V1_REWRITE_MAP.entrySet()) {
            String v1Prefix = entry.getKey();
            if (path.startsWith(v1Prefix)) {
                String rewrittenPath = entry.getValue() + path.substring(v1Prefix.length());
                log.debug("Path rewrite (v1→no-v1): {} → {}", path, rewrittenPath);
                HttpServletRequest wrapper = new PathRewriteRequestWrapper(request, rewrittenPath);
                filterChain.doFilter(wrapper, response);
                return;
            }
        }

        // 2) 无v1 → v1 反向重写（精确匹配优先）
        // 精确匹配
        for (Map.Entry<String, String> entry : REVERSE_EXACT_MAP.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                String rewrittenPath = entry.getValue() + path.substring(entry.getKey().length());
                log.debug("Path rewrite (exact): {} → {}", path, rewrittenPath);
                HttpServletRequest wrapper = new PathRewriteRequestWrapper(request, rewrittenPath);
                filterChain.doFilter(wrapper, response);
                return;
            }
        }
        // 前缀匹配
        for (Map.Entry<String, String> entry : REVERSE_PREFIX_MAP.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                String rewrittenPath = entry.getValue() + path.substring(entry.getKey().length());
                log.debug("Path rewrite (prefix): {} → {}", path, rewrittenPath);
                HttpServletRequest wrapper = new PathRewriteRequestWrapper(request, rewrittenPath);
                filterChain.doFilter(wrapper, response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 需要处理的路径：包含 /api/v1/ 的，或匹配反向映射前缀的
        if (path.contains("/api/v1/")) return false;
        return !needsReverseRewrite(path);
    }

    /**
     * 重写请求 URI 的 HttpServletRequestWrapper。
     */
    private static class PathRewriteRequestWrapper extends HttpServletRequestWrapper {

        private final String rewrittenPath;

        PathRewriteRequestWrapper(HttpServletRequest request, String rewrittenPath) {
            super(request);
            this.rewrittenPath = rewrittenPath;
        }

        @Override
        public String getRequestURI() {
            return rewrittenPath;
        }

        @Override
        public String getServletPath() {
            return rewrittenPath;
        }
    }
}
