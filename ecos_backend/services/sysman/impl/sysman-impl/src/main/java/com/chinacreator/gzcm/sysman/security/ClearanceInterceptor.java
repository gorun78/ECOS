package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.context.TenantContext;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
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

    private final com.chinacreator.gzcm.sysman.dao.ClearanceLookupDao clearanceLookupDao;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 可选注入 EventBusService（避免编译期硬依赖 runtime-event / Kafka；不可用时跳过审计）。 */
    private final org.springframework.beans.factory.ObjectProvider<Object> eventBusProvider;

    /** ThreadLocal 保存当前请求，供辅助方法获取 orgId/tenantId */
    private final ThreadLocal<HttpServletRequest> currentRequest = new ThreadLocal<>();

    @Autowired
    public ClearanceInterceptor(com.chinacreator.gzcm.sysman.dao.ClearanceLookupDao clearanceLookupDao,
                                org.springframework.beans.factory.ObjectProvider<Object> eventBusProvider) {
        this.clearanceLookupDao = clearanceLookupDao;
        this.eventBusProvider = eventBusProvider;
    }

    /** 兼容构造器（无事件总线）：超管旁路审计仅落日志。保留供既有单测与非注入装配使用。 */
    public ClearanceInterceptor(com.chinacreator.gzcm.sysman.dao.ClearanceLookupDao clearanceLookupDao) {
        this.clearanceLookupDao = clearanceLookupDao;
        this.eventBusProvider = null;
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

        // 公开端点免检查 — PMO-74 H9-T2 + 详细设计-00 W05 (C.3.2)：
        // 豁免统一由 AnonymousEndpointRegistry 单源驱动（ANONYMOUS + CLEARANCE_EXEMPT 展开集），
        // 与 SecurityConfig permitAll、ClearanceMvcConfig excludePathPatterns 同源；
        // 不再内联 startsWith 硬编码。
        if (com.chinacreator.gzcm.common.security.anonymous
                .AnonymousEndpointRegistry.isAnonymousOrExempt(path)) {
            return true;
        }

        // ── 1. 确定所需准入等级 ──────────────────────
        int requiredLevel = resolveRequiredLevel(handlerMethod, path);

        // L0 公开 — 无需检查
        if (requiredLevel <= 0) {
            return true;
        }

        // ── 1.5 P0-1 (Wave-4.1) + 卷01 W40：super-admin 旁路 —— 通过强审计上报后放行 ──
        // 卷01 F01-08：超管旁路保留但须每次留痕（保留跨租户遥测视角业务语义，同时关闭"无审计旁路"的越权面）。
        // 审计事件走 runtime-event EventBusService 发布到 ecos.audit（可选注入，不可用仅 WARN 不阻断）。
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_SUPER_ADMIN".equalsIgnoreCase(a.getAuthority()))) {
                String operator = auth.getPrincipal() == null ? "unknown" : String.valueOf(auth.getPrincipal());
                StringBuilder roles = new StringBuilder();
                for (var ga : auth.getAuthorities()) {
                    if (roles.length() > 0) roles.append(',');
                    roles.append(ga.getAuthority());
                }
                publishSuperAdminBypassAudit(operator, roles.toString(), path, requiredLevel);
                log.warn("super-admin 旁路准入校验: operator={}, roles={}, path={}, requiredL={}",
                        operator, roles, path, requiredLevel);
                return true;
            }
        } catch (Exception e) {
            // fail-closed：判定异常不放行，按非超管继续走准入等级校验
            log.warn("super-admin 旁路判定失败，按非超管继续准入校验: path={}", path, e);
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
        // 详细设计-01 W40：机构上下文只从服务端解析（TD_USER.ORG_ID），
        // 不再信任客户端 X-Org-Id 头 / orgId 参数（水平越权面）。客户端头已在
        // gateway EcosTrustHeaderFilter 剥离；此处兜底彻底移除客户端来源，双重保险。
        if (userId != null) {
            try {
                List<Map<String, Object>> rows = clearanceLookupDao.findOrgById(userId);
                if (rows != null && !rows.isEmpty()) {
                    Object oid = rows.get(0).get("ORG_ID");
                    return oid != null ? oid.toString() : null;
                }
            } catch (Exception e) {
                log.warn("查询用户机构失败，跳过机构级: userId={}", userId, e);
            }
        }
        return null;
    }

    // ── 超管旁路强审计（详细设计-01 W40 / F01-08）────────────────────
    // 保留旁路业务能力，但每次旁路必须留痕到 ecos.audit（可选注入 EventBusService，
    // 不可用仅 WARN 不阻断；反射调用避免编译期硬依赖 runtime-event，同 SecurityPolicyController 口径）。

    private void publishSuperAdminBypassAudit(String operator, String roles, String path, int requiredLevel) {
        try {
            Object eventBus = eventBusProvider == null ? null : eventBusProvider.getIfAvailable();
            if (eventBus == null) {
                log.debug("[audit] EventBusService 不可用，超管旁路留痕仅走日志: operator={}, path={}", operator, path);
                return;
            }
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("eventType", "super_admin_bypass");
            payload.put("action", "clearance.bypass.superAdmin");
            payload.put("userId", operator);
            payload.put("username", operator);
            payload.put("resource", path);
            payload.put("result", "BYPASSED");
            payload.put("roles", roles);
            payload.put("requiredLevel", requiredLevel);
            payload.put("source", "sysman-clearance-interceptor");
            payload.put("timestamp", System.currentTimeMillis());
            eventBus.getClass().getMethod("publish", String.class, Object.class)
                    .invoke(eventBus, "ecos.audit", payload);
        } catch (Exception e) {
            log.warn("[audit] 超管旁路审计发布失败（不阻断旁路）: operator={}, path={}, {}",
                    operator, path, e.getMessage());
        }
    }

    // ── 查询方法（带租户过滤）───────────────────────────

    private Integer queryClearanceLevel(String tableName, String idColumn, String idValue, String tenantId) {
        try {
            List<Map<String, Object>> rows = clearanceLookupDao.queryClearanceRows(tableName, idColumn, idValue, tenantId);
            if (!rows.isEmpty()) {
                Object val = rows.get(0).get("clearance_level");
                if (val instanceof Number n) {
                    return n.intValue();
                }
            }
        } catch (Exception e) {
            log.warn("查询{}安全配置失败，级联继续下一档", tableName, e);
        }
        return null;
    }

    private Integer queryHighestRoleClearanceLevel(String userId, String tenantId) {
        try {
            Integer val = clearanceLookupDao.highestRoleClearanceLevel(userId, tenantId);
            return (val != null && val >= 0) ? val : null;
        } catch (Exception e) {
            log.warn("查询用户角色安全配置失败，级联继续下一档: userId={}", userId, e);
            return null;
        }
    }

    private Integer queryGlobalDefaultClearanceLevel(String tenantId) {
        try {
            Integer val = clearanceLookupDao.globalDefaultClearanceLevel(tenantId);
            return val;
        } catch (EmptyResultDataAccessException e) {
            log.debug("未查到全局默认安全配置行: tenantId={}", tenantId);
            return null;
        } catch (Exception e) {
            log.warn("查询全局默认安全配置失败，落到兜底等级", e);
            return null;
        }
    }

    // ── 机构/租户级查询 ──────────────────────────────

    private Integer queryClearanceLevelByOrg(String orgId, String tenantId) {
        try {
            Integer val = clearanceLookupDao.orgClearanceLevel(orgId, tenantId);
            return val;
        } catch (EmptyResultDataAccessException e) {
            log.debug("未查到机构级安全配置行: orgId={}", orgId);
            return null;
        } catch (Exception e) {
            log.warn("查询机构级安全配置失败，级联继续下一档: orgId={}", orgId, e);
            return null;
        }
    }

    private Integer queryClearanceLevelByTenant(String tenantId) {
        try {
            Integer val = clearanceLookupDao.tenantClearanceLevel(tenantId);
            return val;
        } catch (EmptyResultDataAccessException e) {
            log.debug("未查到租户级安全配置行: tenantId={}", tenantId);
            return null;
        } catch (Exception e) {
            log.warn("查询租户级安全配置失败，级联继续下一档: tenantId={}", tenantId, e);
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
