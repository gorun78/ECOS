package com.chinacreator.gzcm.sysman.abac;

import com.chinacreator.gzcm.sysman.abac.model.AbacContext;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;
import com.chinacreator.gzcm.sysman.iam.context.TenantContext;
import com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ABAC Policy Enforcement Point (PEP) — MVP 实现。
 *
 * <p>当前阶段：基于角色的内存策略评估，不依赖 OPA 引擎。
 * 后续阶段可通过 SPI 注入 OPA/Rego 评估器，本类保持 PepFacade 语义不变。
 *
 * <h3>评估流程</h3>
 * <ol>
 *   <li>从 ABAC 策略表获取匹配策略</li>
 *   <li>调用现有 {@link IPermissionCheckService} 做角色权限检查</li>
 *   <li>应用 DENY-OVERRIDES 合并规则</li>
 *   <li>返回 PERMIT/DENY</li>
 * </ol>
 *
 * <h3>设计意图</h3>
 * 对外暴露统一的 {@code evaluate(subject, resource, action)} 语义，
 * 调用方（Filter / Interceptor / AOP 切面）无需感知底层是 RBAC 还是 OPA。
 */
@Service
public class AbacPepService {

    private static final Logger log = LoggerFactory.getLogger(AbacPepService.class);

    private final IPermissionCheckService permissionCheckService;

    /** ABAC 策略服务（可选 — 未实现时使用纯 RBAC 回退） */
    @Autowired(required = false)
    private IAbacPolicyService policyService;

    /**
     * ABAC 策略缓存：policyId → AbacPolicy。
     * <p>M0 改造 (2026-09): ConcurrentHashMap → Caffeine，上限 10 万 (策略量上限)。
     * 策略变更调用 {@link #invalidateAllCaches()} 主动失效。TTL 不设 (策略常驻)。
     * <p>POV: PMO-B2 第 3 项 (AbacPepService 政策缓存)
     */
    private final Cache<String, AbacPolicy> policyCache = Caffeine.newBuilder()
        .maximumSize(100_000)
        .build();

    /**
     * ABAC 评估结果缓存：cacheKey → Boolean。
     * <p>M0 改造 (2026-09): ConcurrentHashMap (max 1000) → Caffeine (max 100,000 / TTL 5 min)。
     * <p>POV: PMO-B2 第 4 项 (AbacPepService 决策缓存)
     */
    private final Cache<String, Boolean> decisionCache = Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterWrite(DECISION_CACHE_TTL_MINUTES, TimeUnit.MINUTES)
        .build();

    /** 决策缓存 TTL（分钟）：MPV 阶段 5 分钟足够，后续可配到 sys_config 表 */
    private static final long DECISION_CACHE_TTL_MINUTES = 5;

    @Autowired
    public AbacPepService(IPermissionCheckService permissionCheckService) {
        this.permissionCheckService = permissionCheckService;
    }

    // ── 公共 API ──────────────────────────────────────────────

    /**
     * 评估访问请求是否允许。
     *
     * @param subject  主体标识（用户ID）
     * @param resource 资源标识（API 路径、数据源 ID 等）
     * @param action   动作（GET / POST / READ / WRITE 等）
     * @return true = PERMIT，false = DENY
     */
    public boolean evaluate(String subject, String resource, String action) {
        if (subject == null || resource == null || action == null) {
            log.warn("ABAC evaluate 参数为空: subject={}, resource={}, action={}", subject, resource, action);
            return false;
        }

        // 1. 短期缓存命中直接返回
        String cacheKey = subject + "|" + resource + "|" + action;
        Boolean cached = decisionCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("ABAC 决策缓存命中: key={}, result={}", cacheKey, cached);
            return cached;
        }

        boolean permitted;

        try {
            // 2. ABAC 策略评估（MVP: 检查是否有显式的 DENY/ALLOW 策略）
            String abacDecision = evaluateAbacPolicies(subject, resource, action);

            if ("DENY".equalsIgnoreCase(abacDecision)) {
                permitted = false;
            } else if ("ALLOW".equalsIgnoreCase(abacDecision)) {
                permitted = true;
            } else {
                // 3. 无显式 ABAC 策略 → 回退到 RBAC 角色权限检查
                permitted = evaluateRbac(subject, resource, action);
            }
        } catch (Exception e) {
            log.error("ABAC 策略评估异常, 默认 DENY: subject={}, resource={}, action={}",
                    subject, resource, action, e);
            permitted = false;
        }

        // 4. 写入决策缓存 (Caffeine 自动按 LRU/TTL 淘汰, 不需要手动 clear)
        // M0 改造 (2026-09): Caffeine 已启用 `maximumSize(100_000) + expireAfterWrite(5 min)`,
        // 原 ConcurrentHashMap 的"超过上限 clear 全部"逻辑不再需要。
        decisionCache.put(cacheKey, permitted);

        log.debug("ABAC evaluate: subject={}, resource={}, action={} → {}",
                subject, resource, action, permitted ? "PERMIT" : "DENY");
        return permitted;
    }

    /**
     * 基于 AbacContext 的评估入口（供需要完整上下文的调用方使用）。
     */
    public boolean evaluate(AbacContext context) {
        if (context == null) {
            return false;
        }
        String subject = extractString(context.getSubject(), "userId");
        String resource = extractString(context.getResource(), "resourceId");
        String action = extractString(context.getAction(), "action");
        return evaluate(subject, resource, action);
    }

    // ── ABAC 策略评估 ─────────────────────────────────────────

    /**
     * MVP 策略评估：从缓存/数据库获取策略列表，匹配 subject/resource/action。
     * <p>
     * 匹配规则（字符串包含匹配，后续可扩展为 JSONPath/SpEL）：
     * <ul>
     *   <li>subjectCondition 包含 subject（例如 "role:admin" 中的 "admin"）</li>
     *   <li>resourceCondition 包含 resource</li>
     *   <li>actionCondition 包含 action</li>
     * </ul>
     *
     * @return "ALLOW" / "DENY" / null (NOT_APPLICABLE)
     */
    private String evaluateAbacPolicies(String subject, String resource, String action) {
        List<AbacPolicy> policies = loadPolicies();
        if (policies == null || policies.isEmpty()) {
            return null; // NOT_APPLICABLE — 回退 RBAC
        }

        String bestDecision = null;
        int bestPriority = Integer.MIN_VALUE;

        for (AbacPolicy policy : policies) {
            if (!matches(subject, resource, action, policy)) {
                continue;
            }
            int priority = policy.getPriority() != null ? policy.getPriority() : 0;
            if (priority > bestPriority) {
                bestPriority = priority;
                bestDecision = policy.getEffect();
            } else if (priority == bestPriority && "DENY".equalsIgnoreCase(policy.getEffect())) {
                // DENY overrides ALLOW at same priority
                bestDecision = policy.getEffect();
            }
        }

        return bestDecision;
    }

    /**
     * 检查策略条件是否匹配。
     * MVP: 简单字符串包含匹配。后续版本接入 OPA/Rego 替换此方法。
     */
    private boolean matches(String subject, String resource, String action, AbacPolicy policy) {
        return matchesCondition(policy.getSubjectCondition(), subject)
                && matchesCondition(policy.getResourceCondition(), resource)
                && matchesCondition(policy.getActionCondition(), action);
    }

    private boolean matchesCondition(String condition, String value) {
        if (condition == null || condition.isEmpty()) {
            return true; // 空条件 = 匹配所有
        }
        if (value == null) {
            return false;
        }
        // 支持简单通配符 "*"
        if ("*".equals(condition.trim())) {
            return true;
        }
        return value.contains(condition) || condition.contains(value);
    }

    // ── RBAC 回退 ────────────────────────────────────────────

    /**
     * 回退到现有 RBAC 权限检查。
     */
    private boolean evaluateRbac(String userId, String resource, String action) {
        try {
            return permissionCheckService.checkPermission(userId, resource, action);
        } catch (Exception e) {
            log.error("RBAC 权限检查失败: userId={}, resource={}, action={}",
                    userId, resource, action, e);
            return false;
        }
    }

    // ── 策略加载 ──────────────────────────────────────────────

    /**
     * 从缓存或数据库加载 ABAC 策略列表。
     */
    private List<AbacPolicy> loadPolicies() {
        if (policyService == null) {
            return List.of();
        }
        try {
            List<AbacPolicy> policies = policyService.listPolicies();
            // 刷新内存缓存
            for (AbacPolicy p : policies) {
                policyCache.put(p.getPolicyId(), p);
            }

            // 作用域过滤：根据当前租户过滤策略
            String currentTenantId = getCurrentTenantId();
            if (currentTenantId != null) {
                int beforeCount = policies.size();
                policies = policies.stream()
                    .filter(p -> "GLOBAL".equals(p.getScopeType())
                        || ("TENANT".equals(p.getScopeType()) && currentTenantId.equals(p.getScopeId())))
                    .collect(Collectors.toList());
                log.debug("ABAC 作用域过滤: tenant={}, before={}, after={}",
                        currentTenantId, beforeCount, policies.size());
            }

            return policies;
        } catch (Exception e) {
            log.warn("加载ABAC策略失败，使用内存缓存: {}", e.getMessage());
            // M0 改造 (2026-09): Caffeine 没有 values() 直接 API, 用 asMap() 视图
            return List.copyOf(policyCache.asMap().values());
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────

    private String extractString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * 获取当前租户ID，用于作用域过滤。
     * 如果无法获取租户上下文（非多租户环境或未设置），返回 null 表示不过滤。
     */
    private String getCurrentTenantId() {
        try {
            return TenantContext.getTenantId();
        } catch (Exception e) {
            log.debug("无法获取当前租户ID，跳过作用域过滤: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 清空决策缓存（供策略变更后调用）。
     * M0 改造 (2026-09): Caffeine Cache 用 invalidateAll() 而非 clear()
     */
    public void invalidateDecisionCache() {
        decisionCache.invalidateAll();
        log.info("ABAC 决策缓存已清空");
    }

    /**
     * 清空策略 + 决策缓存，强制下次重新加载（供策略 CRUD 后调用）。
     */
    public void invalidateAllCaches() {
        policyCache.invalidateAll();
        decisionCache.invalidateAll();
        log.info("ABAC 全部缓存已清空");
    }
}
