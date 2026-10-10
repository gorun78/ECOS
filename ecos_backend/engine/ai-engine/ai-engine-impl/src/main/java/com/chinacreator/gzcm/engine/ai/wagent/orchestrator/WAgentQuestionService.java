package com.chinacreator.gzcm.engine.ai.wagent.orchestrator;

import com.chinacreator.gzcm.engine.ai.wagent.WAgentEnums.GoalIntent;
import com.chinacreator.gzcm.engine.ai.wagent.dao.WAgentQuestionDao;
import com.chinacreator.gzcm.engine.ai.wagent.dataobject.WAgentQuestionDO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 业务问题服务（详细设计-10 F10-05/F10-06/F10-11 上游，REQ-WAG-05/06）。
 *
 * <p>{@link #fromUtterance}：原文 → {@link IntentClassifyPort} 分类 → Question 落
 * V229（{@code intent}/{@code intent_confidence} 与 Goal.goal_type 共用
 * {@link GoalIntent} 单源词表）。置信 &lt; 0.70 或分类器不可用 ⇒ 状态
 * {@code clarifying}（<b>不建 Run</b>），并计入澄清配额（{@link SlotFillingService}）。</p>
 *
 * <p><b>depends_on 环路</b>（F10-05 边界）：应用层拓扑检测（DFS），禁依赖 DB 触发器；
 * 检出环即 {@code E-VALIDATION} 拒绝（附件"触发器双校"改应用层 + DDL CHECK 的落地）。</p>
 *
 * <p>V229 {@code mission_id NOT NULL}：Question 必挂 Mission（Mission 建立属对象链
 * T2 步骤，由调用方在请求中携带既有 missionId）。</p>
 */
@Service
public class WAgentQuestionService {

    private static final Logger log = LoggerFactory.getLogger(WAgentQuestionService.class);

    private final WAgentQuestionDao questionDao;
    private final IntentClassifyPort intentClassifier;
    private final SlotFillingService slotFillingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WAgentQuestionService(WAgentQuestionDao questionDao,
                                 IntentClassifyPort intentClassifier,
                                 SlotFillingService slotFillingService) {
        this.questionDao = questionDao;
        this.intentClassifier = intentClassifier;
        this.slotFillingService = slotFillingService;
    }

    /** 创建命令（slots 为槽位名→值任意 JSON 标量；dependsOn=前序问题 id 列表）。 */
    public record QuestionCreateCommand(String tenantId, String orgId, String operator,
                                        String missionId, String rawText,
                                        Map<String, Object> slots, List<String> dependsOn) {
    }

    /** 创建结果：Question 行 + 分类判定（clarify 时上游据此回澄清卡，不建 Run）。 */
    public record QuestionOutcome(WAgentQuestionDO question, IntentClassifyPort.Verdict verdict,
                                  boolean requiresClarify) {
    }

    /**
     * F10-05 主干：原文 → 分类 → 环路检测 → 落库。置信不足/不可用 ⇒ {@code clarifying}。
     */
    public QuestionOutcome fromUtterance(QuestionCreateCommand cmd) {
        if (cmd == null || cmd.rawText() == null || cmd.rawText().isBlank()) {
            throw WAgentOrchestrationException.validation("raw_text 不得为空");
        }
        if (cmd.missionId() == null || cmd.missionId().isBlank()) {
            throw WAgentOrchestrationException.validation("mission_id 不得为空（V229 NOT NULL，Question 必挂 Mission）");
        }
        if (cmd.tenantId() == null || cmd.tenantId().isBlank()) {
            throw new WAgentOrchestrationException(
                    com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode.E_AUTH,
                    "tenant_id 缺失（网关未注入租户上下文即拒绝）", null);
        }
        List<String> dependsOn = cmd.dependsOn() == null ? List.of() : List.copyOf(cmd.dependsOn());
        assertDependsOnAcyclic(dependsOn);

        IntentClassifyPort.Verdict verdict = intentClassifier.classify(cmd.rawText());
        boolean ready = verdict.readyForRun();

        WAgentQuestionDO question = new WAgentQuestionDO();
        question.setId(UUID.randomUUID().toString());
        question.setMissionId(cmd.missionId());
        question.setRawText(cmd.rawText());
        question.setIntent(ready ? verdict.intent() : null);
        question.setIntentConfidence(verdict.confidence() == null ? BigDecimal.ZERO : verdict.confidence());
        question.setSlotsJson(writeJson(cmd.slots()));
        question.setDependsOnJson(writeJson(dependsOn));
        question.setStatus(ready
                ? WAgentStatus.QuestionStatus.OPEN.value()
                : WAgentStatus.QuestionStatus.CLARIFYING.value());
        question.setTenantId(cmd.tenantId());
        question.setOrgId(cmd.orgId());
        question.setVersionNo("1");
        question.setCreateBy(cmd.operator());
        question.setUpdateBy(cmd.operator());
        questionDao.insert(question);

        if (!ready) {
            // 计入澄清配额（F10-06/§4.4：连续两轮无新增信息 → awaiting_input 挂起）
            slotFillingService.recordClarifyRound(question, keySetOf(cmd.slots()));
        }
        log.info("[F10-05] Question 建立: id={} status={} intent={} confidence={}",
                question.getId(), question.getStatus(), question.getIntent(), question.getIntentConfidence());
        return new QuestionOutcome(question, verdict, !ready);
    }

    /** 读取（跨租户/不可见同掩 E-NOT-FOUND，§5.3 防枚举）。 */
    public WAgentQuestionDO get(String questionId, String tenantId) {
        WAgentQuestionDO question = questionDao.findById(questionId)
                .orElseThrow(WAgentOrchestrationException::notFound);
        if (tenantId != null && !tenantId.equals(question.getTenantId())) {
            throw WAgentOrchestrationException.notFound();
        }
        return question;
    }

    /**
     * 建 Run 前置闸（F10-05：{@code intent_confidence < 0.70} 不得建 Run）——
     * 由 {@code WAgentRunService.createRun} 消费；clarify 态同样拒绝（禁绕过澄清环建 Run）。
     */
    public void assertRunEligible(WAgentQuestionDO question) {
        BigDecimal confidence = question.getIntentConfidence();
        boolean eligible = WAgentStatus.QuestionStatus.OPEN.value().equals(question.getStatus())
                && GoalIntent.isValid(question.getIntent())
                && confidence != null && confidence.compareTo(IntentClassifyPort.CONFIDENCE_MIN) >= 0;
        if (!eligible) {
            Map<String, Object> extras = new LinkedHashMap<>();
            extras.put("questionId", question.getId());
            extras.put("intentConfidence", String.valueOf(confidence));
            extras.put("threshold", IntentClassifyPort.CONFIDENCE_MIN.toPlainString());
            throw new WAgentOrchestrationException(
                    com.chinacreator.gzcm.engine.ai.wagent.WAgentErrorCodes.WAgentErrorCode.E_VALIDATION,
                    "置信不足或澄清未决，禁建 Run", extras);
        }
    }

    /** Run 建立后回写（question→running + latest_run 摘要位）。 */
    public void markRunning(String questionId, String runId) {
        questionDao.findById(questionId).ifPresent(question -> {
            question.setStatus(WAgentStatus.QuestionStatus.RUNNING.value());
            question.setLatestRunId(runId);
            questionDao.update(question);
        });
    }

    public void markAnswered(String questionId, String answerSummaryText) {
        questionDao.findById(questionId).ifPresent(question -> {
            question.setStatus(WAgentStatus.QuestionStatus.ANSWERED.value());
            question.setAnswerSummaryText(answerSummaryText);
            questionDao.update(question);
        });
    }

    /**
     * F10-05 边界：depends_on 环路应用层拓扑检测。从每个依赖节点 DFS 展开既有问题的
     * depends_on 边；引用不存在的问题 ⇒ E-NOT-FOUND；图上任一环 ⇒ E-VALIDATION 拒绝。
     */
    void assertDependsOnAcyclic(List<String> dependsOn) {
        Set<String> visiting = new HashSet<>();
        Set<String> done = new HashSet<>();
        Deque<String> stackTrace = new ArrayDeque<>();
        for (String dep : dependsOn) {
            dfsDetectCycle(dep, visiting, done, stackTrace);
        }
    }

    private void dfsDetectCycle(String id, Set<String> visiting, Set<String> done, Deque<String> stack) {
        if (done.contains(id)) {
            return;
        }
        if (visiting.contains(id)) {
            List<String> cycle = new ArrayList<>(stack);
            cycle.add(id);
            throw WAgentOrchestrationException.validation(
                    "depends_on 存在环路，应用层拓扑检测拒绝: " + String.join(" -> ", cycle));
        }
        WAgentQuestionDO question = questionDao.findById(id)
                .orElseThrow(WAgentOrchestrationException::notFound);
        visiting.add(id);
        stack.addLast(id);
        for (String next : readStringList(question.getDependsOnJson())) {
            dfsDetectCycle(next, visiting, done, stack);
        }
        stack.removeLast();
        visiting.remove(id);
        done.add(id);
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw WAgentOrchestrationException.validation("depends_on_json 不可解析（禁宽容当空）");
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw WAgentOrchestrationException.validation("问题 JSON 载荷序列化失败");
        }
    }

    private static Set<String> keySetOf(Map<String, Object> slots) {
        return slots == null ? Set.of() : Set.copyOf(slots.keySet());
    }
}
