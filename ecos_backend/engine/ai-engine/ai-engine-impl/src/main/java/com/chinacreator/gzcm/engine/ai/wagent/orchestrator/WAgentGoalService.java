package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.common.event.EventTypes;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.GoalIntent;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.SlotProvenance;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentGoalDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentMissionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentGoalDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentMissionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.event.WAgentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 业务目标服务（详细设计-10 F10-05/F10-06/F10-07，REQ-WAG-05/06/07）。
 *
 * <p><b>口径锁纪律（F10-07）</b>：{@link #revise} 只写新版本行（{@code supersedes_goal_id}
 * 指前代，前代行零改动）；{@link #updateInPlace} 对<b>已被 Run 引用</b>的 Goal 直接拒绝
 * {@code 409 ECOS_WA_CALIBER_LOCKED}（引用链 Goal→Mission→Run 经 DAO 探测）。
 * Run.{@code previous_run_id} 记录口径变更链（{@code WAgentRunService} 侧落）。</p>
 *
 * <p>词表单源：{@code goalType} = {@link GoalIntent}（8 值，与 Question.intent 同源禁二套词表）；
 * {@code status} = {@link WAgentStatus.GoalStatus}（R2-A 补单源，见该类 Javadoc）；
 * 槽位来源 = {@link SlotProvenance}（V227 slots_source_json 逐槽位标注）。
 * 数字（inferred_count、version_no 递增）为确定性计算，非 LLM 产出。</p>
 *
 * <p>INSERT 必填项由服务层补齐（MC01 应用侧 UUID、DR07 version_no、tenant_id NOT NULL）。</p>
 */
@Service
public class WAgentGoalService {

    private final WAgentGoalDao goalDao;
    private final WAgentMissionDao missionDao;
    private final WAgentRunDao runDao;
    private final WAgentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WAgentGoalService(WAgentGoalDao goalDao, WAgentMissionDao missionDao,
                             WAgentRunDao runDao, WAgentEventPublisher eventPublisher) {
        this.goalDao = goalDao;
        this.missionDao = missionDao;
        this.runDao = runDao;
        this.eventPublisher = eventPublisher;
    }

    /** 创建命令（字段与 V227 列一一对；slotsSource=槽位名→SlotProvenance 值）。 */
    public record GoalCreateCommand(String tenantId, String orgId, String operator, String name,
                                    String goalType, String scopeJson, String timeRangeText,
                                    LocalDate asOfDate, String metricId, String metricVersion,
                                    String caliberVersion, String successCriteriaText,
                                    String ownerId, LocalDate dueDate,
                                    Map<String, String> slotsSource) {
    }

    /**
     * F10-05：创建 Goal —— 校验 goalType ∈ 8 意图，status=draft，version_no=1，
     * slots_source 逐槽位标注并投影 inferred_count（"全部为推断"提醒的数据基，F10-06/AC-02）。
     */
    public WAgentGoalDO create(GoalCreateCommand cmd) {
        if (cmd == null) {
            throw WAgentOrchestrationException.validation("Goal 创建命令不得为空");
        }
        if (!GoalIntent.isValid(cmd.goalType())) {
            throw WAgentOrchestrationException.validation("goal_type 越界（8 意图单源 GoalIntent）: " + cmd.goalType());
        }
        requireTenant(cmd.tenantId());
        WAgentGoalDO goal = new WAgentGoalDO();
        goal.setId(UUID.randomUUID().toString());
        goal.setName(cmd.name());
        goal.setGoalType(GoalIntent.valueOf(cmd.goalType().trim().toUpperCase(java.util.Locale.ROOT)).value());
        goal.setScopeJson(cmd.scopeJson());
        goal.setTimeRangeText(cmd.timeRangeText());
        goal.setAsOfDate(cmd.asOfDate());
        goal.setMetricId(cmd.metricId());
        goal.setMetricVersion(cmd.metricVersion());
        goal.setCaliberVersion(cmd.caliberVersion());
        goal.setSuccessCriteriaText(cmd.successCriteriaText());
        goal.setOwnerId(cmd.ownerId());
        goal.setDueDate(cmd.dueDate());
        goal.setStatus(WAgentStatus.GoalStatus.DRAFT.value());
        goal.setSlotsSourceJson(writeSlots(cmd.slotsSource()));
        goal.setInferredCount(countInferred(cmd.slotsSource()));
        goal.setSupersedesGoalId(null);
        goal.setTenantId(cmd.tenantId());
        goal.setOrgId(cmd.orgId());
        goal.setVersionNo("1");
        goal.setCreateBy(cmd.operator());
        goal.setUpdateBy(cmd.operator());
        goalDao.insert(goal);
        publishGoalEvent(EventTypes.Agent.GOAL_CREATED, goal, cmd.operator());
        return goal;
    }

    /**
     * F10-07：口径修订 ⇒ <b>只写新版本</b>。新行 {@code supersedes_goal_id=旧id}、
     * {@code version_no=旧+1}（确定性递增）；旧行零改动（禁就地编辑——前代保留作审计与
     * 已引用口径的快照链）。非空 patch 字段覆盖，未提供字段继承前代。
     */
    public WAgentGoalDO revise(String goalId, Map<String, String> patch, String operator, String tenantId) {
        WAgentGoalDO old = load(goalId);
        if (tenantId != null && !tenantId.equals(old.getTenantId())) {
            throw WAgentOrchestrationException.notFound();
        }
        WAgentGoalDO next = new WAgentGoalDO();
        next.setId(UUID.randomUUID().toString());
        next.setName(patchOrDefault(patch, "name", old.getName()));
        String goalType = patchOrDefault(patch, "goalType", old.getGoalType());
        if (!GoalIntent.isValid(goalType)) {
            throw WAgentOrchestrationException.validation("goal_type 越界（8 意图单源 GoalIntent）: " + goalType);
        }
        next.setGoalType(goalType.trim().toUpperCase(java.util.Locale.ROOT));
        next.setScopeJson(patchOrDefault(patch, "scopeJson", old.getScopeJson()));
        next.setTimeRangeText(patchOrDefault(patch, "timeRangeText", old.getTimeRangeText()));
        next.setAsOfDate(patch.containsKey("asOfDate") ? LocalDate.parse(patch.get("asOfDate")) : old.getAsOfDate());
        next.setMetricId(patchOrDefault(patch, "metricId", old.getMetricId()));
        next.setMetricVersion(patchOrDefault(patch, "metricVersion", old.getMetricVersion()));
        next.setCaliberVersion(patchOrDefault(patch, "caliberVersion", old.getCaliberVersion()));
        next.setSuccessCriteriaText(patchOrDefault(patch, "successCriteriaText", old.getSuccessCriteriaText()));
        next.setOwnerId(patchOrDefault(patch, "ownerId", old.getOwnerId()));
        next.setDueDate(patch.containsKey("dueDate") ? LocalDate.parse(patch.get("dueDate")) : old.getDueDate());
        next.setStatus(WAgentStatus.GoalStatus.DRAFT.value());
        next.setSlotsSourceJson(patchOrDefault(patch, "slotsSourceJson", old.getSlotsSourceJson()));
        next.setInferredCount(old.getInferredCount());
        next.setSupersedesGoalId(old.getId());
        next.setTenantId(old.getTenantId());
        next.setOrgId(old.getOrgId());
        next.setVersionNo(String.valueOf(parseVersion(old.getVersionNo()) + 1));
        next.setCreateBy(operator);
        next.setUpdateBy(operator);
        goalDao.insert(next);
        publishGoalEvent(EventTypes.Agent.GOAL_VERSIONED, next, operator);
        return next;
    }

    /**
     * 就地更新（非口径修订的运营字段面）：<b>已被 Run 引用 ⇒ 409 ECOS_WA_CALIBER_LOCKED</b>
     * （F10-07；被引用走 {@link #revise} 新版本链，{@code Run.previous_run_id} 记链）。
     */
    public WAgentGoalDO updateInPlace(String goalId, Map<String, String> patch, String operator) {
        WAgentGoalDO goal = load(goalId);
        if (isReferencedByRun(goalId)) {
            throw WAgentOrchestrationException.caliberLocked(goalId);
        }
        goal.setName(patchOrDefault(patch, "name", goal.getName()));
        goal.setSuccessCriteriaText(patchOrDefault(patch, "successCriteriaText", goal.getSuccessCriteriaText()));
        goal.setOwnerId(patchOrDefault(patch, "ownerId", goal.getOwnerId()));
        String status = patch.get("status");
        if (status != null) {
            if (!WAgentStatus.GoalStatus.isValid(status)) {
                throw WAgentOrchestrationException.validation("goal status 越界（单源 WAgentStatus.GoalStatus）: " + status);
            }
            goal.setStatus(status.trim().toLowerCase(java.util.Locale.ROOT));
        }
        goal.setUpdateBy(operator);
        goalDao.update(goal);
        return goal;
    }

    /** 读取（跨租户按 E-NOT-FOUND 掩蔽，与 403 文案不可区分，§5.3 防枚举）。 */
    public WAgentGoalDO load(String goalId) {
        return goalDao.findById(goalId).orElseThrow(WAgentOrchestrationException::notFound);
    }

    public Optional<WAgentGoalDO> find(String goalId) {
        return goalDao.findById(goalId);
    }

    /** 引用探测链：Goal→Mission（V228 goal_id）→Run（V233 mission_id）任一非空即锁定。 */
    public boolean isReferencedByRun(String goalId) {
        for (WAgentMissionDO mission : missionDao.findByGoalId(goalId)) {
            if (!runDao.findByMissionId(mission.getId()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 版本链上的当前最新代（supersedes 链末端；无后继即自身为最新）。 */
    public WAgentGoalDO latestVersion(String goalId) {
        WAgentGoalDO current = load(goalId);
        // 防御环路：链深上限取显式 64（确定性遍历，禁递归失控）
        for (int depth = 0; depth < 64; depth++) {
            var successors = goalDao.findBySupersedesGoalId(current.getId());
            if (successors.isEmpty()) {
                return current;
            }
            current = successors.get(0);
        }
        throw new IllegalStateException("Goal 版本链超深（疑似 supersedes 环）: " + goalId);
    }

    private void publishGoalEvent(String eventType, WAgentGoalDO goal, String operator) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("goalId", goal.getId());
        payload.put("goalType", goal.getGoalType());
        payload.put("versionNo", goal.getVersionNo());
        payload.put("supersedesGoalId", goal.getSupersedesGoalId());
        eventPublisher.publish(eventType, WAgentEvent.AGGREGATE_MISSION, goal.getId(),
                goal.getTenantId(), null, operator, payload);
    }

    private String writeSlots(Map<String, String> slotsSource) {
        if (slotsSource == null || slotsSource.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : slotsSource.entrySet()) {
            if (!SlotProvenance.ALL.contains(entry.getValue())) {
                throw WAgentOrchestrationException.validation(
                        "槽位来源越界（SlotProvenance 四值单源）: " + entry.getKey() + "=" + entry.getValue());
            }
        }
        try {
            return objectMapper.writeValueAsString(slotsSource);
        } catch (Exception e) {
            throw WAgentOrchestrationException.validation("slots_source 序列化失败");
        }
    }

    private Integer countInferred(Map<String, String> slotsSource) {
        if (slotsSource == null) {
            return 0;
        }
        return (int) slotsSource.values().stream()
                .filter(SlotProvenance.AGENT_INFERRED.value()::equals)
                .count();
    }

    private static String patchOrDefault(Map<String, String> patch, String key, String fallback) {
        String v = patch == null ? null : patch.get(key);
        return v != null ? v : fallback;
    }

    private static int parseVersion(String versionNo) {
        try {
            return Integer.parseInt(versionNo.trim());
        } catch (RuntimeException e) {
            return 1;
        }
    }

    private static void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new WAgentOrchestrationException(
                    com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode.E_AUTH,
                    "tenant_id 缺失（REQ-PLT-24/25 承；网关未注入租户上下文即拒绝，禁匿名落库）", null);
        }
    }
}
