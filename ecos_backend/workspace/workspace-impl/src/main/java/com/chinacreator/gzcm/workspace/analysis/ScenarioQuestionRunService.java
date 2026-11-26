package com.chinacreator.gzcm.workspace.analysis;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;
import com.chinacreator.gzcm.workspace.scenario.AipPipelineRunClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 问题驱动的场景运行编排（2026-11-24 场景运行页重构）。
 *
 * <h3>链路（对应需求 a~d）</h3>
 * <ol>
 *   <li><b>意图识别</b>：把用户问题交给 LLM，产出意图码与判断依据；</li>
 *   <li><b>假设与信念</b>：按问题与意图检索知识库（知识库 tools），产出候选假设/信念，
 *       <b>仅供用户确认</b>——本方法不停下来等确认，确认在 {@code confirm=true} 的第二次调用中体现；</li>
 *   <li><b>算法推荐</b>：读场景配置的算法库（{@code ecos_algorithm_registry}），按意图挑候选；</li>
 *   <li><b>执行</b>：调用用户选定的心智模型承载编排，拿到真实输出；</li>
 *   <li><b>决策建议</b>：把执行结论 + 假设交给 LLM，产出结构化建议；</li>
 *   <li>全过程节点流水写进 {@code exec_trace}，与结果一起落 {@code ecos_scenario_run}。</li>
 * </ol>
 *
 * <h3>真值纪律</h3>
 * <ul>
 *   <li>LLM 不可用 ⇒ 503，<b>不</b>编造意图/建议；</li>
 *   <li>心智模型编排执行失败 ⇒ 如实落 {@code degraded=true} 与失败原因，不折成成功；</li>
 *   <li>每一步的 {@code status/detail/elapsedMs} 都是真实耗时与真实结果。</li>
 * </ul>
 */
@Service
public class ScenarioQuestionRunService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioQuestionRunService.class);

    /** 意图码（与前端展示词表一致；不改的是码本身，展示走 i18n）。 */
    private static final List<String> INTENT_CODES =
            List.of("DIAGNOSE", "FORECAST", "EVALUATE", "DECIDE", "COMPARE", "OPTIMIZE");

    private final WorkspaceDataDao dao;
    private final AipPipelineRunClient pipelineRunClient;
    private final AlgorithmExecutionService algorithmExecutionService;
    private final com.chinacreator.gzcm.workspace.scenario.ScenarioRuleEvaluator ruleEvaluator;
    private final ScenarioRunArtifactWriter artifactWriter;
    private final com.chinacreator.gzcm.workspace.scenario.WorkspaceSecurityAudit audit;
    private final RestTemplate llmRestTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 本次部署实际使用的模型名。
     *
     * <p>取值链：{@code ECOS_QRUN_LLM_MODEL} → {@code LLM_DEFAULT_MODEL} → {@code Qwen3.8-27B}。
     * 模型名属<b>部署事实</b>，不入库、不写死业务逻辑；缺省值与内网网关的模型目录对齐。</p>
     */
    private final String llmModel;

    public ScenarioQuestionRunService(WorkspaceDataDao dao,
                                      AipPipelineRunClient pipelineRunClient,
                                      AlgorithmExecutionService algorithmExecutionService,
                                      com.chinacreator.gzcm.workspace.scenario.ScenarioRuleEvaluator ruleEvaluator,
                                      ScenarioRunArtifactWriter artifactWriter,
                                      com.chinacreator.gzcm.workspace.scenario.WorkspaceSecurityAudit audit,
                                      @Qualifier("workspaceAipPipelineRest") RestTemplate llmRestTemplate) {
        this.dao = dao;
        this.pipelineRunClient = pipelineRunClient;
        this.algorithmExecutionService = algorithmExecutionService;
        this.ruleEvaluator = ruleEvaluator;
        this.artifactWriter = artifactWriter;
        this.audit = audit;
        this.llmRestTemplate = llmRestTemplate;
        this.llmModel = firstNonBlank(System.getenv("ECOS_QRUN_LLM_MODEL"),
                System.getenv("LLM_DEFAULT_MODEL"), "Qwen3.8-27B");
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    /**
     * 阶段一：只做意图识别 + 假设检索 + 算法推荐（不执行）。
     *
     * <p>供前端「先确认假设再执行」的两段式交互使用；用户确认后再调 {@link #run}。</p>
     */
    public Map<String, Object> prepare(String scenarioId, String question) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(400, "SCENARIO-Q-400: question 必填（问题驱动运行的输入锚点）");
        }
        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> trace = new ArrayList<>();

        Map<String, Object> intent = detectIntent(question);
        trace.add(step("intent", "success", "意图=" + intent.get("intent"), t0));

        long t1 = System.currentTimeMillis();
        List<Map<String, Object>> hypotheses = proposeHypotheses(scenarioId, question, String.valueOf(intent.get("intent")));
        trace.add(step("hypotheses", "success", "候选假设 " + hypotheses.size() + " 条", t1));

        long t2 = System.currentTimeMillis();
        List<Map<String, Object>> algorithms = recommendAlgorithms(String.valueOf(intent.get("intent")))
                .stream().map(algorithmExecutionService::decorate).toList();
        trace.add(step("algorithms", "success", "候选算法 " + algorithms.size() + " 条", t2));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scenarioId", scenarioId);
        out.put("question", question);
        out.put("intent", intent.get("intent"));
        out.put("intentDetail", intent.get("detail"));
        out.put("hypotheses", hypotheses);
        out.put("algorithms", algorithms);
        out.put("mentalModels", listMentalModels());
        Map<String, Object> executors = new LinkedHashMap<>();
        executors.put("mentalModels", listMentalModels());
        executors.put("pipelines", listBoundExecutors(scenarioId, "PIPELINE"));
        executors.put("agents", listBoundExecutors(scenarioId, "AI_AGENT"));
        out.put("executors", executors);
        out.put("trace", trace);
        return out;
    }

    /**
     * 阶段二：按用户已确认的假设与选定模型真正执行，并产出决策建议。
     *
     * @param hypotheses 用户确认后的假设清单（文本 + 是否采纳）
     * @param modelCode  选定的心智模型码（FIRST_PRINCIPLES / INVERT / ...）
     */
    public Map<String, Object> run(String scenarioId, String question, String modelCode,
                                   List<Map<String, Object>> hypotheses, String algorithmId,
                                   Map<String, Object> algorithmInputs,
                                   String executorType, String executorId) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(400, "SCENARIO-Q-400: question 必填");
        }
        // 主执行载体三选一（V367 批次 H）：MENTAL_MODEL（默认）/ SCENARIO_PIPELINE / AI_AGENT
        String execType = executorType == null || executorType.isBlank()
                ? "MENTAL_MODEL" : executorType.trim().toUpperCase();
        String execId = executorId;
        String pipelineId = null;
        Map<String, Object> model = null;
        String execName;
        switch (execType) {
            case "MENTAL_MODEL" -> {
                if (modelCode == null || modelCode.isBlank()) {
                    throw new BusinessException(400, "SCENARIO-Q-400: 心智模型必选（决定运行路径）");
                }
                model = findMentalModel(modelCode);
                if (model == null) {
                    throw new BusinessException(400, "SCENARIO-Q-400: 心智模型未登记: " + modelCode);
                }
                pipelineId = str(model, "pipelineId", "pipeline_id");
                if (pipelineId == null || pipelineId.isBlank()) {
                    throw new BusinessException(409, "SCENARIO-Q-409: 心智模型 " + modelCode + " 尚未接线承载编排");
                }
                execId = modelCode;
                execName = String.valueOf(model.get("name"));
            }
            case "SCENARIO_PIPELINE" -> {
                if (execId == null || execId.isBlank()) {
                    throw new BusinessException(400, "SCENARIO-Q-400: 场景编排必选 executorId");
                }
                assertBound(scenarioId, "PIPELINE", execId);
                pipelineId = execId;
                execName = "场景编排 " + execId;
            }
            case "AI_AGENT" -> {
                if (execId == null || execId.isBlank()) {
                    throw new BusinessException(400, "SCENARIO-Q-400: 智能体必选 executorId");
                }
                assertBound(scenarioId, "AI_AGENT", execId);
                execName = "智能体 " + execId;
            }
            default -> throw new BusinessException(400,
                    "SCENARIO-Q-400: executorType 非法（MENTAL_MODEL|SCENARIO_PIPELINE|AI_AGENT）: " + executorType);
        }

        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> trace = new ArrayList<>();

        Map<String, Object> intent = detectIntent(question);
        trace.add(step("intent", "success", "意图=" + intent.get("intent"), t0));

        // ── 算法执行（V367）：选的算法必须真跑，数值是后续 prompt 的唯一事实来源 ──
        Map<String, Object> algorithmResult = null;
        String algorithmStatus = "SKIPPED";
        Integer algorithmElapsedMs = null;
        boolean algorithmFailed = false;
        if (algorithmId != null && !algorithmId.isBlank()) {
            long ta = System.currentTimeMillis();
            try {
                algorithmResult = algorithmExecutionService.execute(algorithmId, algorithmInputs);
                algorithmStatus = String.valueOf(algorithmResult.getOrDefault("status", "FAILED"));
                Object elapsed = algorithmResult.get("elapsedMs");
                if (elapsed instanceof Number n) algorithmElapsedMs = n.intValue();
                algorithmFailed = !"SUCCEEDED".equalsIgnoreCase(algorithmStatus)
                        && !"DEGRADED".equalsIgnoreCase(algorithmStatus);
                trace.add(step("algorithm", algorithmFailed ? "failed" : "success",
                        "算法 " + algorithmResult.get("name") + " → " + algorithmStatus, ta));
            } catch (RuntimeException e) {
                algorithmFailed = true;
                algorithmStatus = "FAILED";
                algorithmElapsedMs = (int) (System.currentTimeMillis() - ta);
                algorithmResult = new LinkedHashMap<>();
                algorithmResult.put("status", "FAILED");
                algorithmResult.put("algorithmId", algorithmId);
                algorithmResult.put("message", e.getMessage());
                trace.add(step("algorithm", "failed", "算法执行失败: " + e.getMessage(), ta));
            }
        }
        String algorithmFacts = buildAlgorithmFacts(algorithmResult);

        // 装配心智模型输入：问题 + 已确认假设 + 场景绑定的知识与数据资产 + 算法事实
        // （需求「装配获取心智模型需要的数据和知识资产」落在这三项上：模型拿到的不只是问题本身）
        KnowledgeContext modelCtx = loadKnowledgeContext(scenarioId, question);

        // ── 规则求值（V367 批次 E）：绑定规则只读求值，结果进轨迹与 prompt ──
        List<Map<String, Object>> rules = new ArrayList<>();
        long tr = System.currentTimeMillis();
        try {
            List<String> ruleIds = new ArrayList<>();
            for (Map<String, Object> b : listBoundExecutors(scenarioId, "RULE_SET")) {
                String id = str(b, "target_id", "targetId");
                if (id != null && !id.isBlank()) ruleIds.add(id);
            }
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("hypothesisCount", hypotheses == null ? 0 : hypotheses.size());
            facts.put("knowledgeBindingCount", modelCtx.bindings().size());
            // 场景指标（metrics JSONB）进规则事实表：表达式可直接引用业务指标名
            try {
                String metricsJson = dao.selectScenarioMetrics(scenarioId);
                if (metricsJson != null && !metricsJson.isBlank()) {
                    Object parsed = objectMapper.readValue(metricsJson, Map.class);
                    if (parsed instanceof Map<?, ?> mm) {
                        mm.forEach((k, v) -> facts.put(String.valueOf(k), v));
                    }
                }
            } catch (Exception e) {
                log.debug("场景规则事实：metrics 读取/解析失败（跳过）: {}", e.getMessage());
            }
            if (algorithmResult != null && algorithmResult.get("metrics") instanceof Map<?, ?> mm) {
                mm.forEach((k, v) -> facts.put(String.valueOf(k), v));
            }
            rules = ruleEvaluator.evaluate(ruleIds, facts);
            long matched = rules.stream().filter(r -> Boolean.TRUE.equals(r.get("matched"))).count();
            trace.add(step("rules", rules.isEmpty() ? "skipped" : "success",
                    "规则命中 " + matched + "/" + rules.size(), tr));
        } catch (RuntimeException e) {
            trace.add(step("rules", "failed", "规则求值不可用: " + e.getMessage(), tr));
        }
        String ruleFacts = buildRuleFacts(rules);

        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("prompt", buildModelPrompt(question, hypotheses, modelCtx, algorithmFacts, ruleFacts));
        inputs.put("scenarioId", scenarioId);
        inputs.put("confirmedHypotheses", hypotheses == null ? List.of() : hypotheses);
        inputs.put("knowledgeBindings", modelCtx.bindings());
        inputs.put("algorithmFacts", algorithmFacts);
        inputs.put("ruleFacts", ruleFacts);

        long t1 = System.currentTimeMillis();
        String execStatus;
        String execDetail;
        String modelOutput = null;
        boolean modelDegraded;
        String sessionId = null;
        if ("AI_AGENT".equals(execType)) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("scenarioId", scenarioId);
            context.put("confirmedHypotheses", hypotheses == null ? List.of() : hypotheses);
            context.put("algorithmFacts", algorithmFacts);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("message", inputs.get("prompt"));
            payload.put("context", context);
            try {
                Map<String, Object> resp = pipelineRunClient.invokeAgent(execId, payload);
                modelOutput = str(resp, "answer", "response");
                sessionId = str(resp, "sessionId");
                modelDegraded = !Boolean.TRUE.equals(resp.get("success"))
                        || modelOutput == null || modelOutput.isBlank();
                execStatus = modelDegraded ? "failed" : "success";
                execDetail = "agent turns=" + resp.getOrDefault("turns", "-")
                        + " tokens=" + resp.getOrDefault("tokens", "-");
            } catch (RuntimeException e) {
                log.warn("场景问题运行：智能体调用通道故障 agent={}", execId, e);
                throw new BusinessException(503, "SCENARIO-Q-503: 智能体调用不可用（" + e.getMessage() + "）");
            }
        } else {
            try {
                Map<String, Object> execData = pipelineRunClient.execute(pipelineId, inputs);
                modelOutput = extractContent(execData.get("outputs"));
                modelDegraded = Boolean.TRUE.equals(execData.get("degraded"));
                execStatus = modelDegraded ? "failed" : "success";
                execDetail = String.valueOf(execData.get("message"));
            } catch (RuntimeException e) {
                log.warn("场景问题运行：编排执行通道故障 type={} id={}", execType, pipelineId, e);
                throw new BusinessException(503, "SCENARIO-Q-503: 编排执行不可用（" + e.getMessage() + "）");
            }
        }
        trace.add(step("execute", execStatus, execType + " " + execName + " → " + execStatus, t1));
        boolean degraded = modelDegraded || algorithmFailed;

        // 决策建议：拿真实执行结论去要建议，执行失败时不给「假建议」
        long t2 = System.currentTimeMillis();
        Map<String, Object> advice;
        if (modelDegraded || modelOutput == null || modelOutput.isBlank()) {
            advice = Map.of(
                    "summary", "本次执行未产出有效结论，无法给出建议（执行状态：" + execStatus + "）",
                    "actions", List.of(),
                    "risks", List.of("心智模型执行失败，详见执行过程"),
                    "confidence", "LOW");
            trace.add(step("advice", "skipped", "执行未成功，不给建议", t2));
        } else {
            advice = buildAdvice(question, execName, modelOutput);
            if (algorithmFailed) advice = forceLow(advice);
            trace.add(step("advice", "success", "建议已生成", t2));
        }

        String runId = "run-" + UUID.randomUUID();
        String hypothesesJson = writeJson(hypotheses == null ? List.of() : hypotheses);
        String traceJson = writeJson(trace);
        String adviceJson = writeJson(advice);
        String algorithmResultJson = algorithmResult == null ? null : writeJson(algorithmResult);
        dao.insertQuestionRun(runId, scenarioId, question, String.valueOf(intent.get("intent")),
                String.valueOf(intent.get("detail")), modelCode, hypothesesJson, algorithmId,
                algorithmStatus, algorithmResultJson, algorithmElapsedMs,
                execType, execId, sessionId, traceJson, adviceJson, degraded);

        // ── 产物留档（V367 批次 F）：算法结果与执行轨迹各一份，失败不阻断 ──
        artifactWriter.writeJson(runId, scenarioId, "ALGORITHM_RESULT", algorithmResult);
        artifactWriter.writeJson(runId, scenarioId, "EXEC_TRACE", trace);

        // ── 审计留痕（不落正文）：执行载体 / 算法状态 / 降级 ──
        try {
            audit.record("scenario_question_run", runId, Map.of(
                    "executorType", execType,
                    "executorId", String.valueOf(execId),
                    "algorithmStatus", algorithmStatus,
                    "degraded", degraded));
        } catch (RuntimeException e) {
            log.warn("场景运行审计投递失败 run={} err={}", runId, e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("scenarioId", scenarioId);
        out.put("question", question);
        out.put("intent", intent.get("intent"));
        out.put("intentDetail", intent.get("detail"));
        out.put("mentalModel", modelCode);
        out.put("mentalModelName", execName);
        out.put("pipelineId", pipelineId);
        out.put("hypotheses", hypotheses == null ? List.of() : hypotheses);
        out.put("algorithmId", algorithmId);
        out.put("algorithmStatus", algorithmStatus);
        out.put("algorithmResult", algorithmResult);
        out.put("executorType", execType);
        out.put("executorId", execId);
        out.put("executorSessionId", sessionId);
        out.put("trace", trace);
        out.put("degraded", degraded);
        out.put("result", modelOutput);
        out.put("advice", advice);
        return out;
    }

    // ── LLM 调用（走 AIP 同款 REST 通道，中继调用方凭据）────────────────────

    /**
     * 调 LLM（{@code POST /api/v1/llm/chat}）。
     *
     * <p>关键：必须中继调用方凭据（{@link CallerCredentialSource#applyTo}），
     * 否则护栏裁决拿不到主体 → 403。这是本批踩过的坑，注释留痕防复发。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private String callLlm(String systemPrompt, String userPrompt, int maxTokens) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        CallerCredentialSource.applyTo(headers);

        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> body = new LinkedHashMap<>();
        // 必须显式传 model：上游 OpenAI 兼容网关对空 model 直接 400，
        // 不会回落到网关默认（实测 2026-11-24：缺 model 报 "Model name not specified"）
        body.put("model", llmModel);
        body.put("messages", messages);
        body.put("temperature", 0.3);
        body.put("maxTokens", maxTokens);

        ResponseEntity<Map> resp;
        try {
            resp = llmRestTemplate.exchange("/llm/chat", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);
        } catch (RuntimeException e) {
            throw new BusinessException(503, "SCENARIO-Q-503: 对话网关不可用（" + e.getMessage() + "）");
        }
        Map<String, Object> envelope = resp.getBody();
        if (envelope == null) {
            throw new BusinessException(503, "SCENARIO-Q-503: 对话网关应答为空");
        }
        Object code = envelope.get("code");
        if (code instanceof Number n && n.intValue() != 0) {
            throw new BusinessException(503, "SCENARIO-Q-503: 对话网关业务错误 " + envelope.get("message"));
        }
        Object data = envelope.get("data");
        if (!(data instanceof Map<?, ?> d)) {
            throw new BusinessException(503, "SCENARIO-Q-503: 对话网关应答缺 data 段");
        }
        Object content = d.get("content");
        return content == null ? "" : String.valueOf(content);
    }

    // ── 各步骤实现 ─────────────────────────────────────────────────────────

    private Map<String, Object> detectIntent(String question) {
        String sys = "你是场景请求智能体。请判断用户问题属于哪一类意图，只回一个 JSON，不要任何解释。"
                + "可用意图码：" + String.join("/", INTENT_CODES) + "。"
                + "JSON 结构：{\"intent\":\"码\",\"detail\":\"一句话判断依据\"}";
        String raw = callLlm(sys, question, 300);
        String intentCode = INTENT_CODES.get(0);
        String detail = "";
        try {
            Map<?, ?> parsed = objectMapper.readValue(stripFence(raw), Map.class);
            Object i = parsed.get("intent");
            if (i != null && INTENT_CODES.contains(String.valueOf(i))) {
                intentCode = String.valueOf(i);
            }
            Object d = parsed.get("detail");
            if (d != null) detail = String.valueOf(d);
        } catch (Exception e) {
            // 解析失败不编造：保留原文进 detail，意图码回落首个（可辨认，不静默）
            log.warn("场景问题运行：意图 JSON 解析失败，原文入 detail: {}", raw);
            detail = raw;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("intent", intentCode);
        out.put("detail", detail);
        return out;
    }

    /**
     * 候选假设/信念。
     *
     * <p>需求要求「通过知识库 tools 获取」。知识检索端点
     * （{@code POST /api/v1/knowledge/retrieval/search}）在本部署被 OPA
     * {@code kb.retrieval.search} 默认 DENY（rbac 包无该动作规则，fail-closed 基线，
     * 不擅自改安全策略），故改走<b>同源知识资产</b>：场景绑定的 KNOWLEDGE_BASE/DATASET
     * + 知识文档库按问题关键词的正文片段。取到什么用什么，取不到如实标注无依据。</p>
     */
    private List<Map<String, Object>> proposeHypotheses(String scenarioId, String question, String intent) {
        KnowledgeContext ctx = loadKnowledgeContext(scenarioId, question);
        String sys = "你是场景请求智能体。基于给定资料，列出该问题涉及的关键假设与信念，供用户确认。"
                + "只回 JSON 数组，每项 {\"text\":\"假设内容\",\"kind\":\"ASSUMPTION|BELIEF\",\"source\":\"依据\"}，最多 5 项。"
                + "source 必须指向给定资料里的具体条目；资料里没有的就写「无知识库依据」，不要编造。";
        String user = "问题：" + question + "\n意图：" + intent + "\n" + ctx.render();
        String raw = callLlm(sys, user, 900);
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            Object parsed = objectMapper.readValue(stripFence(raw), Object.class);
            if (parsed instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m && m.get("text") != null) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("text", String.valueOf(m.get("text")));
                        item.put("kind", m.get("kind") == null ? "ASSUMPTION" : String.valueOf(m.get("kind")));
                        item.put("source", m.get("source") == null ? "" : String.valueOf(m.get("source")));
                        item.put("confirmed", true);
                        out.add(item);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("场景问题运行：假设 JSON 解析失败，返回空列表（不编造）: {}", raw);
        }
        return out;
    }

    /** 一次运行可用的知识上下文（绑定资产 + 文档片段），渲染成给 LLM/心智模型的资料段。 */
    private record KnowledgeContext(List<Map<String, Object>> bindings,
                                    List<Map<String, Object>> docs) {

        boolean isEmpty() {
            return bindings.isEmpty() && docs.isEmpty();
        }

        /** 给 LLM 的「参考资料」段；空时显式说明，避免模型误以为有资料。 */
        String render() {
            if (isEmpty()) {
                return "参考资料：（本场景未绑定知识资产，且知识文档库无相关条目）";
            }
            StringBuilder sb = new StringBuilder("参考资料：\n");
            if (!bindings.isEmpty()) {
                sb.append("[已绑定知识资产]\n");
                for (Map<String, Object> b : bindings) {
                    sb.append("- ").append(b.get("binding_type")).append(": ")
                      .append(b.get("target_id")).append('\n');
                }
            }
            if (!docs.isEmpty()) {
                sb.append("[知识文档片段]\n");
                for (Map<String, Object> d : docs) {
                    sb.append("- 《").append(d.get("title")).append("》 ");
                    Object snippet = d.get("snippet");
                    sb.append(snippet == null ? "（无正文）" : String.valueOf(snippet)).append('\n');
                }
            }
            return sb.toString();
        }
    }

    /**
     * 装载知识上下文：场景绑定的资产 + 按问题关键词命中的知识文档片段。
     *
     * <p>任何一步失败都不阻塞主链路（降级为空并记日志），但**绝不**在失败时假装有资料。</p>
     */
    private KnowledgeContext loadKnowledgeContext(String scenarioId, String question) {
        List<Map<String, Object>> bindings = List.of();
        try {
            bindings = dao.listScenarioKnowledgeBindings(scenarioId);
        } catch (RuntimeException e) {
            log.warn("场景问题运行：场景知识绑定读取失败（降级为空）: {}", e.getMessage());
        }
        List<Map<String, Object>> docs = List.of();
        try {
            docs = dao.searchKnowledgeDocs(extractKeywords(question), 5);
        } catch (RuntimeException e) {
            log.warn("场景问题运行：知识文档检索失败（降级为空）: {}", e.getMessage());
        }
        return new KnowledgeContext(bindings, docs);
    }

    /**
     * 从问题里抽关键词（去停用词/标点，取长度≥2 的词元，最多 6 个）。
     *
     * <p>轻量启发式而非分词器：只为 ILIKE 定位候选文档，精度不足由 LLM 在假设阶段择优。</p>
     */
    static List<String> extractKeywords(String question) {
        if (question == null || question.isBlank()) return List.of();
        String cleaned = question.replaceAll("[\\p{Punct}，。？！、；：“”‘’（）《》【】…—]", " ");
        java.util.Set<String> stop = java.util.Set.of(
                "如何", "怎么", "什么", "为什么", "应该", "我们", "可以", "这个", "那个",
                "以及", "但是", "如果", "因为", "所以", "进行", "需要", "问题", "情况");
        List<String> out = new ArrayList<>();
        for (String w : cleaned.split("\\s+")) {
            String t = w.trim();
            if (t.length() < 2 || stop.contains(t)) continue;
            if (!out.contains(t)) out.add(t);
            if (out.size() >= 6) break;
        }
        return out;
    }

    private List<Map<String, Object>> recommendAlgorithms(String intent) {
        String analysisType = switch (intent) {
            case "FORECAST" -> "FORECAST";
            case "EVALUATE", "DECIDE", "COMPARE", "OPTIMIZE" -> "SIMULATION";
            default -> "CAUSAL";
        };
        try {
            return dao.listActiveAlgorithmsByType(analysisType);
        } catch (RuntimeException e) {
            log.warn("场景问题运行：算法库读取失败: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> listMentalModels() {
        try {
            return dao.listActiveMentalModels();
        } catch (RuntimeException e) {
            log.warn("场景问题运行：心智模型目录读取失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 场景绑定的执行载体（PIPELINE / AI_AGENT）；读失败降级空表，不伪造。 */
    private List<Map<String, Object>> listBoundExecutors(String scenarioId, String bindingType) {
        try {
            return dao.listScenarioBindings(scenarioId, bindingType);
        } catch (RuntimeException e) {
            log.warn("场景问题运行：{} 绑定读取失败: {}", bindingType, e.getMessage());
            return List.of();
        }
    }

    /** 归属校验：只能调用本场景绑定的编排/智能体，禁止跨场景裸 id。 */
    private void assertBound(String scenarioId, String bindingType, String targetId) {
        for (Map<String, Object> row : listBoundExecutors(scenarioId, bindingType)) {
            if (targetId.equals(str(row, "target_id", "targetId"))) return;
        }
        throw new BusinessException(403,
                "SCENARIO-Q-403: " + bindingType + " 未绑定到本场景: " + targetId);
    }

    private Map<String, Object> findMentalModel(String code) {
        for (Map<String, Object> m : listMentalModels()) {
            if (code.equals(String.valueOf(m.get("code")))) return m;
        }
        return null;
    }

    private Map<String, Object> buildAdvice(String question, String modelName, String modelOutput) {
        String sys = "你是决策建议助手。基于心智模型的分析结论，给出可执行的决策建议。"
                + "只回 JSON：{\"summary\":\"一句话结论\",\"actions\":[\"动作1\",\"动作2\"],"
                + "\"risks\":[\"风险1\"],\"confidence\":\"HIGH|MEDIUM|LOW\"}。不要编造未在结论中出现的数据。";
        String user = "问题：" + question + "\n心智模型：" + modelName + "\n分析结论：\n" + modelOutput;
        String raw = callLlm(sys, user, 800);
        try {
            Map<?, ?> parsed = objectMapper.readValue(stripFence(raw), Map.class);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("summary", parsed.get("summary") == null ? "" : String.valueOf(parsed.get("summary")));
            out.put("actions", parsed.get("actions") instanceof List<?> l ? l : List.of());
            out.put("risks", parsed.get("risks") instanceof List<?> l ? l : List.of());
            out.put("confidence", parsed.get("confidence") == null ? "MEDIUM" : String.valueOf(parsed.get("confidence")));
            return out;
        } catch (Exception e) {
            log.warn("场景问题运行：建议 JSON 解析失败，退回原文摘要: {}", raw);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("summary", raw.length() > 400 ? raw.substring(0, 400) : raw);
            out.put("actions", List.of());
            out.put("risks", List.of());
            out.put("confidence", "LOW");
            return out;
        }
    }

    // ── 工具 ───────────────────────────────────────────────────────────────

    /** 按多个候选键名取字符串值（DAO 走库列名 snake_case，API 面可能是 camelCase）。 */
    private static String str(Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) {
                String s = String.valueOf(v);
                if (!s.isBlank()) return s;
            }
        }
        return null;
    }

    private static Map<String, Object> step(String name, String status, String detail, long since) {        Map<String, Object> s = new LinkedHashMap<>();
        s.put("step", name);
        s.put("status", status);
        s.put("detail", detail);
        s.put("elapsedMs", System.currentTimeMillis() - since);
        return s;
    }

    private static Map<String, Object> forceLow(Map<String, Object> advice) {
        Map<String, Object> out = new LinkedHashMap<>(advice);
        out.put("confidence", "LOW");
        List<Object> risks = new ArrayList<>();
        if (advice.get("risks") instanceof List<?> l) risks.addAll(l);
        risks.add("算法执行未成功，建议仅基于心智模型定性分析");
        out.put("risks", risks);
        return out;
    }

    /** 算法事实块：只允许模型解释这些数字，不允许新增。 */
    private String buildAlgorithmFacts(Map<String, Object> algorithmResult) {
        if (algorithmResult == null) return "";
        String status = String.valueOf(algorithmResult.getOrDefault("status", "UNKNOWN"));
        if (!"SUCCEEDED".equalsIgnoreCase(status) && !"DEGRADED".equalsIgnoreCase(status)) {
            return "算法执行未成功（" + status + "）：" + algorithmResult.getOrDefault("message", "无详情");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("算法：").append(algorithmResult.getOrDefault("name", algorithmResult.get("algorithmId")));
        sb.append("（").append(algorithmResult.getOrDefault("code", "")).append("）\n");
        sb.append("结构化数值：").append(writeJson(algorithmResult.getOrDefault("metrics", Map.of()))).append('\n');
        Object assumptions = algorithmResult.get("assumptions");
        if (assumptions instanceof List<?> l && !l.isEmpty()) {
            sb.append("算法限制：").append(String.join("；", l.stream().map(String::valueOf).toList())).append('\n');
        }
        return sb.toString();
    }

    private String buildRuleFacts(List<Map<String, Object>> rules) {
        if (rules == null || rules.isEmpty()) return "本场景未绑定规则";
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> r : rules) {
            sb.append("- ").append(r.getOrDefault("name", r.get("ruleId")))
              .append(": matched=").append(r.get("matched"));
            if (r.get("error") != null) sb.append(" error=").append(r.get("error"));
            if (r.get("action") != null) sb.append(" action=").append(r.get("action"));
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String buildModelPrompt(String question, List<Map<String, Object>> hypotheses,
                                           KnowledgeContext ctx, String algorithmFacts, String ruleFacts) {
        StringBuilder sb = new StringBuilder();
        sb.append("问题：").append(question).append('\n');
        if (hypotheses != null && !hypotheses.isEmpty()) {
            sb.append("已确认的假设与信念：\n");
            for (Map<String, Object> h : hypotheses) {
                Object text = h.get("text");
                Object confirmed = h.get("confirmed");
                if (text != null && !Boolean.FALSE.equals(confirmed)) {
                    sb.append("- ").append(text).append('\n');
                }
            }
        }
        // 知识资产同样进模型输入：心智模型不该在「不知道本场景有哪些资料」的情况下推理
        if (ctx != null && !ctx.isEmpty()) {
            sb.append('\n').append(ctx.render());
        }
        if (algorithmFacts != null && !algorithmFacts.isBlank()) {
            sb.append('\n').append("算法事实（只能解释，不得新增或改动数字）：\n")
              .append(algorithmFacts);
        }
        if (ruleFacts != null && !ruleFacts.isBlank()) {
            sb.append('\n').append("规则事实（只读求值结果）：\n").append(ruleFacts);
        }
        return sb.toString();
    }

    /** 从编排 outputs 里取 LLM 产出。节点输出可能是对象或字符串化 JSON，两种都认。 */
    private String extractContent(Object outputs) {
        if (!(outputs instanceof Map<?, ?> m) || m.isEmpty()) return null;
        Object first = m.values().iterator().next();
        Object content = null;
        if (first instanceof Map<?, ?> nodeOut) {
            content = nodeOut.get("content");
        } else if (first instanceof String s) {
            try {
                Object parsed = objectMapper.readValue(s, Object.class);
                if (parsed instanceof Map<?, ?> pm) content = pm.get("content");
            } catch (Exception ignore) {
                return s;
            }
        }
        return content == null ? null : String.valueOf(content);
    }

    /** 去掉 LLM 可能带上的 ```json 围栏。 */
    private static String stripFence(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl > 0) s = s.substring(firstNl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    private String writeJson(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception e) {
            return "[]";
        }
    }
}
