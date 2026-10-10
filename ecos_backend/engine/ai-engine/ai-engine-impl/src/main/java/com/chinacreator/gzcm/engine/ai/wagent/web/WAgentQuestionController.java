package com.chinacreator.gzcm.engine.ai.wagent.web;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.trace.AiTraceSupport;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.IntentClassifyPort;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.SlotFillingService;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentQuestionService;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Question 端点（详细设计-10 §5.2 #3 createQuestion / #4 getQuestion，F10-05/F10-06）。
 *
 * <p>#3 的 <b>CLARIFY 标记出口</b>：置信不足或分类器不可用 ⇒ {@code outcome=CLARIFY} +
 * 本轮澄清项（≤3，{@link SlotFillingService#MAX_CLARIFICATION_ITEMS_PER_ROUND}）；
 * <b>不建 Run</b>（禁 fail-open，F10-05 明文）。#4 含 {@code latest_run} 摘要
 * （Run 只读经 DAO，Agent 不直连库表）。</p>
 *
 * <p>路径口径同 {@link WAgentGoalController}：只映射 v1 正典前缀，legacy 由滤波器反写。</p>
 */
@RestController
public class WAgentQuestionController {

    /** 结果标记单源（§5.2 #3 "意图识别 + 澄清返回 CLARIFY"）。 */
    public static final String OUTCOME_READY = "READY";
    public static final String OUTCOME_CLARIFY = "CLARIFY";

    private final WAgentQuestionService questionService;
    private final SlotFillingService slotFillingService;
    private final WAgentRunDao runDao;
    private final WAgentIdentityResolver identityResolver;

    public WAgentQuestionController(WAgentQuestionService questionService,
                                    SlotFillingService slotFillingService,
                                    WAgentRunDao runDao,
                                    WAgentIdentityResolver identityResolver) {
        this.questionService = questionService;
        this.slotFillingService = slotFillingService;
        this.runDao = runDao;
        this.identityResolver = identityResolver;
    }

    /** #3 创建请求体（slots 任意 JSON 对象；dependsOn 前序问题 id 列表，环路服务端检测）。 */
    public record QuestionCreateRequest(String missionId, String rawText,
                                        Map<String, Object> slots, List<String> dependsOn, String orgId) {
    }

    @PostMapping(WAgentApiPaths.QUESTIONS_COLLECTION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> createQuestion(
            @RequestBody QuestionCreateRequest req) {
        Identity identity = identityResolver.require();
        WAgentQuestionService.QuestionOutcome outcome = questionService.fromUtterance(
                new WAgentQuestionService.QuestionCreateCommand(identity.tenantId(),
                        req.orgId() == null ? identity.orgId() : req.orgId(), identity.operator(),
                        req.missionId(), req.rawText(), req.slots(), req.dependsOn()));
        Map<String, Object> data = questionView(outcome.question());
        data.put("outcome", outcome.requiresClarify() ? OUTCOME_CLARIFY : OUTCOME_READY);
        data.put("requires_clarify", outcome.requiresClarify());
        if (outcome.requiresClarify()) {
            // 澄清项确定性生成：意图未立 ⇒ intent 必列；一轮 ≤3（F10-06 明文裁剪）
            data.put("clarification_items", slotFillingService.nextClarificationItems(
                    outcome.question().getIntent() == null ? List.of("intent", "time_range", "scope")
                            : List.of("intent")));
            IntentClassifyPort.Verdict verdict = outcome.verdict();
            data.put("classifier_available", verdict == null ? false : verdict.classifierAvailable());
            data.put("confidence_threshold", IntentClassifyPort.CONFIDENCE_MIN.toPlainString());
        }
        return ResponseEntity.status(201).body(ok(data));
    }

    /** #4 读取（含 latest_run 摘要；跨租户掩 404，§5.3 防枚举）。 */
    @GetMapping(WAgentApiPaths.QUESTION_BY_ID)
    public ApiResponse<Map<String, Object>> getQuestion(
            @PathVariable(WAgentApiPaths.P_QUESTION_ID) String questionId) {
        Identity identity = identityResolver.require();
        WAgentQuestionDO question = questionService.get(questionId, identity.tenantId());
        Map<String, Object> data = questionView(question);
        if (question.getLatestRunId() != null && !question.getLatestRunId().isBlank()) {
            data.put("latest_run", runDao.findById(question.getLatestRunId())
                    .map(WAgentQuestionController::runSummary).orElse(null));
        }
        return ok(data);
    }

    private static Map<String, Object> questionView(WAgentQuestionDO q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("question_id", q.getId());
        m.put("mission_id", q.getMissionId());
        m.put("intent", q.getIntent());
        m.put("intent_confidence", q.getIntentConfidence());
        m.put("status", q.getStatus());
        m.put("latest_run_id", q.getLatestRunId());
        m.put("answer_summary_text", q.getAnswerSummaryText());
        return m;
    }

    private static Map<String, Object> runSummary(WAgentRunDO run) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("run_id", run.getId());
        m.put("status", run.getStatus());
        m.put("plan_source", run.getPlanSource());
        m.put("automation_ceiling", run.getAutomationCeiling());
        return m;
    }

    /** traceId 兜底自取（成功面同 Advice 同源，禁第二套 trace 源）。 */
    static ApiResponse<Map<String, Object>> ok(Map<String, Object> data) {
        return ApiResponse.success(data).withTraceId(AiTraceSupport.currentTraceId());
    }
}
