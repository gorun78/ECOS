package com.chinacreator.gzcm.engine.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 熔断器 — 连续失败保护，防止故障传播。
 * <p>
 * 状态机: CLOSED → (failCount >= 3) → OPEN → (5 分钟后) → HALF_OPEN → (1 次成功) → CLOSED
 * </p>
 */
public class AgentCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(AgentCircuitBreaker.class);

    /** 连续失败阈值 */
    private static final int FAIL_THRESHOLD = 3;
    /** HALF_OPEN 恢复延迟（毫秒）: 5 分钟 */
    private static final long HALF_OPEN_DELAY_MS = 5 * 60 * 1000L;

    private final ConcurrentHashMap<String, CircuitState> states = new ConcurrentHashMap<>();

    /**
     * 检查是否允许执行 — OPEN 状态返回 false。
     */
    public boolean isAllowed(String agentId) {
        if (agentId == null) return true;
        CircuitState state = states.get(agentId);
        if (state == null) return true;

        // HALF_OPEN: 允许一次探测请求
        if (state.status == Status.HALF_OPEN) {
            log.debug("[CircuitBreaker] agent={} HALF_OPEN — allowing probe", agentId);
            return true;
        }

        // OPEN: 检查是否已过恢复期
        if (state.status == Status.OPEN) {
            long elapsed = System.currentTimeMillis() - state.openedAt;
            if (elapsed >= HALF_OPEN_DELAY_MS) {
                state.status = Status.HALF_OPEN;
                state.failCount.set(0);
                log.info("[CircuitBreaker] agent={} transitioned OPEN→HALF_OPEN ({}ms)", agentId, elapsed);
                return true;
            }
            log.warn("[CircuitBreaker] agent={} is OPEN — requests blocked", agentId);
            return false;
        }

        return true;
    }

    /**
     * 记录成功 — CLOSED 状态重置计数器，HALF_OPEN 状态恢复为 CLOSED。
     */
    public void recordSuccess(String agentId) {
        if (agentId == null) return;
        CircuitState state = states.get(agentId);
        if (state == null) return;

        if (state.status == Status.CLOSED) {
            state.failCount.set(0);
        } else if (state.status == Status.HALF_OPEN) {
            states.remove(agentId);
            log.info("[CircuitBreaker] agent={} HALF_OPEN → CLOSED (recovered)", agentId);
        }
    }

    /**
     * 记录失败 — CLOSED/HALF_OPEN 状态下累加，达阈值后转为 OPEN。
     */
    public void recordFailure(String agentId) {
        if (agentId == null) return;
        CircuitState state = states.computeIfAbsent(agentId, k -> new CircuitState());

        if (state.status == Status.CLOSED || state.status == Status.HALF_OPEN) {
            int count = state.failCount.incrementAndGet();
            if (count >= FAIL_THRESHOLD) {
                state.status = Status.OPEN;
                state.openedAt = System.currentTimeMillis();
                log.warn("[CircuitBreaker] agent={} transitioned to OPEN ({} consecutive failures)",
                        agentId, count);
            }
        }
    }

    /**
     * 获取当前状态（用于监控）。
     */
    @SuppressWarnings("unchecked")
    public java.util.Map<String, Object> getStatus(String agentId) {
        CircuitState state = states.get(agentId);
        if (state == null) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("status", "CLOSED");
            m.put("failCount", 0);
            return m;
        }
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("status", state.status.name());
        m.put("failCount", state.failCount.get());
        m.put("openedAt", state.openedAt);
        return m;
    }

    // ─── 内部状态 ────────────────────────────────────────

    private enum Status { CLOSED, OPEN, HALF_OPEN }

    private static class CircuitState {
        Status status = Status.CLOSED;
        AtomicInteger failCount = new AtomicInteger(0);
        long openedAt;
    }
}
