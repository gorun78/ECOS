package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.context.TenantContext;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Map;

/**
 * 准入等级拦截器 — 在每个请求到达 Controller 之前校验用户的 clearanceLevel。
 * <p>
 * 优先级：User级配置 > Role级配置 > 全局默认
 * </p>
 */
@Component
public class ClearanceInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ClearanceInterceptor.class);

    /** 等级名称映射 */
    private static final String[] LEVEL_NAMES = {"L0公开", "L1内部", "L2保密", "L3机密", "L4绝密"};

    /** 路径前缀默认等级 */
    private static final String[][] PATH_RULES = {
        {"/api/v1/security/", "2"},
        {"/api/v1/system/",   "3"},
        {"/api/v1/",          "1"},
    };

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** ThreadLocal 保存当前请求，供辅助方法获取 orgId/tenantId */
    private final ThreadLocal<HttpServletRequest> currentRequest = new ThreadLocal<>();

    public ClearanceInterceptor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {

        // 非 Controller 方法（如静态资源）直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 保存当前请求供辅助方法使用
        currentRequest.set(request);

        try {
            return doPreHandle(request, response, handlerMethod);
        } finally {
            currentRequest.remove();
        }
    }

    private boolean doPreHandle(HttpServletRequest request, HttpServletResponse response,
                                 HandlerMethod handlerMethod) throws Exception {

        String path = request.getRequestURI();

        // 公开端点免检查
        if (path.startsWith("/api/v1/auth/") || path.equals("/api/health") || path.equals("/health")
                || path.startsWith("/api/v1/ecos/world-model")
                || path.startsWith("/api/v1/worldmodel")
                || path.startsWith("/api/v1/knowledge")
                || path.startsWith("/api/v1/guardrails")
                || path.startsWith("/api/v1/pipeline")
                || path.startsWith("/api/v1/agents")
                || path.startsWith("/api/v1/aip")
                || path.startsWith("/api/ontology")
                || path.startsWith("/api/integration")
                || path.startsWith("/api/metadata")
                || path.startsWith("/api/lineage")
                || path.startsWith("/api/agent-mesh")
                || path.startsWith("/datanet")
                || path.startsWith("/api/security")
                || path.startsWith("/api/twins")
                || path.startsWith("/api/v1/system/tenants")
                || path.startsWith("/api/v1/ecos/ontologies")
                || path.startsWith("/api/v1/workspace")
                || path.startsWith("/api/v1/ontology/proposals")
                || path.startsWith("/api/monitor")
                || path.startsWith("/api/v1/engine")) {
            return true;
        }

        // ── 1. 确定所需准入等级 ──────────────────────
        int requiredLevel = resolveRequiredLevel(handlerMethod, path);

        // L0 公开 — 无需检查
        if (requiredLevel <= 0) {
            return true;
        }

        // ── 2. 获取当前用户的准入等级 ──────────────────
        String userId = UserContext.getCurrentUserId();
        if (userId == null) {
            // 未认证用户 — 只能访问 L0
            if (requiredLevel > 0) {
                sendForbidden(response, "未认证用户无权访问该资源（需要 L" + requiredLevel + " 及以上）");
                return false;
            }
            return true;
        }

        int userClearanceLevel = resolveUserClearanceLevel(userId, request);

        // ── 3. 比对 ──────────────────────────────────
        if (userClearanceLevel < requiredLevel) {
            String levelName = requiredLevel < LEVEL_NAMES.length ? LEVEL_NAMES[requiredLevel] : ("L" + requiredLevel);
            log.warn("准入等级不足: userId={}, userLevel={}, requiredLevel={}, path={}",
                    userId, userClearanceLevel, requiredLevel, path);
            sendForbidden(response,
                    "准入等级不足: 您的等级为 L" + userClearanceLevel + "，该资源需要 " + levelName + " (L" + requiredLevel + ")");
            return false;
        }

        return true;
    }

    // ── 解析所需等级 ──────────────────────────────────

    private int resolveRequiredLevel(HandlerMethod handlerMethod, String path) {
        // 优先取方法上的注解
        MinimumClearance methodAnno = handlerMethod.getMethodAnnotation(MinimumClearance.class);
        if (methodAnno != null) {
            return methodAnno.level();
        }
        // 其次取类上的注解
        MinimumClearance classAnno = handlerMethod.getBeanType().getAnnotation(MinimumClearance.class);
        if (classAnno != null) {
            return classAnno.level();
        }
        // 默认规则：按路径前缀推断
        for (String[] rule : PATH_RULES) {
            if (path.startsWith(rule[0])) {
                return Integer.parseInt(rule[1]);
            }
        }
        return 0; // L0 公开
    }

    // ── 解析用户准入等级（级联查询）────────────────────
    // 级联顺序: USER > ROLE > ORG > TENANT > GLOBAL

    private int resolveUserClearanceLevel(String userId, HttpServletRequest request) {
        String tenantId = getCurrentTenantId();
        String orgId = getCurrentOrgId(userId);

        // 1. 查用户级配置
        Integer userLevel = queryClearanceLevel("td_user_security_profile", "user_id", userId, tenantId);
        if (userLevel != null) {
            log.debug("用户级安全配置命中: userId={}, level={}", userId, userLevel);
            return userLevel;
        }

        // 2. 查角色级配置（取用户拥有角色中 clearance_level 最高的）
        Integer roleLevel = queryHighestRoleClearanceLevel(userId, tenantId);
        if (roleLevel != null) {
            log.debug("角色级安全配置命中: userId={}, level={}", userId, roleLevel);
            return roleLevel;
        }

        // 2.5 查机构级配置 (organization level)
        if (orgId != null) {
            Integer orgLevel = queryClearanceLevelByOrg(orgId, tenantId);
            if (orgLevel != null) {
                log.debug("机构级安全配置命中: userId={}, orgId={}, level={}", userId, orgId, orgLevel);
                return orgLevel;
            }
        }

        // 2.8 查租户级配置
        if (tenantId != null) {
            Integer tenantLevel = queryClearanceLevelByTenant(tenantId);
            if (tenantLevel != null) {
                log.debug("租户级安全配置命中: userId={}, tenantId={}, level={}", userId, tenantId, tenantLevel);
                return tenantLevel;
            }
        }

        // 3. 回退到全局默认配置
        Integer globalLevel = queryGlobalDefaultClearanceLevel(tenantId);
        if (globalLevel != null) {
            log.debug("全局默认安全配置命中: userId={}, level={}", userId, globalLevel);
            return globalLevel;
        }

        // 4. 硬编码默认值 L1
        return 1;
    }

    // ── 租户/机构 上下文辅助方法 ──────────────────────

    private String getCurrentTenantId() {
        // 优先从 UserContext 获取（JwtAuthenticationFilter 设置）
        try {
            String tid = UserContext.getCurrentTenantId();
            if (tid != null && !tid.isBlank()) return tid;
        } catch (Exception e) {
            log.debug("从 UserContext 获取 tenantId 失败: {}", e.getMessage());
        }
        // 回退到 TenantContext
        try {
            return TenantContext.getTenantId();
        } catch (Exception e) {
            log.debug("从 TenantContext 获取 tenantId 失败: {}", e.getMessage());
            return null;
        }
    }

    private String getCurrentOrgId(String userId) {
        // 尝试从请求属性获取
        HttpServletRequest req = currentRequest.get();
        if (req != null) {
            String orgId = req.getHeader("X-Org-Id");
            if (orgId != null && !orgId.isBlank()) return orgId;
            orgId = req.getParameter("orgId");
            if (orgId != null && !orgId.isBlank()) return orgId;
        }
        // 回退到从用户表查询 org_id
        if (userId != null) {
            try {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT \"ORG_ID\" FROM TD_USER WHERE \"ID\" = ?", userId);
                if (rows != null && !rows.isEmpty()) {
                    Object oid = rows.get(0).get("ORG_ID");
                    return oid != null ? oid.toString() : null;
                }
            } catch (Exception e) {
                log.debug("查询用户机构失败: userId={}, {}", userId, e.getMessage());
            }
        }
        return null;
    }

    // ── 查询方法（带租户过滤）───────────────────────────

    private Integer queryClearanceLevel(String tableName, String idColumn, String idValue, String tenantId) {
        try {
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = String.format(
                        "SELECT clearance_level FROM %s WHERE %s = ? AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1",
                        tableName, idColumn);
                params = new Object[]{idValue, tenantId};
            } else {
                sql = String.format(
                        "SELECT clearance_level FROM %s WHERE %s = ? LIMIT 1",
                        tableName, idColumn);
                params = new Object[]{idValue};
            }
            List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
            if (!rows.isEmpty()) {
                Object val = rows.get(0).get("clearance_level");
                if (val instanceof Number n) {
                    return n.intValue();
                }
            }
        } catch (Exception e) {
            log.warn("查询{}安全配置失败: {}", tableName, e.getMessage());
        }
        return null;
    }

    private Integer queryHighestRoleClearanceLevel(String userId, String tenantId) {
        try {
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = """
                        SELECT COALESCE(MAX(rsp.clearance_level), -1) AS max_level
                        FROM td_user_role ur
                        JOIN td_role_security_profile rsp ON ur.ROLE_ID = rsp.role_id
                        WHERE ur.USER_ID = ? AND (rsp.tenant_id = ? OR rsp.tenant_id IS NULL)
                        """;
                params = new Object[]{userId, tenantId};
            } else {
                sql = """
                        SELECT COALESCE(MAX(rsp.clearance_level), -1) AS max_level
                        FROM td_user_role ur
                        JOIN td_role_security_profile rsp ON ur.ROLE_ID = rsp.role_id
                        WHERE ur.USER_ID = ?
                        """;
                params = new Object[]{userId};
            }
            Integer val = jdbc.queryForObject(sql, Integer.class, params);
            return (val != null && val >= 0) ? val : null;
        } catch (Exception e) {
            log.warn("查询用户角色安全配置失败: userId={}, {}", userId, e.getMessage());
            return null;
        }
    }

    private Integer queryGlobalDefaultClearanceLevel(String tenantId) {
        try {
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = "SELECT clearance_level FROM td_user_security_profile WHERE is_default = TRUE AND (tenant_id = ? OR tenant_id IS NULL) LIMIT 1";
                params = new Object[]{tenantId};
            } else {
                sql = "SELECT clearance_level FROM td_user_security_profile WHERE is_default = TRUE LIMIT 1";
                params = new Object[]{};
            }
            Integer val = jdbc.queryForObject(sql, Integer.class, params);
            return val;
        } catch (Exception e) {
            log.debug("查询全局默认安全配置失败: {}", e.getMessage());
            return null;
        }
    }

    // ── 机构/租户级查询 ──────────────────────────────

    private Integer queryClearanceLevelByOrg(String orgId, String tenantId) {
        try {
            String sql;
            Object[] params;
            if (tenantId != null) {
                sql = "SELECT clearance_level FROM td_user_security_profile WHERE org_id = ? AND scope_type = 'ORG' AND (tenant_id = ? OR tenant_id IS NULL) ORDER BY clearance_level DESC LIMIT 1";
                params = new Object[]{orgId, tenantId};
            } else {
                sql = "SELECT clearance_level FROM td_user_security_profile WHERE org_id = ? AND scope_type = 'ORG' ORDER BY clearance_level DESC LIMIT 1";
                params = new Object[]{orgId};
            }
            Integer val = jdbc.queryForObject(sql, Integer.class, params);
            return val;
        } catch (Exception e) {
            log.debug("查询机构级安全配置失败: orgId={}, {}", orgId, e.getMessage());
            return null;
        }
    }

    private Integer queryClearanceLevelByTenant(String tenantId) {
        try {
            String sql = "SELECT clearance_level FROM td_user_security_profile WHERE tenant_id = ? AND scope_type = 'TENANT' ORDER BY clearance_level DESC LIMIT 1";
            Integer val = jdbc.queryForObject(sql, Integer.class, tenantId);
            return val;
        } catch (Exception e) {
            log.debug("查询租户级安全配置失败: tenantId={}, {}", tenantId, e.getMessage());
            return null;
        }
    }

    // ── 响应 ──────────────────────────────────────────

    private void sendForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.forbidden(message)));
    }
}
