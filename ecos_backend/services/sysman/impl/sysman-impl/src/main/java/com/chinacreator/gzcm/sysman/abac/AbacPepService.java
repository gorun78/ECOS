package com.chinacreator.gzcm.sysman.abac;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.sysman.abac.model.AbacContext;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;
import com.chinacreator.gzcm.sysman.iam.service.IPermissionCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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

    /**
     * ABAC 策略服务（可选 — 未实现时使用纯 RBAC 回退）
     */
    @Autowired(required = false)
    private IAbacPolicyService policyService;

    /**
     * <b>本类不自持缓存</b>（#147AG 清退，§2.9.8⑤/DC-3）：原先两枚 Caffeine
     * （策略常驻 + 决策 5 分钟硬编码 TTL、键为 {@code subject|resource|action} 无租户维度）
     * 已删除，理由有三：
     * <ol>
     *   <li>缓存配置必须由 Runtime 单源供给，{@code DECISION_CACHE_TTL_MINUTES} 属服务自建配置；</li>
     *   <li>决策键无租户/密级维度 ⇒ 多租户下可能跨域命中（与 #147AA 同族越权形态）；</li>
     *   <li>策略加载失败时「回退到内存里的旧策略」是 <b>fail-open</b>（撤权/新增 DENY 不生效），
     *       与 §2.9.8⑥⑦「不可用 fail-closed（DENY）」相反。</li>
     * </ol>
     * <p>本类当前<b>无任何调用方</b>（PEP 未承流）。后续接线时若需缓存，必须经
     * {@code RuntimeCacheManager}：决策键
     * {@code ecos:{env}:sysman:abac-decision:{租户}:{subject|resource|action}:v{版本}}，
     * 策略列表键 {@code ...:abac-policy:{租户}:list:v{版本}}，陈旧上限由配置钳制。
     */

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

        // 1. 不再有进程内决策缓存：每次评估都走权威面（策略表 + RBAC），
        //    避免无租户维度的键与不可证明的陈旧窗口（#147AG）。
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

        // 4. 不写决策缓存（见字段处说明）；缓存接入须经 RuntimeCacheManager 且键带租户段。

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
    private String evaluateAbacPolicies(String subject, String resource, String action)
            throws IAbacPolicyService.AbacException {
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
     * 从权威面加载 ABAC 策略列表。
     *
     * <p><b>加载失败一律抛出</b>：{@code evaluate} 的 catch 会把异常落成 DENY（fail-closed）。
     * 旧实现「失败时回退到内存里的旧策略」是 fail-open——撤销/新增 DENY 策略在 DB 故障期间不生效，
     * 且以陈旧策略继续放行（#147AG）。</p>
     */
    private List<AbacPolicy> loadPolicies() throws IAbacPolicyService.AbacException {
        if (policyService == null) {
            return List.of();
        }
        List<AbacPolicy> policies = policyService.listPolicies();

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
    }

    // ── 工具方法 ──────────────────────────────────────────────

    private String extractString(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * 获取当前租户ID，用于作用域过滤。
     *
     * <p>与权限缓存/权限检查同源（{@link TenantContextHolder}，由 JWT 过滤器从令牌侧写入），
     * 不再读另一套 {@code sysman.iam.context.TenantContext}——两套 ThreadLocal 并存时
     * 过滤口径可能分叉（#147AG）。取不到租户返回 null 表示不过滤（策略表仍受
     * {@code GLOBAL}/{@code TENANT} 作用域列约束）。</p>
     */
    private String getCurrentTenantId() {
        String tenant = TenantContextHolder.getTenantId();
        return tenant == null || tenant.isBlank() ? null : tenant.trim();
    }

    /**
     * 缓存失效入口已随自持缓存一并删除（#147AG）：本类无缓存可供失效。
     * 后续接入 {@code RuntimeCacheManager} 后，策略变更应调
     * {@code invalidateDomain(env, "sysman")} 或按对象 {@code evictObject}，不得回到服务私有失效。
     */
}
