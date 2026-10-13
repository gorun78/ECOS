package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessGapDao;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentReadinessItemDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessGapDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentReadinessItemDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentQuestionService;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
 * <p><b>#13 明确降级（无假真理）</b>：补齐循环（≤3）依赖缺口回填引擎侧执行面
 * （data/ontology/kb 引擎补数端口 + readiness 重算器均未接线）⇒ 存在性/租户校验通过后
 * 如实 {@code 501 NOT_IMPLEMENTED}；循环上限 ≤3 的约束登记于此，接线时消费。</p>
 */
@RestController
public class WAgentReadinessController {

    /** #13 补齐循环上限（§5.2 表"循环 ≤3"；未实装先冻结口径，防接线时口径漂移）。 */
    public static final int MAX_REFILL_ROUNDS = 3;

    private final WAgentQuestionService questionService;
    private final WAgentReadinessDao readinessDao;
    private final WAgentReadinessItemDao readinessItemDao;
    private final WAgentReadinessGapDao readinessGapDao;
    private final WAgentIdentityResolver identityResolver;

    public WAgentReadinessController(WAgentQuestionService questionService,
                                     WAgentReadinessDao readinessDao,
                                     WAgentReadinessItemDao readinessItemDao,
                                     WAgentReadinessGapDao readinessGapDao,
                                     WAgentIdentityResolver identityResolver) {
        this.questionService = questionService;
        this.readinessDao = readinessDao;
        this.readinessItemDao = readinessItemDao;
        this.readinessGapDao = readinessGapDao;
        this.identityResolver = identityResolver;
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

    /** #13 缺口补齐（循环 ≤3）——引擎侧补数端口未接线：如实 501，禁伪称已补齐。 */
    @PostMapping(WAgentApiPaths.READINESS_REFILL)
    public ResponseEntity<ApiResponse<Map<String, Object>>> refillReadiness(
            @PathVariable(WAgentApiPaths.P_QUESTION_ID) String questionId) {
        Identity identity = identityResolver.require();
        questionService.get(questionId, identity.tenantId()); // 存在性/租户校验先行（fail-fast）
        readinessDao.findCurrentByQuestionId(questionId)
                .filter(a -> identity.tenantId().equals(a.getTenantId()))
                .orElseThrow(WAgentOrchestrationException::notFound);
        return WAgentDegradation.notImplemented(
                "F10-08/09 缺口回填引擎侧执行面+readiness 重算器未接线（循环上限 MAX_REFILL_ROUNDS="
                        + MAX_REFILL_ROUNDS + " 已冻结）");
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
