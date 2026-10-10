package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * <h3>W06（详细设计-00 C.1.3/C.2.4，M0）失败语义收紧 — 默认 DENY 唯一实现口径</h3>
 * <ul>
 *   <li>签名无效/过期/类型错 → 401 ECOS-AUTH-002/003（不透传 X-ECOS-*）</li>
 *   <li>jti 命中黑名单 → 401 ECOS-AUTH-004</li>
 *   <li><b>黑名单查询异常（DB 故障）→ 503 ECOS-AUTH-005 fail-closed</b>（原 return false = fail-open）</li>
 *   <li><b>JWT 缺 tenant_id 且回查失败 → 403 ECOS-AUTH-006</b>（删除 "tenant-a" 硬编码兜底）</li>
 * </ul>
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
                sendRejected(response, 401, "ECOS-AUTH-003", "Token类型无效");
                return;
            }

            // ── T5: 检查 Token 是否在黑名单中 (forceLogout) —— DB 异常 fail-closed（W06）──
            String jti = claims.getId();
            String userId = claims.getSubject();
            if (jti != null && isTokenBlacklisted(jti)) {
                log.warn("Token已被强制踢出: userId={}, jti={}", userId, jti);
                sendRejected(response, 401, "ECOS-AUTH-004", "Token已被强制踢出，请重新登录");
                return;
            }

            // 构建 Authentication

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
            // Access Token 未签发 username claim（JwtTokenProvider 仅写 sub/roles/tenant_id），
            // 缺失时回查 TD_USER 补全，避免下游按登录态取用户名时恒为 null（提案 author/reviewer 等）
            String username = claims.get("username", String.class);
            if (username == null || username.isBlank()) {
                username = loadUsername(userId);
            }
            context.setUsername(username);

            // ── W06：租户 claim 优先；claim 与回查皆无 → 403 ECOS-AUTH-006（删 tenant-a 兜底）──
            String tenantId = claims.get("tenant_id", String.class);
            if (tenantId == null || tenantId.isBlank()) {
                tenantId = loadTenantId(userId);
            }
            if (tenantId == null || tenantId.isBlank()) {
                log.warn("用户未绑定租户，拒绝登录: userId={}", userId);
                SecurityContextHolder.clearContext();
                UserContext.clear();
                sendRejected(response, 403, "ECOS-AUTH-006", "用户未绑定租户");
                return;
            }
            context.setTenantId(tenantId);
            UserContext.setCurrent(context);

            // 设置租户上下文 (供 QuotaFilter 等下游使用)
            TenantContextHolder.setTenantId(tenantId);

            log.debug("JWT authenticated: userId={}, tenant={}, authorities={}", userId, tenantId, allAuthorities);

        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();
            UserContext.clear();
            sendRejected(response, 401, "ECOS-AUTH-002", "Token已过期");
            return;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            UserContext.clear();
            sendRejected(response, 401, "ECOS-AUTH-002", "Token无效或已过期");
            return;
        } catch (TokenBlacklistUnavailableException e) {
            // W06 fail-closed：黑名单依赖不可用 → 503（不再按未拉黑放行）
            log.error("token 黑名单不可用（DB 故障），fail-closed 拒绝: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            UserContext.clear();
            sendRejected(response, 503, "ECOS-AUTH-005", "认证依赖不可用，请稍后重试");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * 按 userId 回查用户名（Token 缺 username claim 时的兜底，查不到则退回 userId 以保证身份非空）。
     * <p>注意：public.td_user 的列名为大写（USER_ID/USERNAME），需带双引号限定。
     *
     * @param userId JWT subject（用户ID）
     * @return 用户名，查询失败时返回 userId
     */
    private String loadUsername(String userId) {
        try {
            List<String> names = jdbcTemplate.queryForList(
                "SELECT \"USERNAME\" FROM td_user WHERE \"USER_ID\" = ?", String.class, userId);
            if (names != null && !names.isEmpty() && names.get(0) != null) {
                return names.get(0);
            }
        } catch (Exception e) {
            log.debug("Failed to resolve username for userId={}: {}", userId, e.getMessage());
        }
        return userId;
    }

    /** W06：按 userId 回查租户（JWT 不含 tenant_id 时的兜底；失败返回 null → 上层 403） */
    protected String loadTenantId(String userId) {
        try {
            // 登录身份表 = users（V245 起带 tenant_id）；td_user 是组织侧遗留表，无此列
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT tenant_id FROM users WHERE id = ?", userId);
            if (rows != null && !rows.isEmpty()) {
                Object tid = rows.get(0).get("tenant_id");
                return tid != null ? tid.toString() : null;
            }
        } catch (Exception ex) {
            log.warn("回查租户失败 userId={}: {}（按默认 DENY 口径交上层 403）", userId, ex.getMessage());
        }
        return null;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        sendRejected(response, 401, "ECOS-AUTH-001", message);
    }

    /** C.1.3 统一拒绝包络：status + ECOS-AUTH-### errorCode（D.5.2 错误码总表） */
    private void sendRejected(HttpServletResponse response, int status, String errorCode, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        if (status == 401) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.getWriter().write(ApiResponse.error(status, errorCode, message).toJson());
    }

    /**
     * 检查 token jti 是否在黑名单中（已被强制踢出）。
     * <p>W06：查询异常 <b>抛出</b> {@link TokenBlacklistUnavailableException} → 上层 503
     * fail-closed；原 return false（= DB 故障时已拉黑 token 仍可通行，fail-open）。
     * <p>测试注入点：子类可覆写本方法模拟黑名单查询。
     */
    protected boolean isTokenBlacklisted(String jti) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ecos_token_blacklist WHERE jti = ? AND expires_at > NOW()",
                Integer.class, jti);
            return count != null && count > 0;
        } catch (DataAccessException e) {
            throw new TokenBlacklistUnavailableException(jti, e);
        }
    }

    /** W06 专用：黑名单依赖不可用（触发 fail-closed 503） */
    public static class TokenBlacklistUnavailableException extends RuntimeException {
        public TokenBlacklistUnavailableException(String jti, Throwable cause) {
            super("token blacklist unavailable for jti=" + jti, cause);
        }
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
