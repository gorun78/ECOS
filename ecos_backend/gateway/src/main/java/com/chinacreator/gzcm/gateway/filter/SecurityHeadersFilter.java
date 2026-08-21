package com.chinacreator.gzcm.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * P3-2: 安全 HTTP 响应头过滤器
 *
 * 为所有响应添加标准安全头，防止常见的 Web 攻击：
 * - X-Content-Type-Options: nosniff — 防止 MIME 类型嗅探
 * - X-Frame-Options: DENY — 防止点击劫持（iframe 嵌入）
 * - Strict-Transport-Security — 强制 HTTPS（生产环境）
 * - Content-Security-Policy — 限制资源加载来源
 * - X-XSS-Protection — XSS 过滤
 * - Referrer-Policy — 限制 Referrer 泄露
 * - Cache-Control — 敏感端点禁止缓存
 */
@Component
@Order(1)  // 最高优先级，确保安全头最先设置
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        // 防止 MIME 类型嗅探
        response.setHeader("X-Content-Type-Options", "nosniff");

        // 防止点击劫持
        response.setHeader("X-Frame-Options", "DENY");

        // 强制 HTTPS（1年，含子域名）
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");

        // 内容安全策略 — 限制资源加载来源
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                "style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data: https:; " +
                "font-src 'self' data:; " +
                "connect-src 'self' ws: wss:; " +
                "frame-ancestors 'none'");

        // XSS 保护（旧浏览器兼容）
        response.setHeader("X-XSS-Protection", "1; mode=block");

        // Referrer 策略 — 仅同源发送完整 Referrer
        response.setHeader("Referrer-Policy", "same-origin");

        // 权限策略 — 禁用不需要的浏览器功能
        response.setHeader("Permissions-Policy",
                "geolocation=(), microphone=(), camera=(), payment=()");

        // 敏感端点禁止缓存
        String path = request.getRequestURI();
        if (isSensitivePath(path)) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            response.setHeader("Expires", "0");
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 判断是否为敏感路径（需要禁止缓存的端点）
     */
    private boolean isSensitivePath(String path) {
        return path != null && (
            path.contains("/auth/") ||
            path.contains("/security/") ||
            path.contains("/abac/") ||
            path.contains("/audit/") ||
            path.contains("/data-masking/") ||
            path.contains("/policy-engine/") ||
            path.contains("/decrypt") ||
            path.contains("/data-permission/")
        );
    }
}
