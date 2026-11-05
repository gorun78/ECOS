package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentRunDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentRunDO;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.IntentClassifyPort;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.SlotFillingService;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentQuestionService;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
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

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

    /** 问题基视图（7 键；旧 Map 逐键同名同序）。 */
    public record QuestionVO(@JsonProperty("question_id") String questionId,
                             @JsonProperty("mission_id") String missionId,
                             @JsonProperty("intent") String intent,
                             @JsonProperty("intent_confidence") BigDecimal intentConfidence,
                             @JsonProperty("status") String status,
                             @JsonProperty("latest_run_id") String latestRunId,
                             @JsonProperty("answer_summary_text") String answerSummaryText) {

        static QuestionVO of(WAgentQuestionDO q) {
            return new QuestionVO(q.getId(), q.getMissionId(), q.getIntent(), q.getIntentConfidence(),
                    q.getStatus(), q.getLatestRunId(), q.getAnswerSummaryText());
        }
    }

    /** latest_run 摘要（4 键；旧 Map 逐键同名同序）。 */
    public record RunSummaryVO(@JsonProperty("run_id") String runId,
                               @JsonProperty("status") String status,
                               @JsonProperty("plan_source") String planSource,
                               @JsonProperty("automation_ceiling") Integer automationCeiling) {

        static RunSummaryVO of(WAgentRunDO run) {
            return new RunSummaryVO(run.getId(), run.getStatus(), run.getPlanSource(),
                    run.getAutomationCeiling());
        }
    }

    /** #3 建问题响应（9 键；CLARIFY 时再追加 3 键）。 */
    public record QuestionCreateVO(@JsonProperty("question_id") String questionId,
                                   @JsonProperty("mission_id") String missionId,
                                   @JsonProperty("intent") String intent,
                                   @JsonProperty("intent_confidence") BigDecimal intentConfidence,
                                   @JsonProperty("status") String status,
                                   @JsonProperty("latest_run_id") String latestRunId,
                                   @JsonProperty("answer_summary_text") String answerSummaryText,
                                   @JsonProperty("outcome") String outcome,
                                   @JsonProperty("requires_clarify") boolean requiresClarify,
                                   @JsonInclude(JsonInclude.Include.NON_NULL)
                                   @JsonProperty("clarification_items") List<String> clarificationItems,
                                   @JsonInclude(JsonInclude.Include.NON_NULL)
                                   @JsonProperty("classifier_available") Boolean classifierAvailable,
                                   @JsonInclude(JsonInclude.Include.NON_NULL)
                                   @JsonProperty("confidence_threshold") String confidenceThreshold) {

        static QuestionCreateVO of(QuestionVO q, String outcome, boolean requiresClarify,
                                   List<String> clarificationItems, Boolean classifierAvailable,
                                   String confidenceThreshold) {
            return new QuestionCreateVO(q.questionId(), q.missionId(), q.intent(), q.intentConfidence(),
                    q.status(), q.latestRunId(), q.answerSummaryText(), outcome, requiresClarify,
                    clarificationItems, classifierAvailable, confidenceThreshold);
        }
    }

    /**
     * #4 详情响应（7 键；{@code latest_run} 仅当问题带 latest_run_id 时出现）。
     *
     * <p>旧实现用 {@code put(key, null)} 表达「有 latest_run_id 但行缺失」——该键仍出现且值为
     * null。为逐字保真：存在位靠 {@code nodes} 方法返回 {@link NullNode}（序列化为 null），
     * 不存在时返回 Java null 并由属性级 NON_NULL 省略。</p>
     */
    public record QuestionDetailVO(@JsonProperty("question_id") String questionId,
                                   @JsonProperty("mission_id") String missionId,
                                   @JsonProperty("intent") String intent,
                                   @JsonProperty("intent_confidence") BigDecimal intentConfidence,
                                   @JsonProperty("status") String status,
                                   @JsonProperty("latest_run_id") String latestRunId,
                                   @JsonProperty("answer_summary_text") String answerSummaryText,
                                   @JsonIgnore boolean latestRunPresent,
                                   @JsonIgnore RunSummaryVO latestRun) {

        @JsonProperty("latest_run")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public JsonNode latestRunNode() {
            if (!latestRunPresent) {
                return null;
            }
            return latestRun == null ? NullNode.getInstance() : MAPPER.valueToTree(latestRun);
        }
    }

    @PostMapping(WAgentApiPaths.QUESTIONS_COLLECTION)
    public ResponseEntity<ApiResponse<QuestionCreateVO>> createQuestion(
            @RequestBody QuestionCreateRequest req) {
        Identity identity = identityResolver.require();
        WAgentQuestionService.QuestionOutcome outcome = questionService.fromUtterance(
                new WAgentQuestionService.QuestionCreateCommand(identity.tenantId(),
                        req.orgId() == null ? identity.orgId() : req.orgId(), identity.operator(),
                        req.missionId(), req.rawText(), req.slots(), req.dependsOn()));
        QuestionVO base = QuestionVO.of(outcome.question());
        String outcomeName = outcome.requiresClarify() ? OUTCOME_CLARIFY : OUTCOME_READY;
        if (!outcome.requiresClarify()) {
            return ResponseEntity.status(201).body(WAgentRunController.ok(
                    QuestionCreateVO.of(base, outcomeName, false, null, null, null)));
        }
        // 澄清项确定性生成：意图未立 ⇒ intent 必列；一轮 ≤3（F10-06 明文裁剪）
        List<String> items = slotFillingService.nextClarificationItems(
                outcome.question().getIntent() == null ? List.of("intent", "time_range", "scope")
                        : List.of("intent"));
        IntentClassifyPort.Verdict verdict = outcome.verdict();
        return ResponseEntity.status(201).body(WAgentRunController.ok(
                QuestionCreateVO.of(base, outcomeName, true, items,
                        verdict == null ? Boolean.FALSE : verdict.classifierAvailable(),
                        IntentClassifyPort.CONFIDENCE_MIN.toPlainString())));
    }

    /** #4 读取（含 latest_run 摘要；跨租户掩 404，§5.3 防枚举）。 */
    @GetMapping(WAgentApiPaths.QUESTION_BY_ID)
    public ApiResponse<QuestionDetailVO> getQuestion(
            @PathVariable(WAgentApiPaths.P_QUESTION_ID) String questionId) {
        Identity identity = identityResolver.require();
        WAgentQuestionDO question = questionService.get(questionId, identity.tenantId());
        QuestionVO q = QuestionVO.of(question);
        boolean present = question.getLatestRunId() != null && !question.getLatestRunId().isBlank();
        RunSummaryVO summary = present
                ? runDao.findById(question.getLatestRunId()).map(RunSummaryVO::of).orElse(null)
                : null;
        return WAgentRunController.ok(new QuestionDetailVO(q.questionId(), q.missionId(), q.intent(),
                q.intentConfidence(), q.status(), q.latestRunId(), q.answerSummaryText(),
                present, summary));
    }
}
