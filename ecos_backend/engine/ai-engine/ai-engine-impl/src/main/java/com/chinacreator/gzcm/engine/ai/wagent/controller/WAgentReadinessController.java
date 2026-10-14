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
import java.util.LinkedHashMap;
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
    public ApiResponse<Map<String, Object>> getReadiness(
            @PathVariable(WAgentApiPaths.P_QUESTION_ID) String questionId) {
        Identity identity = identityResolver.require();
        questionService.get(questionId, identity.tenantId()); // 租户闸（掩 404）
        WAgentReadinessDO assessment = readinessDao.findCurrentByQuestionId(questionId)
                .filter(a -> identity.tenantId().equals(a.getTenantId()))
                .orElseThrow(WAgentOrchestrationException::notFound);
        Map<String, Object> data = assessmentView(assessment);
        data.put("items", itemView(readinessItemDao.findByAssessmentId(assessment.getId())));
        data.put("gaps", gapView(readinessGapDao.findByAssessmentId(assessment.getId())));
        return WAgentRunController.ok(data);
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> refillReadiness(
            @PathVariable(WAgentApiPaths.P_QUESTION_ID) String questionId,
            @RequestBody(required = false) Map<String, Object> body) {
        Identity identity = identityResolver.require();
        WAgentQuestionDO question = questionService.get(questionId, identity.tenantId()); // 租户闸（掩 404）
        WAgentReadinessDO assessment = readinessDao.findCurrentByQuestionId(questionId)
                .filter(a -> identity.tenantId().equals(a.getTenantId()))
                .orElseThrow(WAgentOrchestrationException::notFound);

        String actionType = readString(body, "action_type");
        if (actionType != null) {
            ReadinessGapService.assertActionType(actionType); // 非法类型 ⇒ E-VALIDATION，禁静默按默认派发
        }
        List<String> gapIds = readStringList(body, "gap_ids");

        ReadinessService.RefillResult result =
                readinessService.refill(question, assessment, gapIds, actionType, identity.operator());
        if (result.maxRefillRounds() != MAX_REFILL_ROUNDS) {
            log.warn("[F10-09] 循环上限口径漂移: 快照={} 锚={} questionId={}",
                    result.maxRefillRounds(), MAX_REFILL_ROUNDS, questionId);
        }
        return ResponseEntity.ok(WAgentRunController.ok(refillView(questionId, result)));
    }

    /** #13 响应视图（只出受控字段；缺口明细复用 #12 的 {@code gapView}，禁另立一套字段名）。 */
    static Map<String, Object> refillView(String questionId, ReadinessService.RefillResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assessment_id", r.assessmentId());
        m.put("question_id", questionId);
        m.put("previous_grade", r.previousGrade());
        m.put("new_grade", r.newGrade());
        m.put("refill_round", r.refillRound());
        m.put("max_refill_rounds", r.maxRefillRounds());
        m.put("awaiting_input", r.awaitingInput());
        m.put("task_ids", r.taskIds());
        m.put("deferred_actions", r.deferred());
        m.put("gaps", gapView(r.openGaps()));
        return m;
    }

    /** 请求体可选字段读取：缺位返 null（由服务层走默认），非字符串类型直接拒（禁 toString 伪造）。 */
    private static String readString(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object v = body.get(key);
        if (v == null) {
            return null;
        }
        if (!(v instanceof String s)) {
            throw WAgentOrchestrationException.validation(key + " 必须是字符串");
        }
        return s.isBlank() ? null : s.trim();
    }

    /** {@code gap_ids} 读取：必须是字符串数组；空数组与缺位同义（= 本次评估全部 open 缺口）。 */
    private static List<String> readStringList(Map<String, Object> body, String key) {
        if (body == null) {
            return List.of();
        }
        Object v = body.get(key);
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> list)) {
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

    // ── 视图（只出受控字段；JSON 列原样透出字符串形态，禁二次解析伪造）──

    static Map<String, Object> assessmentView(WAgentReadinessDO a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assessment_id", a.getId());
        m.put("question_id", a.getQuestionId());
        m.put("run_id", a.getRunId());
        m.put("profile_id", a.getProfileId());
        m.put("profile_version", a.getProfileVersion());
        m.put("grade", a.getGrade());
        m.put("layers", Map.of("D", nullSafe(a.getLayerD()), "I", nullSafe(a.getLayerI()),
                "K", nullSafe(a.getLayerK()), "C", nullSafe(a.getLayerC())));
        m.put("layer_summary_json", a.getLayerSummaryJson());
        m.put("degradations_json", a.getDegradationsJson());
        m.put("computed_time", a.getComputedTime() == null ? null : a.getComputedTime().toString());
        m.put("valid_until", a.getValidUntil() == null ? null : a.getValidUntil().toString());
        m.put("expired", a.getValidUntil() != null && a.getValidUntil().isBefore(LocalDateTime.now()));
        m.put("is_pinned", a.getIsPinned());
        m.put("superseded_by", a.getSupersededBy());
        return m;
    }

    private static List<Map<String, Object>> itemView(List<WAgentReadinessItemDO> items) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (WAgentReadinessItemDO i : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item_id", i.getId());
            m.put("layer", i.getLayer());
            m.put("check_code", i.getCheckCode());
            m.put("status", i.getStatus());
            m.put("measured_json", i.getMeasuredJson());
            m.put("threshold_json", i.getThresholdJson());
            m.put("evidence_ids_json", i.getEvidenceIdsJson());
            list.add(m);
        }
        return list;
    }

    private static List<Map<String, Object>> gapView(List<WAgentReadinessGapDO> gaps) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (WAgentReadinessGapDO g : gaps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("gap_id", g.getId());
            m.put("layer", g.getLayer());
            m.put("severity", g.getSeverity());
            m.put("description_text", g.getDescriptionText());
            m.put("impact_text", g.getImpactText());
            m.put("suggested_action_text", g.getSuggestedActionText());
            m.put("suggested_owner", g.getSuggestedOwner());
            m.put("effort_estimate", g.getEffortEstimate());
            m.put("unlock_from", g.getUnlockFrom());
            m.put("unlock_to", g.getUnlockTo());
            m.put("status", g.getStatus());
            m.put("resolved_time", g.getResolvedTime() == null ? null : g.getResolvedTime().toString());
            list.add(m);
        }
        return list;
    }

    private static String nullSafe(String v) {
        return v == null ? "unknown" : v;
    }
}
