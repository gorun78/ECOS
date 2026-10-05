package com.chinacreator.gzcm.ai.wagent;

import com.chinacreator.gzcm.ai.wagent.WAgentEnums.ReadinessGrade;
import com.chinacreator.gzcm.ai.wagent.WAgentEnums.RunStatus;
import com.chinacreator.gzcm.ai.wagent.readiness.ReadinessGrader;
import com.chinacreator.gzcm.common.exception.BusinessException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分册10 F10 · W Agent 编排协调门面（单 Facade，R-59① 仅协调、不做金额运算）。
 *
 * <p>本类持有 S4~S15 各 service 尚未由并发 Agent 落盘的<b>在内存态</b>
 * （goal/question/run/claim/evidence/decision/action），供 9 个 slim Controller
 * 委托取结构。所有方法只组装 {@link LinkedHashMap}（确定性、locale-safe），
 * 金额/比率/概率一律 {@link BigDecimal}，JSON 语义字段以 String 落 body。
 * 无 double/float，无 JdbcTemplate，无 ScheduledExecutor，无 LLM/Git 直连。
 * 未来并发 Agent 落盘真实 service 后，本门面方法体改为透传即可，Controller 不变。</p>
 */
public final class WAgentOrchestratorFacade {

    // ---- 在内存态 store（在内存协调态；并发 Agent 落库后本层退化为透传）----
    private final Map<String, GoalState> goals = new LinkedHashMap<>();
    private final Map<String, QuestionState> questions = new LinkedHashMap<>();
    private final Map<String, RunState> runs = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> claimsByRun = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> evidenceById = new LinkedHashMap<>();
    private final Map<String, ActionState> actions = new LinkedHashMap<>();
    private final AtomicLong runSeq = new AtomicLong(0);

    // ---- 内部态记录 ----
    private record GoalState(boolean referenced, String goalType, String revisedAt) {}
    private record QuestionState(String intent, BigDecimal confidence, String slotsSourceJson, String status) {}
    private record RunState(String runId, RunStatus status, List<String> timeline, String latestRun) {}
    private record ActionState(String id, String optionId, boolean published) {}

    // ════════════════════════════ #1/#2 Goal ════════════════════════════

    /** #1 createGoal → 201（integer-only locale-safe 载荷）。 */
    public Map<String, Object> createGoal(String goalId, String goalType, String description) {
        String id = (goalId == null || goalId.isBlank()) ? "goal-" + System.nanoTime() : goalId;
        goals.put(id, new GoalState(false, goalType, null));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("goalId", id);
        out.put("goalType", goalType);
        out.put("description", description);
        out.put("version", 1); // integer-only，禁 double
        return out;
    }

    /** 供 Controller 反查：goal 是否已被引用（被引用即锁，revise 前必读）。 */
    public boolean goalReferenced(String goalId) {
        GoalState g = goals.get(goalId);
        return g != null && g.referenced();
    }

    /** 由外部 service 记录"被引用"标志（Controller 不直接改，由编排链路写）。 */
    public void markGoalReferenced(String goalId) {
        GoalState g = goals.get(goalId);
        if (g != null) goals.put(goalId, new GoalState(true, g.goalType(), g.revisedAt()));
    }

    /** #2 reviseGoal → 200（幂等重编号 version+1）。 */
    public Map<String, Object> reviseGoal(String goalId, String newGoalType) {
        GoalState g = goals.get(goalId);
        int version = (g == null) ? 1 : 2;
        goals.put(goalId, new GoalState(false, newGoalType, Instant.now().toString()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("goalId", goalId);
        out.put("goalType", newGoalType);
        out.put("revisedAt", Instant.now().toString());
        out.put("version", version);
        return out;
    }

    // ════════════════════════════ #3/#4 Question ════════════════════════════

    /** #3 createQuestion → 201。confidence &lt; 0.70 保守不 fail-open 建 Run（F10-05）。 */
    public Map<String, Object> createQuestion(String questionId, String intent,
                                              BigDecimal confidence, String slotsSourceJson) {
        String id = (questionId == null || questionId.isBlank()) ? "q-" + System.nanoTime() : questionId;
        boolean lowConf = (confidence == null) || confidence.compareTo(new BigDecimal("0.70")) < 0;
        QuestionState q = new QuestionState(intent, confidence == null ? BigDecimal.ZERO : confidence,
                slotsSourceJson, lowConf ? "CLARIFY" : "READY");
        questions.put(id, q);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("questionId", id);
        out.put("intent", intent);
        out.put("confidence", q.confidence());          // BigDecimal → NUMERIC(5,4) 语义，禁 double
        out.put("slots_source", q.slotsSourceJson());    // JSON 语义：String 落 body
        out.put("status", q.status());                    // CLARIFY / READY（低置信保守）
        return out;
    }

    /** #4 getQuestion → 200（含 latest_run 摘要字符串）。 */
    public Map<String, Object> getQuestion(String questionId) {
        QuestionState q = questions.get(questionId);
        if (q == null) throw new BusinessException(404, "E_NOT_FOUND question " + questionId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("questionId", questionId);
        out.put("intent", q.intent());
        out.put("confidence", q.confidence());
        out.put("slots_source", q.slotsSourceJson());
        out.put("status", q.status());
        out.put("latest_run", latestRunSummaryFor(questionId));
        return out;
    }

    // ════════════════════════════ #5~#10 Run ════════════════════════════

    /** #5 startRun → 201 同步 / 202 异步（Prefer: respond-async 由 Controller 定状态）。 */
    public Map<String, Object> startRun(String idempotencyKey, String questionId, boolean async) {
        String runId = "run-" + (runSeq.incrementAndGet());
        runs.put(runId, new RunState(runId, RunStatus.PLANNING, timeline("run started"), questionId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("status", RunStatus.PLANNING.name());
        out.put("async", async);
        out.put("idempotencyKey", idempotencyKey);
        Map<String, Object> budget = budgetRemaining(runId);
        out.put("budget", budget);
        return out;
    }

    /** #6 getRun → 200（status + timeline 事件字符串数组 + 预算剩余）。 */
    public Map<String, Object> getRun(String runId) {
        RunState r = runs.get(runId);
        if (r == null) throw new BusinessException(404, "E_NOT_FOUND run " + runId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("status", r.status().name());
        out.put("timeline", new ArrayList<String>(r.timeline()));
        out.put("latest_run", r.latestRun());
        out.put("budget", budgetRemaining(runId));
        return out;
    }

    /** #8 submitRunInput → 200（awaiting_input 应答，回写 timeline）。 */
    public Map<String, Object> submitRunInput(String runId, Map<String, Object> input) {
        RunState r = requireRun(runId);
        List<String> tl = new ArrayList<>(r.timeline());
        tl.add("input submitted: " + (input == null ? 0 : input.size()) + " fields");
        runs.put(runId, new RunState(runId, r.status(), tl, r.latestRun()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("status", r.status().name());
        out.put("accepted", true);
        return out;
    }

    /** #9 approveRunGate → 200（人工闸放行，记 timeline；token 由 Controller 校验非空）。 */
    public Map<String, Object> approveRunGate(String runId, String approvalToken) {
        RunState r = requireRun(runId);
        List<String> tl = new ArrayList<>(r.timeline());
        tl.add("gate approved (token=" + (approvalToken == null ? 0 : approvalToken.length()) + ")");
        runs.put(runId, new RunState(runId, RunStatus.PLAN_VALIDATED, tl, r.latestRun()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("status", RunStatus.PLAN_VALIDATED.name());
        out.put("approved", true);
        return out;
    }

    /** #10 cancelRun → 200 幂等。 */
    public Map<String, Object> cancelRun(String runId) {
        RunState r = requireRun(runId);
        if (RunStatus.isTerminal(r.status())) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("runId", runId);
            out.put("status", r.status().name());
            out.put("cancelled", false);   // 已终态：幂等返回原状
            out.put("idempotent", true);
            return out;
        }
        List<String> tl = new ArrayList<>(r.timeline());
        tl.add("cancel requested");
        runs.put(runId, new RunState(runId, RunStatus.CANCELLED, tl, r.latestRun()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("status", RunStatus.CANCELLED.name());
        out.put("cancelled", true);
        out.put("idempotent", true);
        return out;
    }

    // ════════════════════════════ #12/#13 Readiness ════════════════════════════

    /** #12 getReadiness → 200（grade char + 各层 + 缺项 + 降级 DegradeCode 列表）。 */
    public Map<String, Object> getReadiness(String questionId) {
        // 占位：无探测数据时保守记 C（ReadinessGrader 空集语义），无 BLOCKED。
        ReadinessGrader.Assessment a = new ReadinessGrader.Assessment(
                new ReadinessGrader.ItemD(List.of()),
                new ReadinessGrader.ItemI(List.of()),
                new ReadinessGrader.ItemK(List.of()),
                new ReadinessGrader.ItemC(List.of()));
        ReadinessGrade g = ReadinessGrader.grade(a);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("questionId", questionId);
        out.put("grade", String.valueOf(g));
        Map<String, String> layers = new LinkedHashMap<>();
        layers.put("d", "UNKNOWN");
        layers.put("i", "UNKNOWN");
        layers.put("k", "UNKNOWN");
        layers.put("c", "UNKNOWN");
        out.put("layers", layers);
        out.put("gaps", new ArrayList<String>());
        out.put("degradations", new ArrayList<String>());
        return out;
    }

    // ════════════════════════════ #21~#23 Decision / Evidence ════════════════════════════

    /** #21 listClaims → 200（claims + numeric_value BigDecimal + evidence_grade）。 */
    public Map<String, Object> listClaims(String runId) {
        List<Map<String, Object>> cs = claimsByRun.getOrDefault(runId, new ArrayList<>());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("claims", new ArrayList<>(cs));
        out.put("count", cs.size());
        return out;
    }

    /** #22 traceEvidence → 200（引擎端追溯 endpoint_map：engine→path，JSON string 语义）。 */
    public Map<String, Object> traceEvidence(String evidenceId) {
        Map<String, Object> e = evidenceById.get(evidenceId);
        if (e == null) throw new BusinessException(404, "E_NOT_FOUND evidence " + evidenceId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("evidenceId", evidenceId);
        out.put("endpoint_map", e.get("endpointMapJson")); // String（engine→path 的 JSON 串）
        out.put("capturedAt", e.get("capturedAt"));
        return out;
    }

    /** #23 recordDecision → 201（decisionId 生成回传）。 */
    public Map<String, Object> recordDecision(String runId, String decisionText, String rationale) {
        String decisionId = "dec-" + System.nanoTime();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("decisionId", decisionId);
        out.put("runId", runId);
        out.put("decision", decisionText);
        out.put("rationale", rationale);
        out.put("recordedAt", Instant.now().toString());
        return out;
    }

    /** #22 内部：登记一条 evidence（endpointMapJson = engine→path 的 JSON 字符串）。 */
    public void trackEvidence(String evidenceId, String engineRefPath, String engine, String capturedAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("engineRefPath", engineRefPath);
        row.put("engine", engine);
        row.put("capturedAt", capturedAt);
        // endpoint_map：engine→path 映射，以 JSON 字符串落 body（保持 string 语义，不展开 double）。
        row.put("endpointMapJson", "{\"" + engine + "\":\"" + engineRefPath + "\"}");
        evidenceById.put(evidenceId, row);
    }

    // ════════════════════════════ #24 Action ════════════════════════════

    /** #24a draftAction → 201（5 必填缺一由 Controller 400；此处组装 draft）。 */
    public Map<String, Object> draftAction(String optionId, String title, String ownerId,
                                           String role, String dueDate, String kpiText, String sourceType) {
        String id = "act-" + System.nanoTime();
        actions.put(id, new ActionState(id, optionId, false));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("actionId", id);
        out.put("optionId", optionId);
        out.put("title", title);
        out.put("ownerId", ownerId);
        out.put("role", role);
        out.put("dueDate", dueDate);          // String 语义（日期不序列化 double）
        out.put("kpiText", kpiText);
        out.put("sourceType", sourceType);
        out.put("status", "DRAFT");
        return out;
    }

    /** #24b commitAction → 200（已 published 由 Controller 判 409）。 */
    public Map<String, Object> commitAction(String id, String approvalToken) {
        ActionState a = actions.get(id);
        if (a == null) throw new BusinessException(404, "E_NOT_FOUND action " + id);
        actions.put(id, new ActionState(id, a.optionId(), true));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("actionId", id);
        out.put("status", "PUBLISHED");
        out.put("committed", true);
        return out;
    }

    /** 供 Controller 反查 action 是否已 published（#24b 409 前置）。 */
    public boolean actionPublished(String id) {
        ActionState a = actions.get(id);
        return a != null && a.published();
    }

    // ─────────────────────────── helpers ───────────────────────────

    private RunState requireRun(String runId) {
        RunState r = runs.get(runId);
        if (r == null) throw new BusinessException(404, "E_NOT_FOUND run " + runId);
        return r;
    }

    private String latestRunSummaryFor(String questionId) {
        for (RunState r : runs.values()) {
            if (questionId != null && questionId.equals(r.latestRun())) {
                return r.status().name() + " @ " + r.runId();
            }
        }
        return "none";
    }

    /** 预算剩余（RunBudget defaults；余量 = max - used，整数/BigDecimal，禁 double）。 */
    private Map<String, Object> budgetRemaining(String runId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stepRemaining", 60);          // integer-only
        m.put("llmRemaining", 40);           // integer-only
        m.put("costRemaining", BigDecimal.valueOf(100).toPlainString()); // 金额 String 语义 NUMERIC(18,2)
        m.put("withinLimit", true);
        return m;
    }

    private static List<String> timeline(String first) {
        List<String> tl = new ArrayList<>();
        tl.add(first);
        return tl;
    }
}
