package com.chinacreator.gzcm.workspace.scenario;

import com.chinacreator.gzcm.workspace.exception.IslandBindingException;
import com.chinacreator.gzcm.workspace.exception.ScenarioNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 场景状态迁移编排（详细设计-07 D-2 N12 / F07-07 C-153）。
 *
 * <p>N12 {@code PATCH /scenarios/{id}/status} 的服务侧编排：先据 C-2 迁移表与前置条件判，
 * 再持久化 {@code status} 并下发 {@code allowedTransitions}（服务端权威，前端按钮态唯一来源）。</p>
 *
 * <pre>
 *   迁移（ScenarioStatusMachine）：DRAFT→ACTIVE；ACTIVE→{SUSPENDED,COMPLETED}；SUSPENDED→ACTIVE；COMPLETED=终态
 *   前置守卫：
 *     ① 激活（→ACTIVE）时孤岛必须为空 → 否则 409 ISLAND_BINDING（R-26① 双闸的激活闸）
 *     ② 完成（→COMPLETED）须存在至少一条 FORMAL 成功运行 → 否则 409 NO_FORMAL_RUN
 * </pre>
 *
 * <p>数据源：场景 = {@code ecos_business_scenario}；运行 = {@code ecos_scenario_execution}
 * （R-25① 正交列 {@code run_mode}，V209）。判定只读，不另写。</p>
 */
@Service
public class ScenarioStatusTransitionService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioStatusTransitionService.class);

    private final JdbcTemplate jdbc;
    private final ScenarioStatusMachine machine;
    private final ScenarioCompletenessService completenessService;

    public ScenarioStatusTransitionService(JdbcTemplate jdbc,
                                           ScenarioStatusMachine machine,
                                           ScenarioCompletenessService completenessService) {
        this.jdbc = jdbc;
        this.machine = machine;
        this.completenessService = completenessService;
    }

    /** 出参 VO：迁移后的状态 + 允许的目标态。 */
    public static class StatusVO {
        private String status;
        private List<String> allowedTransitions;
        public StatusVO() {}
        public StatusVO(String status, List<String> allowedTransitions) {
            this.status = status;
            this.allowedTransitions = allowedTransitions;
        }
        public String getStatus() { return status; }
        public void setStatus(String v) { this.status = v; }
        public List<String> getAllowedTransitions() { return allowedTransitions; }
        public void setAllowedTransitions(List<String> v) { this.allowedTransitions = v; }
    }

    @Transactional
    public StatusVO transition(String scenarioId, String toStatus) {
        String current = currentStatus(scenarioId); // 不存在 → ScenarioNotFoundException(404)

        String target = (toStatus == null || toStatus.isBlank()) ? current : toStatus.toUpperCase();

        boolean hasFormalRun = hasFormalSucceededRun(scenarioId);
        machine.assertTransition(current, target, hasFormalRun);

        if (!current.equals(target)) {
            // ① 激活闸：进入 ACTIVE 时孤岛必须为空（R-26①）
            if (ScenarioStatusMachine.ACTIVE.equals(target)) {
                rejectIfIsolated(scenarioId);
            }
            String operator = currentOperator();
            int rows = jdbc.update(
                "UPDATE ecos_business_scenario SET status = ?, update_time = NOW(), update_by = ? " +
                "WHERE id = ? AND is_deleted = 0",
                target, operator, scenarioId);
            if (rows < 1) {
                throw ScenarioNotFoundException.ofId(scenarioId);
            }
            log.info("scenario {} status {} -> {}", scenarioId, current, target);
        }
        return new StatusVO(target, machine.allowedTransitions(target));
    }

    /** 当前状态（场景不存在 → 404）。 */
    private String currentStatus(String scenarioId) {
        List<String> rows = jdbc.queryForList(
            "SELECT status FROM ecos_business_scenario WHERE id = ? AND is_deleted = 0",
            String.class, scenarioId);
        if (rows.isEmpty()) {
            throw ScenarioNotFoundException.ofId(scenarioId);
        }
        return rows.get(0);
    }

    /** 是否存在至少一条 FORMAL 且成功的运行（COMPLETED 前置，R-25① run_mode）。 */
    private boolean hasFormalSucceededRun(String scenarioId) {
        Integer n = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_scenario_execution " +
            "WHERE scenario_id = ? AND run_mode = 'FORMAL' AND status = 'SUCCEEDED' AND is_deleted = 0",
            Integer.class, scenarioId);
        return n != null && n > 0;
    }

    /** 激活闸：若存在孤岛绑定 → 409 ISLAND_BINDING（携带孤岛清单）。 */
    private void rejectIfIsolated(String scenarioId) {
        List<CompletenessVO.Island> islands = completenessService.islandsOf(scenarioId);
        if (islands == null || islands.isEmpty()) {
            return;
        }
        List<IslandBindingException.IslandItem> items = new ArrayList<>(islands.size());
        for (CompletenessVO.Island is : islands) {
            items.add(new IslandBindingException.IslandItem(
                is.getBindingId(), is.getName(), is.getBindingType(), is.getReason()));
        }
        throw new IslandBindingException(
            "cannot activate scenario with isolated bindings", items);
    }

    private String currentOperator() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String name) {
                return name;
            }
        } catch (Exception e) {
            log.debug("取操作人失败，回退 system: {}", e.getMessage());
        }
        return "system";
    }
}
