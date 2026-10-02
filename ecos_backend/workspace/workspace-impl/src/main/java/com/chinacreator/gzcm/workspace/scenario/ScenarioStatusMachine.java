package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.exception.IllegalScenarioTransitionException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 场景状态机迁移守卫（详细设计-07 F07-07 / C-2）。
 *
 * <p>四态 DRAFT / ACTIVE / SUSPENDED / COMPLETED。迁移合法性与 {@code allowedTransitions}
 * 由本类唯一计算（服务端下发，前端按钮态唯一来源，F07-07-4）；非法迁移抛
 * {@link IllegalScenarioTransitionException}（→ 409 ILLEGAL_TRANSITION / TERMINAL_STATE / NO_FORMAL_RUN）。
 * 前置条件（孤岛/完整度/正式运行/权限）由 {@link ScenarioStatusTransitionService} 在调用
 * {@link #assertTransition} 前据 C-2 迁移表判，本类只负责"边是否存在/源态是否终态"。</p>
 */
@Component
public class ScenarioStatusMachine {

    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String COMPLETED = "COMPLETED";

    /** C-2 迁移表：from → 允许的 to 集合（不含前置条件，前置由上层叠）。 */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            DRAFT, Set.of(ACTIVE),
            ACTIVE, Set.of(SUSPENDED, COMPLETED),
            SUSPENDED, Set.of(ACTIVE),
            COMPLETED, Set.of()); // 终态

    /** 源态的合法目标态（供 allowedTransitions 下发）。 */
    public List<String> allowedTransitions(String fromStatus) {
        Set<String> allowed = allowedSet(fromStatus);
        return new java.util.ArrayList<>(allowed);
    }

    /**
     * 断言 from→to 是合法迁移边；不合法则抛 409。
     *
     * @param fromStatus 源态
     * @param toStatus   目标态
     * @param hasFormalRun COMPLETED 迁移前置：是否已存在至少一条 FORMAL 运行
     */
    public void assertTransition(String fromStatus, String toStatus, boolean hasFormalRun) {
        String from = fromStatus == null ? DRAFT : fromStatus.toUpperCase();
        String to = toStatus == null ? "" : toStatus.toUpperCase();
        Set<String> allowed = allowedSet(from);
        if (!allowed.isEmpty() && !allowed.contains(to)) {
            throw new IllegalScenarioTransitionException(
                    IllegalScenarioTransitionException.CODE_ILLEGAL,
                    "illegal transition " + from + " -> " + to);
        }
        // 终态兜底（若 to 非空且 from 终态）
        if (allowed.isEmpty() && !to.isBlank()) {
            throw new IllegalScenarioTransitionException(
                    IllegalScenarioTransitionException.CODE_TERMINAL,
                    "terminal state " + from);
        }
        // ACTIVE → COMPLETED 前置：须有正式运行
        if (ACTIVE.equals(from) && COMPLETED.equals(to) && !hasFormalRun) {
            throw new IllegalScenarioTransitionException(
                    IllegalScenarioTransitionException.CODE_NO_FORMAL_RUN,
                    "cannot complete without a formal run");
        }
    }

    private Set<String> allowedSet(String fromStatus) {
        String from = fromStatus == null ? DRAFT : fromStatus.toUpperCase();
        return TRANSITIONS.getOrDefault(from, new LinkedHashSet<>());
    }
}
