package com.chinacreator.gzcm.gateway.filter;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * P2-17 租户配额过滤器 — 在每个请求进入时检查租户配额。
 *
 * <p>支持多种配额类型：
 * <ul>
 *   <li>API_CALLS: 每个 HTTP 请求递增</li>
 *   <li>STORAGE_MB: 从 X-Content-Length header 估算</li>
 *   <li>WORKBOOK_EXEC: WorkbookController.execute() 调用时递增</li>
 *   <li>AGENT_TASKS: Agent 调用时递增</li>
 * </ul>
 *
 * <p>逻辑：
 * <ol>
 *   <li>从请求头 {@code X-Tenant-Id} 提取租户ID，设置到 {@link TenantContextHolder}</li>
 *   <li>从 {@code ecos_tenant_quota} 表查询该租户的多种日配额</li>
 *   <li>从 {@code ecos_tenant_usage} 表查询当日已用量</li>
 *   <li>若 API_CALLS 已用量 >= 日配额 → 返回 429 Too Many Requests</li>
 *   <li>其他配额类型在请求结束后记录</li>
 * </ol>
 */
@Component
public class QuotaFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(QuotaFilter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;

    /**
     * 配额查询本地缓存 — 缓存每日限额，避免每次请求都查DB。
     * TTL 60s，由 A12 指标要求。
     */
    private final Cache<String, Long> quotaCache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(1000)
            .build();

    public QuotaFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        // 优先从 JWT 解析的租户上下文获取，其次从 HTTP Header 回退
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = request.getHeader("X-Tenant-Id");
        }

        // 无租户ID → 直接放行（未启用多租户隔离的请求）
        if (tenantId == null || tenantId.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            TenantContextHolder.setTenantId(tenantId);

            String today = LocalDate.now().toString();

            // P2-17: 确定配额类型
            String quotaType = determineQuotaType(request);
            String quotaTypeLabel = quotaType != null ? quotaType : "API_CALLS";

            // 从缓存获取每日限额（A12: 60s TTL本地缓存）
            String cacheKey = tenantId + ":" + quotaTypeLabel;
            Long cachedLimit = quotaCache.getIfPresent(cacheKey);
            long dailyLimit;
            if (cachedLimit != null) {
                dailyLimit = cachedLimit;
            } else {
                // P2-17: 查询该租户对应类型的日配额
                List<Map<String, Object>> quotas = jdbc.queryForList(
                    "SELECT daily_limit FROM ecos_tenant_quota WHERE tenant_id = ? AND quota_type = ?",
                    tenantId, quotaTypeLabel);
                if (quotas.isEmpty()) {
                    // 未配置该类型配额 → 只检查 API_CALLS 作为兜底，其他类型放过
                    if (!"API_CALLS".equals(quotaTypeLabel)) {
                        chain.doFilter(request, response);
                        return;
                    }
                    // API_CALLS 未配置 → 放行
                    chain.doFilter(request, response);
                    return;
                }
                dailyLimit = ((Number) quotas.get(0).get("daily_limit")).longValue();
                quotaCache.put(cacheKey, dailyLimit);
            }

            if (dailyLimit <= 0) {
                // 无限额 → 直接放行
                chain.doFilter(request, response);
                return;
            }

            // P2-17: STORAGE_MB — 从 X-Content-Length 估算
            long usageIncrement = 1;
            if ("STORAGE_MB".equals(quotaTypeLabel)) {
                String contentLength = request.getHeader("X-Content-Length");
                if (contentLength != null) {
                    try {
                        long bytes = Long.parseLong(contentLength);
                        usageIncrement = Math.max(1, bytes / (1024 * 1024)); // bytes → MB
                    } catch (NumberFormatException ignored) {
                        usageIncrement = 1;
                    }
                }
            }

            // ── B5 竞态修复: PostgreSQL 原子 UPSERT + RETURNING ──
            String atomicSql =
                "INSERT INTO ecos_tenant_usage (tenant_id, usage_date, quota_type, used_count, updated_at) " +
                "VALUES (?, ?::date, ?, ?, NOW()) " +
                "ON CONFLICT (tenant_id, usage_date, quota_type) " +
                "DO UPDATE SET used_count = ecos_tenant_usage.used_count + ?, updated_at = NOW() " +
                "WHERE ecos_tenant_usage.used_count < ? " +
                "RETURNING used_count";

            // 执行原子操作并获取新的计数值
            List<Long> result = jdbc.query(atomicSql,
                (ResultSet rs, int rowNum) -> rs.getLong("used_count"),
                tenantId, today, quotaTypeLabel, usageIncrement, usageIncrement, dailyLimit);

            long newCount;
            if (result.isEmpty()) {
                // 原子操作被 WHERE 子句阻止（已超限），查询当前值
                List<Map<String, Object>> usages = jdbc.queryForList(
                    "SELECT used_count FROM ecos_tenant_usage WHERE tenant_id = ? AND usage_date = ?::date AND quota_type = ?",
                    tenantId, today, quotaTypeLabel);
                newCount = usages.isEmpty() ? dailyLimit : ((Number) usages.get(0).get("used_count")).longValue();

                log.warn("Tenant quota exceeded: tenantId={}, type={}, used={}, limit={}", tenantId, quotaTypeLabel, newCount, dailyLimit);
                sendQuotaExceeded(response, tenantId, quotaTypeLabel, newCount, dailyLimit);
                return;
            }

            newCount = result.get(0);

            // 安全网: 检查递增后的值（理论上 RETURNING 已保证）
            if (newCount > dailyLimit) {
                log.warn("Tenant quota exceeded after atomic inc: tenantId={}, type={}, used={}, limit={}",
                        tenantId, quotaTypeLabel, newCount, dailyLimit);
                sendQuotaExceeded(response, tenantId, quotaTypeLabel, newCount, dailyLimit);
                return;
            }

            chain.doFilter(request, response);

        } catch (Exception e) {
            log.error("Quota check failed for tenantId={}: {}", tenantId, e.getMessage(), e);
            // 配额检查异常不阻塞业务，放行
            chain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    /**
     * P2-17: 根据请求路径和 Header 确定配额类型。
     */
    private String determineQuotaType(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // WORKBOOK_EXEC: Workbook 执行请求
        if (path != null && path.contains("/api/workbook/execute") && "POST".equalsIgnoreCase(method)) {
            return "WORKBOOK_EXEC";
        }

        // AGENT_TASKS: Agent 调用请求
        if (path != null && (path.contains("/api/agent/") || path.contains("/api/hermes/"))) {
            return "AGENT_TASKS";
        }

        // STORAGE_MB: 上传类请求（有 Content-Length 且超过阈值）
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            String contentLength = request.getHeader("X-Content-Length");
            if (contentLength == null) {
                contentLength = request.getHeader("Content-Length");
            }
            if (contentLength != null) {
                try {
                    long bytes = Long.parseLong(contentLength);
                    if (bytes > 1024 * 1024) { // 超过 1MB 视为存储操作
                        return "STORAGE_MB";
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // 默认: API_CALLS
        return "API_CALLS";
    }

    private void sendQuotaExceeded(HttpServletResponse response,
                                   String tenantId, String quotaType, long used, long limit) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Map<String, Object>> body = ApiResponse.error(429,
            "配额已用尽 — " + quotaType + " 当日使用量已达上限");
        Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("tenant_id", tenantId);
        detail.put("quota_type", quotaType);
        detail.put("used", used);
        detail.put("limit", limit);
        body.setData(detail);
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }
}
