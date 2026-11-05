package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentGoalDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.SlotFillingService;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentGoalService;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Goal 端点（详细设计-10 §5.2 #1 createGoal / #2 reviseGoal，F10-05/F10-07）。
 *
 * <p><b>双路径口径</b>：只映射 {@link WAgentApiPaths} 正典前缀 {@code /api/v1/wagent}；
 * legacy {@code /api/wagent} 由 {@code VersionPrefixRewriteFilter} REVERSE 重写收敛（#127 已登记），
 * 控制器禁直接映射裸前缀（§5.1 只增不改）。</p>
 *
 * <p><b>#2 的路由判定（口径锁 F10-07 的 web 面形态）</b>：PATCH 体只要含任一口径字段
 * （{@link #CALIBER_FIELDS}）⇒ 走 {@link WAgentGoalService#revise}（<b>只写新版本</b>，
 * supersedes 链）；纯运营字段 ⇒ {@link WAgentGoalService#updateInPlace}
 * （已被 Run 引用即 409 {@code ECOS_WA_CALIBER_LOCKED}）。判定确定性、无 LLM。</p>
 */
@RestController
public class WAgentGoalController {

    /** 口径字段名单源（命中任一 ⇒ 修订走新版本链，F10-07）。 */
    static final Set<String> CALIBER_FIELDS = Set.of(
            "caliberVersion", "asOfDate", "metricVersion", "metricId", "scopeJson", "goalType", "timeRangeText");

    private final WAgentGoalService goalService;
    private final SlotFillingService slotFillingService;
    private final WAgentIdentityResolver identityResolver;

    public WAgentGoalController(WAgentGoalService goalService, SlotFillingService slotFillingService,
                                WAgentIdentityResolver identityResolver) {
        this.goalService = goalService;
        this.slotFillingService = slotFillingService;
        this.identityResolver = identityResolver;
    }

    /** #1 创建请求体（字段名与 V227 列 camel 形态；日期 ISO 字符串）。 */
    public record GoalCreateRequest(String name, String goalType, String scopeJson, String timeRangeText,
                                    String asOfDate, String metricId, String metricVersion,
                                    String caliberVersion, String successCriteriaText, String ownerId,
                                    String dueDate, String orgId, Map<String, String> slotsSource) {
    }

    /** Goal 视图（12 键；旧 Map 逐键同名同序，全推断提醒位为确定性布尔）。 */
    public record GoalVO(@JsonProperty("goal_id") String goalId,
                         @JsonProperty("name") String name,
                         @JsonProperty("goal_type") String goalType,
                         @JsonProperty("status") String status,
                         @JsonProperty("version_no") String versionNo,
                         @JsonProperty("supersedes_goal_id") String supersedesGoalId,
                         @JsonProperty("caliber_version") String caliberVersion,
                         @JsonProperty("metric_id") String metricId,
                         @JsonProperty("metric_version") String metricVersion,
                         @JsonProperty("as_of_date") String asOfDate,
                         @JsonProperty("inferred_count") Integer inferredCount,
                         @JsonProperty("all_slots_inferred_warning") boolean allSlotsInferredWarning) {
    }

    @PostMapping(WAgentApiPaths.GOALS_COLLECTION)
    public ResponseEntity<ApiResponse<GoalVO>> createGoal(@RequestBody GoalCreateRequest req) {
        Identity identity = identityResolver.require();
        WAgentGoalDO goal = goalService.create(new WAgentGoalService.GoalCreateCommand(
                identity.tenantId(), req.orgId() == null ? identity.orgId() : req.orgId(),
                identity.operator(), req.name(), req.goalType(), req.scopeJson(), req.timeRangeText(),
                parseDate(req.asOfDate(), "asOfDate"), req.metricId(), req.metricVersion(),
                req.caliberVersion(), req.successCriteriaText(), req.ownerId(),
                parseDate(req.dueDate(), "dueDate"), req.slotsSource()));
        return ResponseEntity.status(201)
                .header("Location", WAgentApiPaths.resolve(WAgentApiPaths.GOAL_REVISE,
                        WAgentApiPaths.P_GOAL_ID, goal.getId()))
                .body(WAgentRunController.ok(view(goal)));
    }

    /** #2 修订/运营更新（含口径字段 ⇒ 新版本链；否则就地更新且被引用即 409）。 */
    @PatchMapping(WAgentApiPaths.GOAL_REVISE)
    public ApiResponse<GoalVO> reviseGoal(@PathVariable(WAgentApiPaths.P_GOAL_ID) String goalId,
                                          @RequestBody Map<String, String> patch) {
        Identity identity = identityResolver.require();
        // 先触读取校验（存在性 + 租户掩蔽经 revise 内 load；就地面对象面自校验）
        WAgentGoalDO result = touchesCaliber(patch)
                ? goalService.revise(goalId, patch, identity.operator(), identity.tenantId())
                : goalService.updateInPlace(goalId, patch, identity.operator());
        return WAgentRunController.ok(view(result));
    }

    static boolean touchesCaliber(Map<String, String> patch) {
        if (patch == null) {
            return false;
        }
        return patch.keySet().stream()
                .anyMatch(k -> CALIBER_FIELDS.contains(k == null ? "" : k.trim()));
    }

    private static LocalDate parseDate(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim().toLowerCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw com.chinacreator.gzcm.engine.ai.wagent.orchestrator
                    .WAgentOrchestrationException.validation(field + " 需 ISO 日期（yyyy-MM-dd）");
        }
    }

    /** Goal 视图（只出受控字段；全推断提醒位 = AC-02 数据基，确定性布尔非 LLM）。 */
    private GoalVO view(WAgentGoalDO goal) {
        return new GoalVO(goal.getId(), goal.getName(), goal.getGoalType(), goal.getStatus(),
                goal.getVersionNo(), goal.getSupersedesGoalId(), goal.getCaliberVersion(),
                goal.getMetricId(), goal.getMetricVersion(),
                goal.getAsOfDate() == null ? null : goal.getAsOfDate().toString(),
                goal.getInferredCount(), slotFillingService.raisesAllInferredWarning(goal));
    }
}
