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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器 — 从 Authorization 请求头提取 Bearer Token，
 * 解析并验证 JWT，若有效则查询数据库加载用户权限并设置 SecurityContext。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** 从 TD_USER_ROLE + TD_ROLE_PERMISSION + TD_PERMISSION 查询用户权限码 */
    private static final String PERMISSION_QUERY =
        "SELECT DISTINCT p.\"PERMISSION_CODE\" " +
        "FROM TD_USER_ROLE ur " +
        "JOIN TD_ROLE_PERMISSION rp ON ur.\"ROLE_ID\" = rp.\"ROLE_ID\" " +
        "JOIN TD_PERMISSION p ON rp.\"PERMISSION_ID\" = p.\"PERMISSION_ID\" " +
        "WHERE ur.\"USER_ID\" = ?";

    private final JwtTokenProvider jwtTokenProvider;
    private final JdbcTemplate jdbcTemplate;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, JdbcTemplate jdbcTemplate) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String path = request.getRequestURI();
        String authHeader = request.getHeader("Authorization");

        // 无 Token 或非 Bearer 类型 → 放行
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        // 公开端点（登录/健康检查）— 即使携带过期 Token 也放行
        if (path.startsWith("/api/v1/auth/") || path.equals("/api/health") || path.equals("/health")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = jwtTokenProvider.validateToken(token);

            // 验证 token 类型为 access
            String tokenType = claims.get("type", String.class);
            if (!"access".equals(tokenType)) {
                log.warn("Invalid token type: {} for subject {}", tokenType, claims.getSubject());
                sendUnauthorized(response, "Token类型无效");
                return;
            }

            // 构建 Authentication
            String userId = claims.getSubject();

            // 从 JWT claims 中提取角色列表（如 ["ROLE_SUPER_ADMIN","admin","SECURITY_AUDITOR"]）
            List<String> tokenRoles = extractRolesFromClaims(claims);
            log.debug("Extracted {} roles from JWT for userId={}", tokenRoles.size(), userId);

            // 从数据库加载用户权限码 (permission_code)
            List<String> permissionCodes = loadUserPermissions(userId);
            log.debug("Loaded {} permissions from DB for userId={}", permissionCodes.size(), userId);

            // 合并角色和权限码，去重后构建 SimpleGrantedAuthority
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
            context.setUsername(claims.get("username", String.class));
            context.setTenantId(claims.get("tenant_id", String.class));
            UserContext.setCurrent(context);
            
            // 设置租户上下文 (供 QuotaFilter 等下游使用)
            String tenantId = claims.get("tenant_id", String.class);
            if (tenantId == null || tenantId.isBlank()) {
                // Fallback: 从DB读取（JWT不含tenant_id时的兜底）
                try {
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT \"TENANT_ID\" FROM TD_USER WHERE \"ID\" = ?", userId);
                    if (rows != null && !rows.isEmpty()) {
                        Object tid = rows.get(0).get("TENANT_ID");
                        tenantId = tid != null ? tid.toString() : null;
                    }
                } catch (Exception ex) {
                    log.debug("Failed to query tenant for user {}: {}", userId, ex.getMessage());
                }
                if (tenantId == null || tenantId.isBlank()) {
                    tenantId = "tenant-a"; // 默认租户
                }
            }
            if (tenantId != null && !tenantId.isBlank()) {
                TenantContextHolder.setTenantId(tenantId);
                context.setTenantId(tenantId);
            }

            log.debug("JWT authenticated: userId={}, authorities={}", userId, allAuthorities);

        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            sendUnauthorized(response, "Token无效或已过期");
            return;
        }

        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(ApiResponse.unauthorized(message).toJson());
    }

    /**
     * 从数据库加载用户权限码列表。
     * <p>
     * 通过 TD_USER_ROLE → TD_ROLE_PERMISSION → TD_PERMISSION 三表联查，
     * 获取用户所有角色关联的 permission_code。
     *
     * @param userId 用户ID
     * @return 权限码列表 (如 ["user:READ", "user:WRITE", ...])
     */
    private List<String> loadUserPermissions(String userId) {
        try {
            return jdbcTemplate.queryForList(PERMISSION_QUERY, String.class, userId);
        } catch (Exception e) {
            log.error("Failed to load permissions for userId={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从 JWT Claims 中提取角色列表。
     * <p>
     * Token 中 roles 字段格式: ["ROLE_SUPER_ADMIN", "admin", "SECURITY_AUDITOR"]
     * 这些角色直接作为 Spring Security 的 GrantedAuthority 使用。
     *
     * @param claims JWT 解析后的载荷
     * @return 角色列表，若 claims 中无 roles 字段则返回空列表
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRolesFromClaims(Claims claims) {
        try {
            Object rolesObj = claims.get("roles");
            if (rolesObj instanceof List) {
                List<?> rawList = (List<?>) rolesObj;
                List<String> roles = new ArrayList<>();
                for (Object item : rawList) {
                    if (item != null) {
                        roles.add(item.toString());
                    }
                }
                return roles;
            }
        } catch (Exception e) {
            log.warn("Failed to extract roles from JWT claims: {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}
