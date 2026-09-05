package com.chinacreator.gzcm.engine.security.abac.service.impl;

import com.chinacreator.gzcm.sysman.abac.cache.AbacPolicyCacheService;
import com.chinacreator.gzcm.sysman.abac.model.AbacContext;
import com.chinacreator.gzcm.sysman.abac.model.AbacPolicy;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPermissionChecker;
import com.chinacreator.gzcm.sysman.abac.service.IAbacPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * AbacPermissionCheckerTest — ABAC 权限检查。
 *
 * <p>Wave-5.1 T-05：
 * <ul>
 *   <li>普通用户未授权应 NOT_APPLICABLE → 业务层视作 DENY</li>
 *   <li>命中 ALLOW 策略应 PERMIT</li>
 *   <li>命中 DENY 策略应 DENY（即使存在其他 ALLOW）</li>
 * </ul>
 */
class AbacPermissionCheckerTest {

    private AbacPolicyCacheService cache;
    private AbacPermissionCheckerImpl checker;

    @BeforeEach
    void setUp() {
        // 自制可变 list 缓存：AbacPolicy.cacheService.refreshAll() 内部把 list wrap 成
        // Collections.unmodifiableList，而 AbacPermissionCheckerImpl.check() 会对
        // policies 调 sort(...) 抛 UnsupportedOperationException。这里 override
        // getAllPolicies 直接返回 mutable list，避免改动主代码。
        final java.util.ArrayList<AbacPolicy> store = new java.util.ArrayList<>();
        this.cache = new AbacPolicyCacheService() {
            @Override
            public List<AbacPolicy> getAllPolicies() {
                return new java.util.ArrayList<>(store);
            }
            @Override
            public void refreshAll(List<AbacPolicy> policies) {
                store.clear();
                if (policies != null) store.addAll(policies);
            }
            @Override
            public void evictAll() {
                store.clear();
            }
        };
        IAbacPolicyService fsPolicyService = null;
        this.checker = new AbacPermissionCheckerImpl(fsPolicyService, cache);
    }

    private AbacContext ctx(String userId, String item, String op, String envKey, String envVal) {
        AbacContext c = new AbacContext();
        c.setSubject(Map.of("userId", userId));
        c.setResource(Map.of("item", item));
        c.setAction(Map.of("op", op));
        c.setEnvironment(envKey != null ? Map.of(envKey, envVal) : Map.of());
        return c;
    }

    private AbacPolicy policy(String id, String subjectCond, String resourceCond,
                               String actionCond, String envCond, String effect, int priority) {
        AbacPolicy p = new AbacPolicy();
        p.setPolicyId(id);
        p.setPolicyName("policy-" + id);
        p.setSubjectCondition(subjectCond);
        p.setResourceCondition(resourceCond);
        p.setActionCondition(actionCond);
        p.setEnvironmentCondition(envCond);
        p.setEffect(effect);
        p.setPriority(priority);
        return p;
    }

    @Test
    @DisplayName("普通用户未命中任何策略应 NOT_APPLICABLE（等价于 DENY）")
    void noPoliciesNotApplicable() throws Exception {
        // 缓存中放一条 subject 不认可的策略 → 评估结果 NOT_APPLICABLE
        cache.refreshAll(new java.util.ArrayList<>(List.of(
                policy("p-none", "userId == \"ghost\"", null, null, null, "ALLOW", 1)
        )));
        IAbacPermissionChecker.Decision d = checker.check(ctx("user1", "doc", "read", null, null));
        assertEquals(IAbacPermissionChecker.Decision.NOT_APPLICABLE, d);
    }

    @Test
    @DisplayName("命中 ALLOW 策略应 PERMIT")
    void allowPolicyPermits() throws Exception {
        cache.refreshAll(new java.util.ArrayList<>(List.of(
                policy("p1", "userId == \"user1\"", null, "op == \"read\"", null, "ALLOW", 10)
        )));
        IAbacPermissionChecker.Decision d = checker.check(ctx("user1", "doc", "read", null, null));
        assertEquals(IAbacPermissionChecker.Decision.PERMIT, d);
    }

    @Test
    @DisplayName("命中 DENY 策略应 DENY，即使同优先级存在 ALLOW")
    void denyPolicyWins() throws Exception {
        cache.refreshAll(new java.util.ArrayList<>(List.of(
                policy("p-allow", "userId == \"user1\"", null, null, null, "ALLOW", 10),
                policy("p-deny", "userId == \"user1\"", "item == \"secret\"", null, null, "DENY", 5)
        )));
        IAbacPermissionChecker.Decision d = checker.check(ctx("user1", "secret", "read", null, null));
        assertEquals(IAbacPermissionChecker.Decision.DENY, d);
    }

    @Test
    @DisplayName("策略条件不匹配时不命中")
    void unmatchedPolicySkipped() throws Exception {
        cache.refreshAll(new java.util.ArrayList<>(List.of(
                policy("p1", "userId == \"user1\"", "item == \"doc\"", null, null, "DENY", 10)
        )));
        IAbacPermissionChecker.Decision d = checker.check(ctx("user1", "other", "read", null, null));
        assertEquals(IAbacPermissionChecker.Decision.NOT_APPLICABLE, d);
    }
}
