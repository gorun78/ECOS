package com.chinacreator.gzcm.sysman.security;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.iam.context.TenantContext;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import com.chinacreator.gzcm.sysman.iam.entity.UserOrganization;
import com.chinacreator.gzcm.sysman.iam.support.PrimaryOrganizationRule;
import com.chinacreator.gzcm.sysman.security.clearance.model.ClearanceLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * 五档密级链的单源读面（#142/B4-3e-81，收口 D-B4-23 余项腿）。
     * <p>本类原先自带一份私有级联实现，与 security 引擎消费的是同一批表；链只保留属主一份，
     * 免得两套判据各自漂移（同族见台账 D-B4-92）。<b>准入语义仍由本类裁决</b>：读面只交付
     * 三态事实，无档案时本类照旧兜底 L1（见 {@link #resolveUserClearanceLevel}）。
     */
    private final com.chinacreator.gzcm.sysman.security.clearance.service.ISubjectClearanceQueryService clearanceQueryService;

    /**
     * 机构隶属的属主 api 门面（#142/B4-3e-82，收口 D-B4-99 b) 腿）。
     * <p>存在理由：本类原经 {@code ClearanceLookupDao.findOrgById} 直读对方表
     * （{@code is_primary = '1' … LIMIT 1}）再取 {@code rows.get(0)}——那是「多枚主机构时取其一」，
     * 交回哪枚由数据库顺序决定；而策略面（security 引擎）对同一事实要求「恰好一枚才写值」。
     * 两套判据在同一个主体上给出不同答案，机构级密级档因此可落在主体并不属于的机构上。
     * 现与策略面共用属主单源判据 {@link PrimaryOrganizationRule}，取数改走属主 api 门面
     * （铁律 #5：跨域事实经属主门面；铁律 §2.5：本类不持 JdbcTemplate，DAO 也不该抄写别人的表）。
     * <p>这是<b>必装配</b>腿：缺席即 context 起不来，不在本类内做「拿不到就跳档」的折叠
     * （折叠族见台账 D-B4-83／D-B4-99 b) 说明）。
     */
    private final com.chinacreator.gzcm.sysman.iam.service.IOrganizationService organizationService;

    /** ThreadLocal 保存当前请求，供辅助方法获取 orgId/tenantId */
    private final ThreadLocal<HttpServletRequest> currentRequest = new ThreadLocal<>();

    @Autowired
    public ClearanceInterceptor(com.chinacreator.gzcm.sysman.dao.ClearanceLookupDao clearanceLookupDao,
                                org.springframework.beans.factory.ObjectProvider<Object> eventBusProvider,
                                com.chinacreator.gzcm.sysman.security.clearance.service.ISubjectClearanceQueryService clearanceQueryService,
                                com.chinacreator.gzcm.sysman.iam.service.IOrganizationService organizationService) {
        this.clearanceLookupDao = clearanceLookupDao;
        this.eventBusProvider = eventBusProvider;
        this.clearanceQueryService = clearanceQueryService;
        this.organizationService = organizationService;
    }

    /**
     * 兼容构造器（无事件总线）：超管旁路审计仅落日志。保留供既有单测与非注入装配使用。
     * <p>密级链不可缺省——它是本类唯一的判据来源，缺失即装配失败（禁再造一份内联级联）。
     * 机构门面同属必给参数（#142/B4-3e-82），测试须显式给替身，禁传 {@code null} 走折叠腿。
     */
    public ClearanceInterceptor(com.chinacreator.gzcm.sysman.dao.ClearanceLookupDao clearanceLookupDao,
                                com.chinacreator.gzcm.sysman.security.clearance.service.ISubjectClearanceQueryService clearanceQueryService,
                                com.chinacreator.gzcm.sysman.iam.service.IOrganizationService organizationService) {
        this.clearanceLookupDao = clearanceLookupDao;
        this.eventBusProvider = null;
        this.clearanceQueryService = clearanceQueryService;
        this.organizationService = organizationService;
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
            String levelName = ClearanceLevel.nameOf(requiredLevel);
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

    // ── 解析用户准入等级（级联取属主单源，兜底留在准入面）───
    // 级联顺序: USER > ROLE > ORG > TENANT > GLOBAL（判据单源见 ISubjectClearanceQueryService）

    private int resolveUserClearanceLevel(String userId, HttpServletRequest request) {
        String tenantId = getCurrentTenantId();
        String orgId = getCurrentOrgId(userId);

        com.chinacreator.gzcm.sysman.security.clearance.model.SubjectClearanceFact fact =
                clearanceQueryService.resolveSubjectClearance(userId, tenantId, orgId);
        if (fact.isResolved()) {
            if (fact.isDegraded()) {
                // 高优先档断了而低优先档读到值：准入面沿用既有「取到即用」，但把断腿如实留痕，
                // 不再像原实现那样静默下探（策略面 security 引擎对同一形态转不可用态，两侧各按语义处置）
                log.warn("准入等级取自降级链: userId={}, tier={}, level={}, degradedTiers={}",
                        userId, fact.tier(), fact.level(), fact.degradedTiers());
            } else {
                log.debug("准入等级命中: userId={}, tier={}, level={}", userId, fact.tier(), fact.level());
            }
            return fact.level();
        }

        // 无档案兜底值单源＝ ClearanceLevel.NO_PROFILE_FALLBACK（D-B4-99 a) 上收；
        // 本批只上收单源、不改值——决策原文「收紧方向的历史既定值」保留；改本常量＝改准入语义＋改 wire，另批裁决。
        // 属主单源刻意不内置它——策略面禁兜底（见 RlsSubjectContext 的 clearanceLevel 口径）。
        log.debug("五档密级链未解析（degradedTiers={}），准入面按单源兜底 L{}: userId={}",
                fact.degradedTiers(), ClearanceLevel.NO_PROFILE_FALLBACK, userId);
        return ClearanceLevel.NO_PROFILE_FALLBACK;
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
        // 详细设计-01 W40：机构上下文只从服务端解析（td_user_organization 主组织），
        // 不再信任客户端 X-Org-Id 头 / orgId 参数（水平越权面）。客户端头已在
        // gateway EcosTrustHeaderFilter 剥离；此处兜底彻底移除客户端来源，双重保险。
        // #142/B4-3e-82：取数改走属主 api 门面、判定改用属主单源判据（与策略面同一份规则）。
        if (userId != null) {
            try {
                List<UserOrganization> affiliations =
                        organizationService.getUserOrganizationAffiliations(userId);
                String orgId = PrimaryOrganizationRule.uniquePrimaryOrg(affiliations);
                if (orgId == null && affiliations != null && !affiliations.isEmpty()) {
                    // 有关联行却裁不出唯一主机构＝事实不完备（0 枚主机构行或多枚互异主机构）。
                    // 旧实现在这里取 rows.get(0) 谎报成「就是它」；现如实跳机构级，
                    // 密级链继续下探到租户/全局档（准入面 fail-soft 语义不变，不放宽档位）。
                    log.warn("用户主机构不唯一或缺失，跳过机构级: userId={}, rows={}",
                            userId, affiliations.size());
                }
                return orgId;
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

    // ── 响应 ──────────────────────────────────────────

    private void sendForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.forbidden(message)));
    }
}
