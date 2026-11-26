package com.chinacreator.gzcm.workspace.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;

/**
 * 只读研究执行器（L3 首切片默认实现，2026-11-25）。
 *
 * <p><b>它到底做什么</b>：把场景<b>已绑定</b>的数据资产 / 知识资产 / 本体对象做成
 * 确定性口径盘点（绑定数、可解析数、知识文档命中数），据此生成假设、执行「只读探针」
 * 并给出确定性读数与建议。<b>不做</b>：不生成代码、不起进程、不调 LLM 编造指标、
 * 不写任何生产表。</p>
 *
 * <p><b>为什么先做只读型</b>：可行性报告 v2.0 §6.2 定「执行器分级」——查询型实验
 * （只读探针）可立即在 ECOS 内落地且零安全风险；代码型实验必须等隔离沙盒授权。
 * 本类就是查询型的最小实现，保证 L3 链路（编排/谱系/预算/降级）先真跑通。</p>
 *
 * <p><b>真值纪律</b>：所有读数来自 DAO 真实查询；查询失败即抛（由服务折算 503），
 * 编译期不使用任何随机数或占位常量。</p>
 */
@Component
public class ReadOnlyResearchExecutor implements ResearchExecutorPort {

    private static final Logger log = LoggerFactory.getLogger(ReadOnlyResearchExecutor.class);

    private final WorkspaceDataDao dao;

    public ReadOnlyResearchExecutor(WorkspaceDataDao dao) {
        this.dao = dao;
    }

    @Override
    public Map<String, Object> describe() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "READ_ONLY_PROBE");
        out.put("codeExecution", false);
        out.put("network", false);
        out.put("note", "只读探针执行器：确定性聚合场景绑定与知识命中，不执行生成代码");
        return out;
    }

    @Override
    public Map<String, Object> describeContext(String scenarioId) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> bindings = safe(() -> dao.listScenarioKnowledgeBindings(scenarioId));
        out.put("knowledgeBindings", bindings);
        out.put("bindingCount", bindings.size());
        // 可解析性：只把「绑定存在」与「资产可用」分开陈述，禁把前者冒充后者
        out.put("resolvable", null);
        out.put("resolvableNote", "首切片不内置跨域解析探针；绑定可用性以各属主实时读面为准");
        return out;
    }

    @Override
    public List<Map<String, Object>> proposeHypotheses(String scenarioId, String question) {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> ctx = describeContext(scenarioId);
        int bindingCount = (int) ctx.get("bindingCount");
        String keyword = firstKeyword(question);

        Map<String, Object> h1 = new LinkedHashMap<>();
        h1.put("text", bindingCount == 0
                ? "该场景尚未绑定任何知识/数据资产，任何结论都缺少场景内证据"
                : "该场景已绑定 " + bindingCount + " 项知识/数据资产，结论应由这些资产的真实读数支撑");
        h1.put("kind", "ASSUMPTION");
        h1.put("source", "场景绑定清单（DAO 实查）");
        h1.put("confirmed", true);
        out.add(h1);

        if (!keyword.isBlank()) {
            List<Map<String, Object>> docs = safe(() -> dao.searchKnowledgeDocs(List.of(keyword), 5));
            Map<String, Object> h2 = new LinkedHashMap<>();
            h2.put("text", docs.isEmpty()
                    ? "知识文档库未命中关键词「" + keyword + "」，需补充资料后再判断"
                    : "知识文档库命中 " + docs.size() + " 条与「" + keyword + "」相关的资料，可作为证据");
                h2.put("kind", "BELIEF");
            h2.put("source", "知识文档检索（DAO 实查，关键词=" + keyword + "）");
            h2.put("confirmed", true);
            out.add(h2);
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> refineHypotheses(String scenarioId, String question,
                                                      List<Map<String, Object>> previousRounds) {
        // 后续轮：把上一轮评估的 evidence 原文带回，形成「根据上轮读数修正」的假设
        Map<String, Object> last = previousRounds.isEmpty() ? Map.of() : previousRounds.get(previousRounds.size() - 1);
        Object evidence = last.get("evaluation") instanceof Map<?, ?> m ? m.get("evidence") : null;
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("text", evidence == null
                ? "上一轮未产出评估依据，本轮改为核对绑定资产的可解析性"
                : "依据上轮读数（" + evidence + "），本轮收窄到口径核对与证据补齐");
        h.put("kind", "ASSUMPTION");
        h.put("source", "上轮评估 evidence");
        h.put("confirmed", true);
        out.add(h);
        return out;
    }

    @Override
    public Map<String, Object> executeRound(String scenarioId, String question,
                                            List<Map<String, Object>> hypotheses, int loopIndex) {
        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> trace = new ArrayList<>();

        List<Map<String, Object>> bindings = safe(() -> dao.listScenarioKnowledgeBindings(scenarioId));
        trace.add(step("bindings", "success", "绑定资产 " + bindings.size() + " 项", t0));

        long t1 = System.currentTimeMillis();
        String keyword = firstKeyword(question);
        List<Map<String, Object>> docs = keyword.isBlank()
                ? List.of() : safe(() -> dao.searchKnowledgeDocs(List.of(keyword), 5));
        trace.add(step("knowledge", keyword.isBlank() ? "skipped" : "success",
                keyword.isBlank() ? "问题无可用关键词" : "命中资料 " + docs.size() + " 条", t1));

        long t2 = System.currentTimeMillis();
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("bindingCount", bindings.size());
        metric.put("docHitCount", docs.size());
        metric.put("hypothesisCount", hypotheses.size());
        trace.add(step("metric", "success", "确定性读数已聚合", t2));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("loopIndex", loopIndex);
        spec.put("question", question);
        spec.put("hypotheses", hypotheses);
        spec.put("dataSlices", List.of("scenario_bindings", "knowledge_docs"));
        spec.put("successCriteria", List.of(
                "绑定资产数 > 0",
                "知识命中数 ≥ 0 且来源可回指"));
        spec.put("executor", "READ_ONLY_PROBE");

        Map<String, Object> advice = new LinkedHashMap<>();
        advice.put("summary", bindings.isEmpty()
                ? "场景未绑定资产：建议先在「场景配置」补齐数据集/知识库绑定再研究"
                : "场景已绑定 " + bindings.size() + " 项资产，可基于这些资产推进下一步分析");
        advice.put("actions", bindings.isEmpty()
                ? List.of("补齐场景绑定")
                : List.of("核对绑定资产可解析性", "补充关键词相关资料"));
        advice.put("risks", List.of("只读探针不覆盖代码型实验，深度结论需沙盒执行器"));
        advice.put("confidence", "LOW");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("trace", trace);
        out.put("experimentSpec", spec);
        out.put("advice", advice);
        out.put("metric", metric);
        return out;
    }

    @Override
    public Map<String, Object> evaluate(String scenarioId, Map<String, Object> execResult,
                                        List<Map<String, Object>> previousRounds) {
        Map<String, Object> metric = execResult.get("metric") instanceof Map<?, ?> m
                ? cast(m) : Map.of();
        int bindingCount = intOf(metric.get("bindingCount"));
        int docHitCount = intOf(metric.get("docHitCount"));

        // 沙盒档的 metric 形态不同（rowsIn/rowsAfterFilter/results[]），但接受语义必须同源：
        // 「真实读数非空」才算有证据。此处把沙盒读数折算成同一门槛，禁因换执行器放宽判定。
        int sandboxRows = intOf(metric.get("rowsAfterFilter"));
        int sandboxResults = metric.get("results") instanceof List<?> l ? l.size() : 0;

        int evidenceCount = Math.max(docHitCount, sandboxResults);
        int sourceCount = Math.max(bindingCount, sandboxRows);

        // 接受门槛必须高于「只要绑定了资产」——否则首轮必中，多轮循环退化成单轮。
        // 判定：数据源存在 且 至少一条可回指的读数证据，才允许收敛。
        boolean accepted = sourceCount > 0 && evidenceCount > 0;
        // 连续无进展：前两轮都没命中证据时，第三轮不再空转
        boolean noProgress = !accepted && previousRounds.size() >= 2;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accepted", accepted);
        // 没有业务真值（行动结果→实际指标）时显式降级，禁把「结构满足」当「业务有效」
        out.put("degraded", true);
        out.put("evidence", "数据源=" + sourceCount + "，读数证据=" + evidenceCount
                + "（只读档: 绑定/知识命中；沙盒档: 快照行/度量结果）"
                + "；接受需「数据源>0 且 证据>0」；业务实际指标未接入（P1-18），本轮仅结构评估");
        out.put("noProgress", noProgress);
        return out;
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    private static Map<String, Object> step(String name, String status, String detail, long since) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("step", name);
        s.put("status", status);
        s.put("detail", detail);
        s.put("elapsedMs", System.currentTimeMillis() - since);
        return s;
    }

    private static String firstKeyword(String question) {
        if (question == null) return "";
        List<String> kw = com.chinacreator.gzcm.workspace.analysis.ScenarioQuestionRunService
                .extractKeywords(question);
        return kw.isEmpty() ? "" : kw.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        ((Map<Object, Object>) m).forEach((k, v) -> out.put(String.valueOf(k), v));
        return out;
    }

    private static int intOf(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    /** 查询失败如实抛出（由服务层折算 503），不吞成空列表冒充「没有数据」。 */
    private static <T> T safe(java.util.function.Supplier<T> s) {
        try {
            return s.get();
        } catch (RuntimeException e) {
            log.error("只读研究探针查询失败", e);
            throw e;
        }
    }
}
