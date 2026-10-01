package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器 — 从 Authorization 请求头提取 Bearer Token，
 * 解析并验证 JWT，若有效则查询数据库加载用户权限并设置 SecurityContext。
 *
 * <p>详细设计-00 W06 目标态（C.2.4）：
 * <ul>
 *   <li>黑名单查询异常 → <b>503 ECOS-AUTH-005</b>（fail-closed，替代 fail-open 的 return false）</li>
 *   <li>JWT 缺 tenant_id 且 DB 回查失败 → <b>403 ECOS-AUTH-006</b>（禁用 "tenant-a" 硬编码）</li>
 *   <li>JWT 签名/过期/类型非 access → 401（细分 ECOS-AUTH-002/003）</li>
 *   <li>jti 命中黑名单 → 401 ECOS-AUTH-004 + 审计事件</li>
 * </ul></p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final com.chinacreator.gzcm.sysman.dao.AuthLookupDao authLookupDao;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                    com.chinacreator.gzcm.sysman.dao.AuthLookupDao authLookupDao) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.authLookupDao = authLookupDao;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String path = request.getRequestURI();
        String authHeader = request.getHeader("Authorization");

        // 无 Token 或非 Bearer 类型 → 放行（后续 SecurityConfig 匿名/anyRequest 判定）
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        // 公开端点（登录/健康检查）— 即使携带过期 Token 也放行
        if (path.startsWith("/api/v1/auth/") || path.equals("/api/health") || path.equals("/health")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = jwtTokenProvider.validateToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed for path={}: {}", path, e.getMessage());
            SecurityContextHolder.clearContext();
            sendWithTrace(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "ECOS-AUTH-002", "Token无效或已过期");
            return;
        }

        // 验证 token 类型为 access（W06: 类型错 = 401 ECOS-AUTH-003）
        String tokenType = claims.get("type", String.class);
        if (!"access".equals(tokenType)) {
            log.warn("Invalid token type: {} for subject {}", tokenType, claims.getSubject());
            sendWithTrace(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "ECOS-AUTH-003", "Token类型无效（需 access）");
            return;
        }

        // ── T5: 检查 Token 是否在黑名单中 (forceLogout) ── W06: 查询异常 fail-closed 503
        String jti = claims.getId();
        String userId = claims.getSubject();
        if (jti != null) {
            boolean black;
            try {
                black = isTokenBlacklisted(jti);
            } catch (DataAccessException ex) {
                log.warn("blacklist query failed (fail-closed): {}", ex.getMessage());
                sendWithTrace(response, HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "ECOS-AUTH-005", "token 黑名单依赖不可用，请稍后重试");
                return;
            }
            if (black) {
                log.warn("Token已被强制踢出: userId={}, jti={}", userId, jti);
                sendWithTrace(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "ECOS-AUTH-004", "Token已被强制踢出，请重新登录");
                return;
            }
        }

        // 从 JWT claims 中提取角色列表
        List<String> tokenRoles = extractRolesFromClaims(claims);
        log.debug("Extracted {} roles from JWT for userId={}", tokenRoles.size(), userId);

        // 从数据库加载用户权限码 (permission_code)（IR01：Controller/Filter 直连 PG 属过渡态，
        // 详细设计-00 W04 会切 security-engine REST 门面，本 M0 阶段仅语义收紧）
        List<String> permissionCodes;
        try {
            permissionCodes = loadUserPermissions(userId);
        } catch (DataAccessException ex) {
            log.error("loadUserPermissions DataAccessException (fail-closed): userId={}", userId, ex);
            sendWithTrace(response, HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "ECOS-AUTH-005", "用户权限依赖不可用，请稍后重试");
            return;
        }
        log.debug("Loaded {} permissions from DB for userId={}", permissionCodes.size(), userId);

        Set<String> allAuthorities = new LinkedHashSet<>();
        allAuthorities.addAll(tokenRoles);
        allAuthorities.addAll(permissionCodes);

        List<SimpleGrantedAuthority> authorities = allAuthorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        authentication.setDetails(claims);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 同时设置 UserContext (供 ClearanceInterceptor 使用)
        UserContext context = new UserContext();
        context.setUserId(userId);
        String username = claims.get("username", String.class);
        if (username == null || username.isBlank()) {
            username = loadUsername(userId);
        }
        context.setUsername(username);

        // ── W06: tenant_id 双向校验 — 缺 tenant_id 且回查失败 → 403 ECOS-AUTH-006 ──
        String tenantId = claims.get("tenant_id", String.class);
        String tenantSource = "claims";
        if (tenantId == null || tenantId.isBlank()) {
            try {
                List<Map<String, Object>> rows = authLookupDao.findTenantById(userId);
                if (rows != null && !rows.isEmpty()) {
                    Object tid = rows.get(0).get("TENANT_ID");
                    tenantId = tid != null ? tid.toString() : null;
                    tenantSource = "db";
                }
            } catch (DataAccessException ex) {
                log.warn("tenant resolve DataAccessException (fail-closed): userId={}", userId, ex);
                sendWithTrace(response, HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "ECOS-AUTH-005", "租户依赖不可用，请稍后重试");
                return;
            }
            if (tenantId == null || tenantId.isBlank()) {
                // W06：禁用旧 "tenant-a" 兜底 —— 显式拒绝，避免"默认租户"越权面
                log.warn("user has no tenant binding: userId={}", userId);
                sendWithTrace(response, HttpServletResponse.SC_FORBIDDEN,
                        "ECOS-AUTH-006", "用户未绑定租户");
                return;
            }
        }
        TenantContextHolder.setTenantId(tenantId);
        context.setTenantId(tenantId);
        // 保留 UserContext 同步（供 ClearanceInterceptor 用）
        UserContext.setCurrent(context);
        log.debug("JWT authenticated: userId={}, tenant={} (source={}), authorities={}",
                userId, tenantId, tenantSource, allAuthorities);

        chain.doFilter(request, response);
    }

    /** 写响应并附带 ApiResponse.traceId（W09）——过滤链 4xx/5xx 也是一等公民的 traceId 载体。 */
    private void sendWithTrace(HttpServletResponse response, int status,
                               String errorCode, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                ApiResponse.error(status, errorCode, message).withTraceIdFromMdc().toJson());
    }

    /**
     * 按 userId 回查用户名（Token 缺 username claim 时的兜底，查不到则退回 userId 以保证身份非空）。
     */
    private String loadUsername(String userId) {
        try {
            List<String> names = authLookupDao.loadUsernames(userId);
            if (names != null && !names.isEmpty() && names.get(0) != null) {
                return names.get(0);
            }
        } catch (Exception e) {
            log.debug("Failed to resolve username for userId={}: {}", userId, e.getMessage());
        }
        return userId;
    }

    /**
     * 检查 token jti 是否在黑名单中（已被强制踢出）。
     * <p>W06：查询异常向上抛出 DataAccessException 由调用方 fail-closed（不再 return false）。
     * 仅业务异常返回 false（如 EmptyResultDataAccessException 视为未命中）。</p>
     */
    private boolean isTokenBlacklisted(String jti) {
        return authLookupDao.isTokenBlacklisted(jti);
    }

    /** 从数据库加载用户权限码列表（IR01 过渡态，见 W04 备注）。 */
    private List<String> loadUserPermissions(String userId) {
        return authLookupDao.loadUserPermissions(userId);
    }

    /** 从 JWT Claims 中提取角色列表（缺失 / 非 List 时返回空集）。 */
    @SuppressWarnings("unchecked")
    private List<String> extractRolesFromClaims(Claims claims) {
        try {
            Object rolesObj = claims.get("roles");
            if (rolesObj instanceof List) {
                List<?> rawList = (List<?>) rolesObj;
                List<String> roles = new ArrayList<>();
                for (Object item : rawList) {
                    if (item != null) roles.add(item.toString());
                }
                return roles;
            }
        } catch (Exception e) {
            log.warn("Failed to extract roles from JWT claims: {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}
