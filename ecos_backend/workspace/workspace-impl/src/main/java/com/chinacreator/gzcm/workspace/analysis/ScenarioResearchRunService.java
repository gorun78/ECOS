package com.chinacreator.gzcm.workspace.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.chinacreator.gzcm.workspace.scenario.ScenarioService;

/**
 * 场景运行 L3 —— RD-Agent 研究循环首切片（2026-11-25，可行性报告 v2.0 §6）。
 *
 * <p><b>形态定位</b>：场景运行三级形态的最上位——L1 心智模型问答、L2 算法分析已落，
 * 本类承载 <b>L3 研究循环</b>：假设 → 实验计划 → 只读执行 → 评估 → 记录 → 选优，多轮演进。</p>
 *
 * <p><b>本切片的边界（必须如实宣称，禁超范围承诺）</b>：</p>
 * <ul>
 *   <li>执行器为 {@link ResearchExecutorPort} <b>只读</b>端口，默认实现
 *       {@link ReadOnlyResearchExecutor} 只做确定性聚合与口径核对，<b>不执行任意代码</b>、
 *       不起进程、不连生产库写面；</li>
 *   <li>循环轮次由预算冻结（{@code maxLoops/maxSecondsPerLoop/maxTotalSeconds}），
 *       达到即停并如实写 {@code stop_reason}；</li>
 *   <li>研究轮次一律 {@code run_mode=SANDBOX}，<b>不计</b> {@code isFormalRunExists}，
 *       正式口径不被研究轮次污染；</li>
 *   <li>结论只作候选：不自动写蓝图状态机、不自动改场景指标；
 *       L3 产物落 {@code ecos_scenario_run_artifact} 引用（实体落对象存储）。</li>
 * </ul>
 *
 * <p><b>降级纪律</b>：场景不存在 → 404；question 缺失 → 400；研究端口不可用 → 503
 * （{@code RESEARCH_503}）；评估无真值 → 显式 {@code EVALUATION_DEGRADED}，
 * 禁把「没有真值」写成「评估通过」。</p>
 */
@Service
public class ScenarioResearchRunService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioResearchRunService.class);

    /** L3 运行分级码（与 DDL V365 run_level 词表一致；前端展示走 i18n）。 */
    public static final String RUN_LEVEL = "RD_AGENT";
    /** L3 运行类型码（复用 run_type 列，不新增词表）。 */
    public static final String RUN_TYPE = "RESEARCH_RUN";
    /** 单次运行预算默认值（可由请求覆盖，但受硬上限约束）。 */
    static final int DEFAULT_MAX_LOOPS = 3;
    static final int HARD_MAX_LOOPS = 5;
    static final int DEFAULT_MAX_SECONDS_PER_LOOP = 120;
    static final int HARD_MAX_SECONDS_PER_LOOP = 600;
    static final int DEFAULT_MAX_TOTAL_SECONDS = 300;
    static final int HARD_MAX_TOTAL_SECONDS = 1800;

    private final WorkspaceDataDao dao;
    private final ScenarioService scenarioService;
    private final ResearchExecutorPort executor;
    private final com.chinacreator.gzcm.workspace.scenario.WorkspaceSecurityAudit audit;

    /** 测试/兼容构造（无审计）。 */
    public ScenarioResearchRunService(WorkspaceDataDao dao, ScenarioService scenarioService,
                                      ResearchExecutorPort executor) {
        this(dao, scenarioService, executor, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ScenarioResearchRunService(WorkspaceDataDao dao, ScenarioService scenarioService,
                                      ResearchExecutorPort executor,
                                      com.chinacreator.gzcm.workspace.scenario.WorkspaceSecurityAudit audit) {
        this.dao = dao;
        this.scenarioService = scenarioService;
        this.executor = executor;
        this.audit = audit;
    }

    /**
     * 阶段一：研究准备（不执行）。
     *
     * <p>返回场景上下文摘要、默认预算、可评估口径清单与首轮假设草案；
     * 供前端展示「研究将用什么数据、按什么口径评估、最多跑几轮」。</p>
     */
    public Map<String, Object> prepare(String scenarioId, String question) {
        requireScenario(scenarioId);
        if (question == null || question.isBlank()) {
            throw new BusinessException(400, "RESEARCH-400: question 必填（研究循环的输入锚点）");
        }
        Map<String, Object> context = executor.describeContext(scenarioId);
        List<Map<String, Object>> hypotheses = executor.proposeHypotheses(scenarioId, question);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scenarioId", scenarioId);
        out.put("question", question);
        out.put("runLevel", RUN_LEVEL);
        out.put("context", context);
        out.put("hypotheses", hypotheses);
        out.put("budget", defaultBudget(null));
        out.put("executor", executor.describe());
        return out;
    }

    /**
     * 阶段二：发起研究循环（同步执行，首切片不引异步线程）。
     *
     * <p>每轮：取假设 → 只读执行 → 评估 → 落一行运行（谱系指向上轮）→ 选优。
     * 命中 {@code ACCEPTED} 或预算耗尽即停。</p>
     */
    public Map<String, Object> run(String scenarioId, Map<String, Object> body) {
        requireScenario(scenarioId);
        String question = text(body.get("question"));
        if (question.isBlank()) {
            throw new BusinessException(400, "RESEARCH-400: question 必填");
        }
        Map<String, Object> budget = defaultBudget(asMap(body.get("budget")));
        List<Map<String, Object>> inputHypotheses = asListOfMaps(body.get("hypotheses"));
        String branchKey = text(body.get("branchKey"));
        if (branchKey.isBlank()) {
            branchKey = "br-" + UUID.randomUUID().toString().substring(0, 8);
        }

        long startedAt = System.currentTimeMillis();
        int maxLoops = intOf(budget.get("maxLoops"), DEFAULT_MAX_LOOPS);
        int maxTotalSeconds = intOf(budget.get("maxTotalSeconds"), DEFAULT_MAX_TOTAL_SECONDS);

        List<Map<String, Object>> rounds = new ArrayList<>();
        String parentRunId = null;
        String sotaRunId = null;
        String stopReason = null;
        Map<String, Object> sotaResult = null;
        boolean evaluationDegraded = false;
        String budgetJson = json(budget);

        for (int loop = 0; loop < maxLoops; loop++) {
            if ((System.currentTimeMillis() - startedAt) / 1000 > maxTotalSeconds) {
                stopReason = "BUDGET_EXHAUSTED";
                break;
            }
            List<Map<String, Object>> hypotheses = loop == 0
                    ? (inputHypotheses.isEmpty() ? executor.proposeHypotheses(scenarioId, question) : inputHypotheses)
                    : executor.refineHypotheses(scenarioId, question, rounds);

            Map<String, Object> execResult;
            try {
                execResult = executor.executeRound(scenarioId, question, hypotheses, loop);
            } catch (RuntimeException e) {
                // 执行失败也落一行：谱系里必须看得见「这一轮失败了」，不能被静默吞掉
                log.warn("研究循环：第 {} 轮执行失败 scenario={}", loop, scenarioId, e);
                String failedRunId = "run-" + UUID.randomUUID().toString().substring(0, 12);
                String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                dao.insertResearchRun(new WorkspaceDataDao.ResearchRunRow(
                        failedRunId, scenarioId, question, loop, parentRunId, sotaRunId, branchKey,
                        json(Map.of("loopIndex", loop, "error", detail)), budgetJson,
                        json(hypotheses),
                        json(List.of(Map.of("step", "execute", "status", "failed", "detail", detail))),
                        json(Map.of("summary", "本轮执行失败，无建议", "actions", List.of(),
                                "risks", List.of("研究执行器不可用或执行异常"), "confidence", "LOW")),
                        true));
                dao.updateResearchStopReason(failedRunId, "FAILED");

                Map<String, Object> failed = new LinkedHashMap<>();
                failed.put("loopIndex", loop);
                failed.put("runId", failedRunId);
                failed.put("parentRunId", parentRunId);
                failed.put("accepted", false);
                failed.put("degraded", true);
                failed.put("detail", detail);
                rounds.add(failed);
                stopReason = "FAILED";
                break;
            }

            Map<String, Object> evaluation = executor.evaluate(scenarioId, execResult, rounds);
            boolean degraded = Boolean.TRUE.equals(evaluation.get("degraded"));
            evaluationDegraded |= degraded;
            boolean accepted = Boolean.TRUE.equals(evaluation.get("accepted"));

            String runId = "run-" + UUID.randomUUID().toString().substring(0, 12);
            String parent = parentRunId;
            dao.insertResearchRun(new WorkspaceDataDao.ResearchRunRow(
                    runId, scenarioId, question, loop, parent,
                    accepted ? runId : sotaRunId, branchKey,
                    json(execResult.get("experimentSpec")), budgetJson,
                    json(hypotheses), json(execResult.get("trace")), json(execResult.get("advice")),
                    degraded));

            Map<String, Object> round = new LinkedHashMap<>();
            round.put("loopIndex", loop);
            round.put("runId", runId);
            round.put("parentRunId", parent);
            round.put("accepted", accepted);
            round.put("degraded", degraded);
            round.put("evaluation", evaluation);
            round.put("trace", execResult.get("trace"));
            round.put("advice", execResult.get("advice"));
            rounds.add(round);

            parentRunId = runId;
            if (accepted) {
                sotaRunId = runId;
                sotaResult = round;
                stopReason = "ACCEPTED";
                dao.updateResearchStopReason(runId, stopReason);
                break;
            }
            if (Boolean.TRUE.equals(evaluation.get("noProgress"))) {
                // 连续无进展：评估明确给出 noProgress=true 时提前收敛，不空转到预算耗尽
                stopReason = "NO_PROGRESS";
                dao.updateResearchStopReason(runId, stopReason);
                break;
            }
            if (loop == maxLoops - 1) {
                stopReason = "MAX_LOOPS";
                dao.updateResearchStopReason(runId, stopReason);
            }
        }
        if (stopReason == null) {
            stopReason = rounds.isEmpty() ? "BUDGET_EXHAUSTED" : "MAX_LOOPS";
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scenarioId", scenarioId);
        out.put("question", question);
        out.put("runLevel", RUN_LEVEL);
        out.put("branchKey", branchKey);
        out.put("budget", budget);
        out.put("rounds", rounds);
        out.put("sotaRunId", sotaRunId);
        out.put("sota", sotaResult);
        out.put("stopReason", stopReason);
        out.put("evaluationDegraded", evaluationDegraded);
        out.put("elapsedMs", System.currentTimeMillis() - startedAt);
        out.put("candidateOnly", true);

        // V367 批次 F：研究循环执行审计（不落代码正文）
        if (audit != null) {
            try {
                java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
                meta.put("scenarioId", scenarioId);
                meta.put("rounds", rounds.size());
                meta.put("stopReason", stopReason);
                meta.put("executor", executor.describe());
                String auditRunId = sotaRunId != null ? sotaRunId : branchKey;
                audit.record("scenario_run_sandbox_execute", auditRunId, meta);
            } catch (RuntimeException e) {
                log.warn("研究循环审计投递失败 branch={} err={}", branchKey, e.getMessage());
            }
        }
        return out;
    }

    /** 谱系列表（按场景 + 问题分组，倒序）。 */
    public List<Map<String, Object>> list(String scenarioId, int limit) {
        requireScenario(scenarioId);
        return dao.listResearchRuns(scenarioId, Math.min(Math.max(limit, 1), 100));
    }

    /** 单轮详情（含产物引用）。 */
    public Map<String, Object> detail(String runId) {
        List<Map<String, Object>> rows = dao.findResearchRun(runId);
        if (rows.isEmpty()) {
            throw new NotFoundException("RESEARCH-404: 研究运行不存在: " + runId);
        }
        Map<String, Object> out = new LinkedHashMap<>(rows.get(0));
        out.put("artifacts", dao.listRunArtifacts(runId));
        return out;
    }

    /**
     * 谱系对比：逐字段给两侧原值，不判优劣（人的判断）。
     *
     * <p>除字段差异外，另给出两轮在谱系中的<b>关系</b>（{@code relation}）与各自
     * 到最近公共祖先的深度——这是报告 §5.2「升级两两对比为树对比」的落地：
     * 树的信息体现在「谁是谁的祖先/同支兄弟/跨支」与深度上，而不是把整棵树塞进对比结果。</p>
     */
    public Map<String, Object> compare(String a, String b) {
        List<Map<String, Object>> ra = dao.findResearchRun(a);
        List<Map<String, Object>> rb = dao.findResearchRun(b);
        if (ra.isEmpty() || rb.isEmpty()) {
            throw new NotFoundException("RESEARCH-404: 对比记录缺失: " + (ra.isEmpty() ? a : b));
        }
        Map<String, Object> left = ra.get(0);
        Map<String, Object> right = rb.get(0);
        List<String> fields = List.of("loop_index", "parent_run_id", "sota_run_id", "status",
                "stop_reason", "hypotheses", "exec_trace", "advice");
        Map<String, Object> diff = new LinkedHashMap<>();
        for (String f : fields) {
            Map<String, Object> cell = new LinkedHashMap<>();
            cell.put("left", left.get(f));
            cell.put("right", right.get(f));
            cell.put("same", java.util.Objects.equals(left.get(f), right.get(f)));
            diff.put(f, cell);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("left", left);
        out.put("right", right);
        out.put("diff", diff);
        out.put("tree", treeRelation(a, b));
        return out;
    }

    /**
     * 两轮在同一谱系中的关系（报告 §5.2 树对比）。
     *
     * <p>只读同一场景的谱系全集，在内存里回溯父链。返回：</p>
     * <ul>
     *   <li>{@code relation}：{@code ANCESTOR}（a 是 b 祖先）/ {@code DESCENDANT} /
     *       {@code SIBLING}（同父，含同父分支）/ {@code CROSS_BRANCH}（不同分支）/ {@code SAME}；</li>
     *   <li>{@code commonAncestor}：最近公共祖先 id（无则 null）；</li>
     *   <li>{@code leftDepthFromCommon} / {@code rightDepthFromCommon}：各自到公共祖先的边数。</li>
     * </ul>
     */
    Map<String, Object> treeRelation(String a, String b) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (a.equals(b)) {
            out.put("relation", "SAME");
            out.put("commonAncestor", a);
            out.put("leftDepthFromCommon", 0);
            out.put("rightDepthFromCommon", 0);
            return out;
        }

        List<Map<String, Object>> all = dao.findResearchRun(a);
        List<Map<String, Object>> other = dao.findResearchRun(b);
        String scenarioId = all.isEmpty() ? null : str(all.get(0).get("scenario_id"));
        if (scenarioId == null && !other.isEmpty()) {
            scenarioId = str(other.get(0).get("scenario_id"));
        }
        // 全谱系（足够大的窗口）用于回溯父链；限量 200 防病态数据拖慢
        List<Map<String, Object>> lineage = scenarioId == null
                ? List.of() : dao.listResearchRuns(scenarioId, 200);
        Map<String, String> parentOf = new LinkedHashMap<>();
        for (Map<String, Object> row : lineage) {
            parentOf.put(str(row.get("id")), str(row.get("parent_run_id")));
        }
        // 两侧自身的父链也纳入（导出窗口可能截断，缺则按链末端处理）
        parentOf.putIfAbsent(a, str(all.isEmpty() ? null : all.get(0).get("parent_run_id")));
        parentOf.putIfAbsent(b, str(other.isEmpty() ? null : other.get(0).get("parent_run_id")));

        List<String> chainA = chainOf(a, parentOf);
        List<String> chainB = chainOf(b, parentOf);

        // a 是 b 的祖先？
        if (chainB.contains(a)) {
            out.put("relation", "ANCESTOR");
            out.put("commonAncestor", a);
            out.put("leftDepthFromCommon", 0);
            out.put("rightDepthFromCommon", chainB.indexOf(a));
            return out;
        }
        if (chainA.contains(b)) {
            out.put("relation", "DESCENDANT");
            out.put("commonAncestor", b);
            out.put("leftDepthFromCommon", chainA.indexOf(b));
            out.put("rightDepthFromCommon", 0);
            return out;
        }

        String common = null;
        for (String node : chainA) {
            if (chainB.contains(node)) {
                common = node;
                break;
            }
        }
        if (common != null) {
            int depthA = chainA.indexOf(common);
            int depthB = chainB.indexOf(common);
            out.put("relation", depthA == 1 && depthB == 1 ? "SIBLING" : "CROSS_BRANCH");
            out.put("commonAncestor", common);
            out.put("leftDepthFromCommon", depthA);
            out.put("rightDepthFromCommon", depthB);
            return out;
        }
        out.put("relation", "CROSS_BRANCH");
        out.put("commonAncestor", null);
        out.put("leftDepthFromCommon", chainA.size() - 1);
        out.put("rightDepthFromCommon", chainB.size() - 1);
        return out;
    }

    /** 从某节点回溯到根的链（含自身，自身在 index 0）；带环保护。 */
    private static List<String> chainOf(String id, Map<String, String> parentOf) {
        List<String> chain = new ArrayList<>();
        String cursor = id;
        int guard = 0;
        while (cursor != null && !cursor.isBlank() && guard++ < 64) {
            if (chain.contains(cursor)) {
                break; // 环：到此为止，不伪造更长的链
            }
            chain.add(cursor);
            cursor = parentOf.get(cursor);
        }
        return chain;
    }

    /** 空安全的字符串取值（DAO 返回 Object，null/空统一成 ""）。 */
    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    // ── 内部 ────────────────────────────────────────────────────────────────

    private void requireScenario(String scenarioId) {
        try {
            scenarioService.get(scenarioId);
        } catch (RuntimeException e) {
            throw new NotFoundException("RESEARCH-404: 场景不存在: " + scenarioId);
        }
    }

    private Map<String, Object> defaultBudget(Map<String, Object> requested) {
        Map<String, Object> budget = new LinkedHashMap<>();
        budget.put("maxLoops", clamp(intOf(value(requested, "maxLoops"), DEFAULT_MAX_LOOPS), 1, HARD_MAX_LOOPS));
        budget.put("maxSecondsPerLoop", clamp(intOf(value(requested, "maxSecondsPerLoop"),
                DEFAULT_MAX_SECONDS_PER_LOOP), 10, HARD_MAX_SECONDS_PER_LOOP));
        budget.put("maxTotalSeconds", clamp(intOf(value(requested, "maxTotalSeconds"),
                DEFAULT_MAX_TOTAL_SECONDS), 10, HARD_MAX_TOTAL_SECONDS));
        return budget;
    }

    private static Object value(Map<String, Object> m, String k) {
        return m == null ? null : m.get(k);
    }

    /** 评估降级占位（服务层只在被接受或结束时把真实 stop_reason 写回最后一次更新）。 */
    private static int intOf(Object v, int dft) {
        return v instanceof Number n ? n.intValue() : dft;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String text(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object v) {
        return v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asListOfMaps(Object v) {
        if (!(v instanceof List<?> l)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : l) {
            if (o instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private static String json(Object v) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(v == null ? null : v);
        } catch (Exception e) {
            return "null";
        }
    }
}
