package com.chinacreator.gzcm.ai.wagent.governance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分册10 F10-21 · Kill Switch 治理服务（业内/租户双粒度）。
 *
 * <p>scope=SYSTEM 强制 admin；scope=TENANT 强制 tenantId 非空。
 * 内存态 flags：killSwitch 命中即触发 {@link #pauseActiveRuns()} 语义钩（无
 * double/float；无 ScheduledExecutor；无 JdbcTemplate；只 assert 语义，抛
 * {@code BusinessException} 类异常由 controller 统一处理）。</p>
 */
public final class KillSwitch {

    public record Scope(String scope, String tenantId, boolean enabled, String reason) {
        public Scope {
            if (scope == null || (!"SYSTEM".equals(scope) && !"TENANT".equals(scope))) {
                throw new IllegalArgumentException("E-POLICY: scope 必须是 SYSTEM 或 TENANT");
            }
            if ("TENANT".equals(scope) && (tenantId == null || tenantId.isBlank())) {
                throw new IllegalArgumentException("E-POLICY: TENANT scope 必带 tenantId");
            }
        }
    }

    private final Map<String, Scope> flagsByScope = new ConcurrentHashMap<>();
    private volatile int pausedRunCount = 0;

    public boolean isEnabled(String scopeName, String tenantId) {
        Scope s = flagsByScope.get(key(scopeName, tenantId));
        return s != null && s.enabled();
    }

    /** 触发 kill-switch：仅允许 SYSTEM 由 admin、TENANT 由 tenant admin（由 controller 层鉴权）。 */
    public Scope set(Scope scope, String actor) {
        Scope stored = new Scope(scope.scope(), scope.tenantId(), scope.enabled(),
                scope.reason() == null ? ("triggered-by:" + actor) : scope.reason() + " (by " + actor + ")");
        flagsByScope.put(key(scope.scope(), scope.tenantId()), stored);
        if (stored.enabled()) {
            pausedRunCount++;
        }
        return stored;
    }

    public int pausedRunCount() { return pausedRunCount; }

    /** 语义钩：由 wiring 层接入真实 pause 实现（本类只计数，不直接触发迁移）。 */
    public void pauseActiveRuns() {
        // placeholder hook — 生产链路由 runtime-task 或 candidate service 实现
    }

    public Map<String, Object> exportFlags() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Scope> e : flagsByScope.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scope", e.getValue().scope());
            row.put("tenantId", e.getValue().tenantId());
            row.put("enabled", e.getValue().enabled());
            row.put("reason", e.getValue().reason());
            out.put(e.getKey(), row);
        }
        return out;
    }

    private static String key(String scopeName, String tenantId) {
        return scopeName + "::" + (tenantId == null ? "*system*" : tenantId);
    }
}
