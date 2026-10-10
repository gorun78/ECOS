package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.PlanSource;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Run 状态机（详细设计-10 §4.2 / F10-11 / REQ-WAG-11）——<b>Run 状态唯一写入口</b>。
 *
 * <p>迁移表内嵌于本类（单源，禁散落 if/else 到服务层）；非法迁移一律
 * {@code E-WA-STATE 409}（§5.3 单源 = WAgentErrorCodes）。任何服务组件要改
 * {@code ecos_wagent_run.status}，只能经 {@link #requireTransition}，
 * 禁绕过本类直接 {@code runDao.updateStatus}。</p>
 *
 * <p><b>F10-13 硬约束（写进状态机，不写在提示词里）</b>：{@code planSource=EXPLORED}
 * 的 Plan 必须先过 {@code human_gate}（{@code humanGateCleared=true}，由审批入口
 * {@code WAgentRunService.approveGate} 置位）才允许进入 {@code executing}；
 * 未清闸时 {@code plan_validated → executing} 直接判非法。</p>
 *
 * <p>同步 60s / 异步 30min 预算超时 → {@code timed_out}（F10-11）；
 * 五终态（{@link RunStatus#isTerminal()}）出边恒空——终态后任何迁移都是非法。</p>
 */
@Component
public class WAgentRunStateMachine {

    /** from → 合法 to 集合（§4.2 迁移表逐行冻结；执行期重规划回 planning，§4.2 EVT=REPLAN）。 */
    private static final Map<RunStatus, Set<RunStatus>> TRANSITIONS;

    static {
        Map<RunStatus, Set<RunStatus>> m = new EnumMap<>(RunStatus.class);
        m.put(RunStatus.CREATED, EnumSet.of(RunStatus.PLANNING, RunStatus.CANCELLED));
        m.put(RunStatus.PLANNING, EnumSet.of(RunStatus.PLAN_VALIDATED, RunStatus.AWAITING_APPROVAL,
                RunStatus.FAILED, RunStatus.CANCELLED, RunStatus.TIMED_OUT));
        m.put(RunStatus.PLAN_VALIDATED, EnumSet.of(RunStatus.EXECUTING, RunStatus.AWAITING_APPROVAL,
                RunStatus.PLANNING, RunStatus.FAILED, RunStatus.CANCELLED, RunStatus.TIMED_OUT));
        m.put(RunStatus.EXECUTING, EnumSet.of(RunStatus.AWAITING_INPUT, RunStatus.AWAITING_APPROVAL,
                RunStatus.WAITING_TASK, RunStatus.PAUSED, RunStatus.PLANNING,
                RunStatus.COMPLETED, RunStatus.COMPLETED_DEGRADED, RunStatus.FAILED,
                RunStatus.CANCELLED, RunStatus.TIMED_OUT));
        m.put(RunStatus.AWAITING_INPUT, EnumSet.of(RunStatus.EXECUTING, RunStatus.PAUSED,
                RunStatus.FAILED, RunStatus.CANCELLED, RunStatus.TIMED_OUT));
        m.put(RunStatus.AWAITING_APPROVAL, EnumSet.of(RunStatus.EXECUTING, RunStatus.PAUSED,
                RunStatus.FAILED, RunStatus.CANCELLED, RunStatus.TIMED_OUT));
        m.put(RunStatus.WAITING_TASK, EnumSet.of(RunStatus.EXECUTING, RunStatus.PAUSED,
                RunStatus.TIMED_OUT, RunStatus.FAILED, RunStatus.CANCELLED));
        // F10-21 Kill Switch：任意执行中态先入 paused（上方各态已含 →PAUSED 出边），恢复只回 executing
        m.put(RunStatus.PAUSED, EnumSet.of(RunStatus.EXECUTING, RunStatus.CANCELLED, RunStatus.FAILED));
        for (RunStatus terminal : RunStatus.values()) {
            if (terminal.isTerminal()) {
                m.put(terminal, EnumSet.noneOf(RunStatus.class));
            }
        }
        TRANSITIONS = Collections.unmodifiableMap(m);
    }

    private final WAgentRunDao runDao;

    public WAgentRunStateMachine(WAgentRunDao runDao) {
        this.runDao = runDao;
    }

    /** 纯迁移表判定（不含 F10-13 闸口）。 */
    public boolean isLegalTransition(RunStatus from, RunStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(RunStatus.class)).contains(to);
    }

    /**
     * 完整判定 = 迁移表 + F10-13 human_gate 硬约束。
     *
     * @param planSource       Run 计划来源（null 视为未定计划，不触发闸口）
     * @param humanGateCleared human_gate Step 已成功或审批已放行
     */
    public boolean canTransition(RunStatus from, RunStatus to, PlanSource planSource, boolean humanGateCleared) {
        if (!isLegalTransition(from, to)) {
            return false;
        }
        // F10-13：EXPLORED Plan 首次进 executing 前必须已过 human_gate —— 硬约束，非提示词。
        // 仅约束"首入"（created/planning/plan_validated → executing）；已入过 executing 后自
        // awaiting_*/waiting_task/paused 恢复属再入，闸口此前已过，不重复拦截。
        boolean firstEntry = from == RunStatus.CREATED || from == RunStatus.PLANNING
                || from == RunStatus.PLAN_VALIDATED;
        if (to == RunStatus.EXECUTING && firstEntry
                && planSource == PlanSource.EXPLORED && !humanGateCleared) {
            return false;
        }
        return true;
    }

    /**
     * 唯一状态写入口：校验迁移合法性（含 F10-13 闸口），经 {@link WAgentRunDao#updateStatus}
     * 落库并同步内存 DO；非法 ⇒ {@code E-WA-STATE 409}（extras 携 currentState/allowedOps）。
     *
     * @param run     当前 Run DO（其 status 即 from 态；调用方须刚从库读出）
     * @param to      目标态
     * @param humanGateCleared human_gate 闸口是否已清（非 EXPLORED 传 true 即可）
     * @param operator 操作人（落 update_by）
     */
    public WAgentRunDO requireTransition(WAgentRunDO run, RunStatus to, boolean humanGateCleared, String operator) {
        RunStatus from = parseStatus(run.getStatus());
        PlanSource planSource = parsePlanSource(run.getPlanSource());
        if (!canTransition(from, to, planSource, humanGateCleared)) {
            Map<String, Object> extras = new LinkedHashMap<>();
            extras.put("currentState", from.value());
            extras.put("attemptedTransition", to.value());
            extras.put("allowedOps", allowedOps(from));
            throw new WAgentOrchestrationException(
                    com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode.E_WA_STATE,
                    "非法迁移 " + from.value() + " -> " + to.value(), extras);
        }
        runDao.updateStatus(run.getId(), to.value(), operator);
        run.setStatus(to.value());
        boolean touchTime = false;
        if (to.isTerminal()) {
            run.setEndTime(LocalDateTime.now());
            touchTime = true;
        } else if (to == RunStatus.EXECUTING && run.getStartTime() == null) {
            run.setStartTime(LocalDateTime.now());
            touchTime = true;
        }
        if (touchTime) {
            // start/end 时间戳随全列 update 落库；status 已由 updateStatus 收口，二者同值不回写
            runDao.update(run);
        }
        return run;
    }

    /** 当前态的合法后继（诊断/409 extras 用）。 */
    public Set<RunStatus> allowedOps(RunStatus from) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(RunStatus.class));
    }

    /** 词表二段防线：库值只认 RunStatus 单源（脏值即 E-WA-STATE，禁静默续跑）。 */
    public static RunStatus parseStatus(String raw) {
        for (RunStatus s : RunStatus.values()) {
            if (s.value().equals(raw)) {
                return s;
            }
        }
        throw WAgentOrchestrationException.stateConflict(String.valueOf(raw), "(未识别状态词表)");
    }

    private static PlanSource parsePlanSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (PlanSource p : PlanSource.values()) {
            if (p.value().equals(raw)) {
                return p;
            }
        }
        throw WAgentOrchestrationException.validation("plan_source 脏值（词表单源 PlanSource 两值）: " + raw);
    }
}
