package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessGapDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessItemDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessGapDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessItemDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentQuestionService;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.ReadinessGapService;
import com.chinacreator.gzcm.engine.ai.wagent.readiness.ReadinessService;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Readiness 端点（详细设计-10 §5.2 #12 getReadiness / #13 refillReadiness，F10-08/09）。
 *
 * <p><b>#12 只读实装</b>：取问题当前评估（V230 {@code is_pinned DESC, valid_until DESC} 首行）
 * + 缺项（V231 item）+ 缺口（V232 gap，未 resolved 面）。租户校验双闸：问题面经
 * {@link WAgentQuestionService#get}（跨租户掩 404 防枚举，§5.3），评估行再核
 * {@code tenant_id} 不符同掩 404；<b>无评估行 ⇒ 404 E-NOT-FOUND</b>（禁合成"默认 A 级"伪数据）。</p>
 *
 * <p><b>#13 实装（原 501 已去）</b>：补齐循环（≤3）经 {@link ReadinessService#refill} 执行
 * 缺口派发 + 复评落库；轮次台账单源在 Run 的 {@code consumption_json}（F10-11），
 * {@link #MAX_REFILL_ROUNDS} 只作口径锚并在漂移时打 WARN。未接线的引擎侧补数端口
 * 不复现 501，而是在响应 {@code deferred_actions} 中如实列出（差额可见，禁伪称已补齐）。</p>
 *
 * <p><b>#147CN（DoD DI-2 第十七批）</b>：响应视图由 Map 改 record（snake_case 键用
 * {@code @JsonProperty} 钉住，{@code layers} 的 D/I/K/C 四键同法），请求体改
 * {@link RefillRequest}（字段仍按「必须是字符串/字符串数组」显式校验，拒绝语义不变）。
 * {@code deferred_actions} 保持弱类型条目列表——其条目由派发侧
 * （{@code DispatchResult}）定义，属主契约另批。</p>
 */
@RestController
public class WAgentReadinessController {

    private static final Logger log = LoggerFactory.getLogger(WAgentReadinessController.class);

    /**
     * #13 补齐循环上限（§5.2 表"循环 ≤3"）。
     *
     * <p><b>口径锚，不是运行时单源</b>：实际判定用预算快照的
     * {@code max_refill_rounds}（sysman 配置单源，F10-11）。此常量只作两件事——
     * ①接线前的口径冻结位（防口径漂移）；②快照值与本锚不一致时打 WARN 暴露漂移。
     * 禁在此改数值来"放宽循环"。</p>
     */
    public static final int MAX_REFILL_ROUNDS = 3;

    private final WAgentQuestionService questionService;
    private final WAgentReadinessDao readinessDao;
    private final WAgentReadinessItemDao readinessItemDao;
    private final WAgentReadinessGapDao readinessGapDao;
    private final WAgentIdentityResolver identityResolver;
    private final ReadinessService readinessService;

    public WAgentReadinessController(WAgentQuestionService questionService,
                                     WAgentReadinessDao readinessDao,
                                     WAgentReadinessItemDao readinessItemDao,
                                     WAgentReadinessGapDao readinessGapDao,
                                     WAgentIdentityResolver identityResolver,
                                     ReadinessService readinessService) {
        this.questionService = questionService;
        this.readinessDao = readinessDao;
        this.readinessItemDao = readinessItemDao;
        this.readinessGapDao = readinessGapDao;
        this.identityResolver = identityResolver;
        this.readinessService = readinessService;
    }

    /** #12 等级 + 缺项 + 缺口（当前评估快照，只读）。 */
    @GetMapping(WAgentApiPaths.READINESS_BY_QUESTION)
    public ApiResponse<ReadinessVO> getReadiness(
            @PathVariable(WAgentApiPaths.P_QUESTION_ID) String questionId) {
        Identity identity = identityResolver.require();
        questionService.get(questionId, identity.tenantId()); // 租户闸（掩 404）
        WAgentReadinessDO assessment = readinessDao.findCurrentByQuestionId(questionId)
                .filter(a -> identity.tenantId().equals(a.getTenantId()))
                .orElseThrow(WAgentOrchestrationException::notFound);
        return WAgentRunController.ok(ReadinessVO.of(assessmentView(assessment),
                itemView(readinessItemDao.findByAssessmentId(assessment.getId())),
                gapView(readinessGapDao.findByAssessmentId(assessment.getId()))));
    }

    /**
     * #13 缺口补齐（循环 ≤3，F10-09）。
     *
     * <p>实装路径：存在性/租户校验 → {@link ReadinessService#refill}（派发缺口处置任务 + 复评落
     * V230/231/232 新行 + 旧行置 {@code superseded_by}）。响应面 snake_case（详设-10 §5.2 表未规定
     * camelCase，与本域读面 #12 同形，裁决 D-B2④-16）。</p>
     *
     * <p><b>超限也是 200</b>（硬规则⑤）：{@code awaiting_input=true} + 当前缺口清单，语义是
     * "这里需要人"，不是"调用方重试就能过"；此时不派发、不复评、不计轮。</p>
     */
    @PostMapping(WAgentApiPaths.READINESS_REFILL)
    public ResponseEntity<ApiResponse<RefillVO>> refillReadiness(
            @PathVariable(WAgentApiPaths.P_QUESTION_ID) String questionId,
            @RequestBody(required = false) RefillRequest body) {
        Identity identity = identityResolver.require();
        WAgentQuestionDO question = questionService.get(questionId, identity.tenantId()); // 租户闸（掩 404）
        WAgentReadinessDO assessment = readinessDao.findCurrentByQuestionId(questionId)
                .filter(a -> identity.tenantId().equals(a.getTenantId()))
                .orElseThrow(WAgentOrchestrationException::notFound);

        String actionType = asTrimmedString(body == null ? null : body.getActionType(), "action_type");
        if (actionType != null) {
            ReadinessGapService.assertActionType(actionType); // 非法类型 ⇒ E-VALIDATION，禁静默按默认派发
        }
        List<String> gapIds = asStringList(body == null ? null : body.getGapIds(), "gap_ids");

        ReadinessService.RefillResult result =
                readinessService.refill(question, assessment, gapIds, actionType, identity.operator());
        if (result.maxRefillRounds() != MAX_REFILL_ROUNDS) {
            log.warn("[F10-09] 循环上限口径漂移: 快照={} 锚={} questionId={}",
                    result.maxRefillRounds(), MAX_REFILL_ROUNDS, questionId);
        }
        return ResponseEntity.ok(WAgentRunController.ok(refillView(questionId, result)));
    }

    /**
     * 请求体可选字段：缺位返 null；非字符串直接拒（禁 toString 伪造），空白视同缺位。
     * <p>字段声明为 {@code Object} 是为了保留旧实现「非字符串 ⇒ E-VALIDATION」的拒绝语义——
     * 若声明 {@code String}，Jackson 会把数字强转成字符串从而放过本应拒绝的入参。</p>
     */
    private static String asTrimmedString(Object value, String key) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String s)) {
            throw WAgentOrchestrationException.validation(key + " 必须是字符串");
        }
        return s.isBlank() ? null : s.trim();
    }

    /** {@code gap_ids}：必须是字符串数组；空数组与缺位同义（= 本次评估全部 open 缺口）。 */
    private static List<String> asStringList(Object value, String key) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw WAgentOrchestrationException.validation(key + " 必须是字符串数组");
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof String s) || s.isBlank()) {
                throw WAgentOrchestrationException.validation(key + " 元素必须是非空字符串");
            }
            out.add(s.trim());
        }
        return out;
    }

    /** #13 响应视图（只出受控字段；缺口明细复用 #12 的 {@link #gapView}，禁另立一套字段名）。 */
    static RefillVO refillView(String questionId, ReadinessService.RefillResult r) {
        return new RefillVO(r.assessmentId(), questionId, r.previousGrade(), r.newGrade(),
                r.refillRound(), r.maxRefillRounds(), r.awaitingInput(), r.taskIds(), r.deferred(),
                gapView(r.openGaps()));
    }

    // ── 视图（#147CN：键名＝旧 Map 键，键序不变） ─────────────────

    static AssessmentVO assessmentView(WAgentReadinessDO a) {
        return new AssessmentVO(a.getId(), a.getQuestionId(), a.getRunId(), a.getProfileId(),
                a.getProfileVersion(), a.getGrade(),
                new LayersVO(nullSafe(a.getLayerD()), nullSafe(a.getLayerI()),
                        nullSafe(a.getLayerK()), nullSafe(a.getLayerC())),
                a.getLayerSummaryJson(), a.getDegradationsJson(),
                a.getComputedTime() == null ? null : a.getComputedTime().toString(),
                a.getValidUntil() == null ? null : a.getValidUntil().toString(),
                a.getValidUntil() != null && a.getValidUntil().isBefore(LocalDateTime.now()),
                a.getIsPinned(), a.getSupersededBy());
    }

    private static List<ReadinessItemVO> itemView(List<WAgentReadinessItemDO> items) {
        List<ReadinessItemVO> list = new ArrayList<>();
        for (WAgentReadinessItemDO i : items) {
            list.add(new ReadinessItemVO(i.getId(), i.getLayer(), i.getCheckCode(), i.getStatus(),
                    i.getMeasuredJson(), i.getThresholdJson(), i.getEvidenceIdsJson()));
        }
        return list;
    }

    static List<ReadinessGapVO> gapView(List<WAgentReadinessGapDO> gaps) {
        List<ReadinessGapVO> list = new ArrayList<>();
        for (WAgentReadinessGapDO g : gaps) {
            list.add(new ReadinessGapVO(g.getId(), g.getLayer(), g.getSeverity(),
                    g.getDescriptionText(), g.getImpactText(), g.getSuggestedActionText(),
                    g.getSuggestedOwner(), g.getEffortEstimate(), g.getUnlockFrom(), g.getUnlockTo(),
                    g.getStatus(), g.getResolvedTime() == null ? null : g.getResolvedTime().toString()));
        }
        return list;
    }

    private static String nullSafe(String v) {
        return v == null ? "unknown" : v;
    }

    /** 补齐请求（字段按「必须是字符串 / 字符串数组」显式校验，拒绝语义同旧 Map 读取）。 */
    public static final class RefillRequest {
        @JsonProperty("action_type")
        private Object actionType;
        @JsonProperty("gap_ids")
        private Object gapIds;

        public Object getActionType() {
            return actionType;
        }

        public void setActionType(Object actionType) {
            this.actionType = actionType;
        }

        public Object getGapIds() {
            return gapIds;
        }

        public void setGapIds(Object gapIds) {
            this.gapIds = gapIds;
        }
    }

    /** 四层等级（D/I/K/C 四键，键名大写由注解钉住）。 */
    public record LayersVO(@JsonProperty("D") String d, @JsonProperty("I") String i,
                           @JsonProperty("K") String k, @JsonProperty("C") String c) {
    }

    /** 评估快照（15 键，snake_case）。 */
    public record AssessmentVO(@JsonProperty("assessment_id") String assessmentId,
                               @JsonProperty("question_id") String questionId,
                               @JsonProperty("run_id") String runId,
                               @JsonProperty("profile_id") String profileId,
                               @JsonProperty("profile_version") String profileVersion,
                               @JsonProperty("grade") String grade,
                               @JsonProperty("layers") LayersVO layers,
                               @JsonProperty("layer_summary_json") String layerSummaryJson,
                               @JsonProperty("degradations_json") String degradationsJson,
                               @JsonProperty("computed_time") String computedTime,
                               @JsonProperty("valid_until") String validUntil,
                               @JsonProperty("expired") boolean expired,
                               @JsonProperty("is_pinned") Integer isPinned,
                               @JsonProperty("superseded_by") String supersededBy) {
    }

    /** 缺项（7 键）。 */
    public record ReadinessItemVO(@JsonProperty("item_id") String itemId,
                                  @JsonProperty("layer") String layer,
                                  @JsonProperty("check_code") String checkCode,
                                  @JsonProperty("status") String status,
                                  @JsonProperty("measured_json") String measuredJson,
                                  @JsonProperty("threshold_json") String thresholdJson,
                                  @JsonProperty("evidence_ids_json") String evidenceIdsJson) {
    }

    /** 缺口（12 键）。 */
    public record ReadinessGapVO(@JsonProperty("gap_id") String gapId,
                                 @JsonProperty("layer") String layer,
                                 @JsonProperty("severity") String severity,
                                 @JsonProperty("description_text") String descriptionText,
                                 @JsonProperty("impact_text") String impactText,
                                 @JsonProperty("suggested_action_text") String suggestedActionText,
                                 @JsonProperty("suggested_owner") String suggestedOwner,
                                 @JsonProperty("effort_estimate") String effortEstimate,
                                 @JsonProperty("unlock_from") String unlockFrom,
                                 @JsonProperty("unlock_to") String unlockTo,
                                 @JsonProperty("status") String status,
                                 @JsonProperty("resolved_time") String resolvedTime) {
    }

    /** #12 响应（评估 14 键<b>平铺</b> + items + gaps，共 16 键——旧实现就是平铺后追加两键）。 */
    public record ReadinessVO(@JsonProperty("assessment_id") String assessmentId,
                              @JsonProperty("question_id") String questionId,
                              @JsonProperty("run_id") String runId,
                              @JsonProperty("profile_id") String profileId,
                              @JsonProperty("profile_version") String profileVersion,
                              @JsonProperty("grade") String grade,
                              @JsonProperty("layers") LayersVO layers,
                              @JsonProperty("layer_summary_json") String layerSummaryJson,
                              @JsonProperty("degradations_json") String degradationsJson,
                              @JsonProperty("computed_time") String computedTime,
                              @JsonProperty("valid_until") String validUntil,
                              @JsonProperty("expired") boolean expired,
                              @JsonProperty("is_pinned") Integer isPinned,
                              @JsonProperty("superseded_by") String supersededBy,
                              @JsonProperty("items") List<ReadinessItemVO> items,
                              @JsonProperty("gaps") List<ReadinessGapVO> gaps) {

        static ReadinessVO of(AssessmentVO a, List<ReadinessItemVO> items, List<ReadinessGapVO> gaps) {
            return new ReadinessVO(a.assessmentId(), a.questionId(), a.runId(), a.profileId(),
                    a.profileVersion(), a.grade(), a.layers(), a.layerSummaryJson(),
                    a.degradationsJson(), a.computedTime(), a.validUntil(), a.expired(),
                    a.isPinned(), a.supersededBy(), items, gaps);
        }
    }

    /** #13 响应（10 键；deferred_actions 条目属派发侧契约）。 */
    public record RefillVO(@JsonProperty("assessment_id") String assessmentId,
                           @JsonProperty("question_id") String questionId,
                           @JsonProperty("previous_grade") String previousGrade,
                           @JsonProperty("new_grade") String newGrade,
                           @JsonProperty("refill_round") int refillRound,
                           @JsonProperty("max_refill_rounds") int maxRefillRounds,
                           @JsonProperty("awaiting_input") boolean awaitingInput,
                           @JsonProperty("task_ids") List<String> taskIds,
                           @JsonProperty("deferred_actions") List<Map<String, Object>> deferredActions,
                           @JsonProperty("gaps") List<ReadinessGapVO> gaps) {
    }
}
