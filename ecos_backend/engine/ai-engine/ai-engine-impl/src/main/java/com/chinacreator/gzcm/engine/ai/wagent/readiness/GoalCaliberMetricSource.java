package com.chinacreator.gzcm.engine.ai.wagent.readiness;

import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentGoalDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentGoalDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code onto.caliber_defined} 的<b>本域取证</b>实现（详细设计-10 F10-08 I 层检查项①）。
 *
 * <p>检查项语义（V265 seed 原文）：「目标指标是否绑定 PUBLISHED 口径版本」。
 * 口径主权在 {@code ecos_ontology}（{@link com.chinacreator.gzcm.engine.ai.wagent.WAgentEngineCatalog}
 * ONTOLOGY 条目注释：「口径主权在 ecos_ontology，Goal.caliber_version 只读引用，F10-07」），
 * 而"本次评估的目标绑没绑口径"这一事实，其权威载体正是 W 域自家 Goal 行的
 * {@code caliber_version} 列——绑定动作的产物就落在这一列上。</p>
 *
 * <p>因此本实现读的是 <b>ai-engine 自己的表</b>（{@code ecos_ai.ecos_wagent_goal}），
 * 不违反「跨引擎不操作对方表」（铁律约束 5）与
 * {@code ArchitectureTest#shouldNotDependOnOtherEngines}，且不含任何造数：
 * 列非空 ⇒ true，列空 ⇒ false（"未绑定"是实测事实，不是猜测），
 * Goal 行取不到 ⇒ {@code absent}（无目标即无该项证据，禁默认放行）。</p>
 *
 * <p><b>边界与剩余差距</b>：本实现只回答"绑定了没有"，<b>不</b>回答"该版本在 ontology 侧是否仍为
 * PUBLISHED"——后者需 ontology-engine 提供口径版本状态读端点（当前无，已作为剩余差距登记，
 * 归 B4 安全/契约批后由属主引擎补端点，届时新增一个 {@code ReadinessMetricSource} 实现加强，
 * 本类不动）。</p>
 */
@Component
public class GoalCaliberMetricSource implements ReadinessMetricSource {

    private static final Logger log = LoggerFactory.getLogger(GoalCaliberMetricSource.class);

    private final ObjectProvider<WAgentGoalDao> goalDaoProvider;

    public GoalCaliberMetricSource(ObjectProvider<WAgentGoalDao> goalDaoProvider) {
        this.goalDaoProvider = goalDaoProvider;
    }

    @Override
    public boolean supports(ReadinessCheckCode code) {
        return code == ReadinessCheckCode.ONTO_CALIBER_DEFINED;
    }

    @Override
    public MetricValue measure(ReadinessCheckCode code, ReadinessProbeContext context) {
        if (code != ReadinessCheckCode.ONTO_CALIBER_DEFINED) {
            return MetricValue.absent("unsupported-by-goal-caliber-source");
        }
        String goalId = context == null ? null : context.goalId();
        if (goalId == null || goalId.isBlank()) {
            return MetricValue.absent("goal-id-absent");
        }
        WAgentGoalDao goalDao = goalDaoProvider.getIfAvailable();
        if (goalDao == null) {
            log.warn("[F10-08] WAgentGoalDao 未装配，onto.caliber_defined 记 absent（禁默认放行）");
            return MetricValue.absent("goal-dao-unavailable");
        }
        Optional<WAgentGoalDO> found = goalDao.findById(goalId);
        if (found.isEmpty()) {
            return MetricValue.absent("goal-row-absent:" + goalId);
        }
        WAgentGoalDO goal = found.get();
        // 租户闸：跨租户 Goal 不作为证据（防枚举同掩，§5.3）
        if (context.tenantId() != null && !context.tenantId().equals(goal.getTenantId())) {
            return MetricValue.absent("goal-tenant-mismatch");
        }
        boolean bound = goal.getCaliberVersion() != null && !goal.getCaliberVersion().isBlank();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("source", sourceId());
        evidence.put("goal_id", goal.getId());
        evidence.put("metric_id", goal.getMetricId());
        evidence.put("metric_version", goal.getMetricVersion());
        evidence.put("caliber_version", goal.getCaliberVersion());
        evidence.put("caliber_source", "ecos_ai.ecos_wagent_goal.caliber_version");
        evidence.put("published_state_verified", false);
        return MetricValue.bool(bound, evidence);
    }

    @Override
    public String sourceId() {
        return "goal-caliber";
    }
}
