package com.chinacreator.gzcm.gateway.filter;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P3-3: 安全端点速率限制过滤器
 *
 * 对安全敏感端点实施速率限制，防止暴力调用：
 * - /api/security/decrypt — 10 次/分钟
 * - /api/v1/policy-engine/evaluate — 20 次/分钟
 * - /api/v1/data-masking/apply — 10 次/分钟
 * - /api/security/mask — 10 次/分钟
 *
 * 实现方式：基于 IP + 用户ID 的滑动窗口计数器
 * 使用 ConcurrentHashMap，无需外部依赖
 */
@Component
@Order(2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 速率限制配置：路径前缀 → {限制次数, 窗口大小(毫秒)} */
    private static final Map<String, int[]> RATE_LIMITS = Map.of(
        "/api/security/decrypt", new int[]{10, 60_000},
        "/api/v1/policy-engine/evaluate", new int[]{20, 60_000},
        "/api/v1/security/policy-engine/evaluate", new int[]{20, 60_000},
        "/api/v1/data-masking/apply", new int[]{10, 60_000},
        "/api/v1/security/masking/apply", new int[]{10, 60_000},
        "/api/security/mask", new int[]{10, 60_000}
    );

    /** 滑动窗口计数器：key = "ip:userId:pathPrefix", value = {count, windowStartMs} */
    private final ConcurrentHashMap<String, long[]> counters = new ConcurrentHashMap<>();

    /** 清理间隔：每 5 分钟清理过期计数器 */
    private volatile long lastCleanup = System.currentTimeMillis();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // 检查是否需要限流
        String matchedPrefix = matchRateLimitedPath(path);
        if (matchedPrefix == null) {
            filterChain.doFilter(request, response);
            return;
        }

        // 构建限流 key: IP + userId + path
        String clientIp = getClientIp(request);
        String userId = extractUserId(request);
        String key = clientIp + ":" + userId + ":" + matchedPrefix;

        // 检查速率
        int[] limit = RATE_LIMITS.get(matchedPrefix);
        int maxRequests = limit[0];
        long windowMs = limit[1];

        if (isRateLimited(key, maxRequests, windowMs)) {
            log.warn("速率限制触发: ip={}, user={}, path={}, limit={}/min", clientIp, userId, path, maxRequests);
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", "60");
            Map<String, Object> body = Map.of(
                "code", 429,
                "message", "请求过于频繁，请稍后重试",
                "success", false,
                "retryAfter", 60
            );
            response.getWriter().write(MAPPER.writeValueAsString(body));
            return;
        }

        // 定期清理过期计数器
        periodicCleanup();

        filterChain.doFilter(request, response);
    }

    /**
     * 检查是否被限流。使用滑动窗口计数器。
     */
    private boolean isRateLimited(String key, int maxRequests, long windowMs) {
        long now = System.currentTimeMillis();
        long[] entry = counters.compute(key, (k, v) -> {
            if (v == null || now - v[1] > windowMs) {
                // 新窗口
                return new long[]{1, now};
            }
            // 窗口内计数+1
            v[0]++;
            return v;
        });
        return entry[0] > maxRequests;
    }

    /**
     * 匹配请求路径是否在限流列表中
     */
    private String matchRateLimitedPath(String path) {
        for (String prefix : RATE_LIMITS.keySet()) {
            if (path.startsWith(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    /**
     * 获取客户端真实 IP（考虑代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty()) return ip;
        return request.getRemoteAddr();
    }

    /**
     * 从 Authorization header 提取用户标识（取 token 前 16 字符作为标识）
     */
    private String extractUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ") && auth.length() > 22) {
            return auth.substring(7, 23); // 取 token 前 16 字符作为用户标识
        }
        return "anonymous";
    }

    /**
     * 定期清理过期的计数器（每 5 分钟）
     */
    private void periodicCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < 300_000) return; // 5 分钟
        lastCleanup = now;
        counters.entrySet().removeIf(entry -> {
            long[] v = entry.getValue();
            return now - v[1] > 300_000; // 清理 5 分钟内无活动的计数器
        });
        log.debug("速率限制计数器清理完成，当前活跃: {}", counters.size());
    }
}
