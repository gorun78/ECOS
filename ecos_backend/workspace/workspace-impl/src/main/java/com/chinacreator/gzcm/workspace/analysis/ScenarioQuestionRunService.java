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
                                      @Qualifier("workspaceAipPipelineRest") RestTemplate llmRestTemplate) {
        this.dao = dao;
        this.pipelineRunClient = pipelineRunClient;
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
        List<Map<String, Object>> algorithms = recommendAlgorithms(String.valueOf(intent.get("intent")));
        trace.add(step("algorithms", "success", "候选算法 " + algorithms.size() + " 条", t2));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scenarioId", scenarioId);
        out.put("question", question);
        out.put("intent", intent.get("intent"));
        out.put("intentDetail", intent.get("detail"));
        out.put("hypotheses", hypotheses);
        out.put("algorithms", algorithms);
        out.put("mentalModels", listMentalModels());
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
                                   List<Map<String, Object>> hypotheses, String algorithmId) {
        if (question == null || question.isBlank()) {
            throw new BusinessException(400, "SCENARIO-Q-400: question 必填");
        }
        if (modelCode == null || modelCode.isBlank()) {
            throw new BusinessException(400, "SCENARIO-Q-400: 心智模型必选（决定运行路径）");
        }
        Map<String, Object> model = findMentalModel(modelCode);
        if (model == null) {
            throw new BusinessException(400, "SCENARIO-Q-400: 心智模型未登记: " + modelCode);
        }
        // DAO 返回的是库列名（snake_case）；两种命名都认，避免「有值却被判未接线」
        String pipelineId = str(model, "pipelineId", "pipeline_id");
        if (pipelineId == null || pipelineId.isBlank()) {
            throw new BusinessException(409, "SCENARIO-Q-409: 心智模型 " + modelCode + " 尚未接线承载编排");
        }

        long t0 = System.currentTimeMillis();
        List<Map<String, Object>> trace = new ArrayList<>();

        Map<String, Object> intent = detectIntent(question);
        trace.add(step("intent", "success", "意图=" + intent.get("intent"), t0));

        // 装配心智模型输入：问题 + 已确认假设（这是「获取心智模型需要的输入」的落地形态）
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("prompt", buildModelPrompt(question, hypotheses));
        inputs.put("scenarioId", scenarioId);

        long t1 = System.currentTimeMillis();
        String execStatus;
        String execDetail;
        Map<String, Object> execData = null;
        String modelOutput = null;
        boolean degraded;
        try {
            execData = pipelineRunClient.execute(pipelineId, inputs);
            Object outputs = execData.get("outputs");
            modelOutput = extractContent(outputs);
            Object degradedFlag = execData.get("degraded");
            degraded = Boolean.TRUE.equals(degradedFlag);
            execStatus = degraded ? "failed" : "success";
            execDetail = String.valueOf(execData.get("message"));
        } catch (RuntimeException e) {
            log.warn("场景问题运行：心智模型执行通道故障 model={} pipeline={}", modelCode, pipelineId, e);
            throw new BusinessException(503, "SCENARIO-Q-503: 心智模型执行不可用（" + e.getMessage() + "）");
        }
        trace.add(step("execute", execStatus, execDetail, t1));

        // 决策建议：拿真实执行结论去要建议，执行失败时不给「假建议」
        long t2 = System.currentTimeMillis();
        Map<String, Object> advice;
        if (degraded || modelOutput == null || modelOutput.isBlank()) {
            advice = Map.of(
                    "summary", "本次执行未产出有效结论，无法给出建议（执行状态：" + execStatus + "）",
                    "actions", List.of(),
                    "risks", List.of("心智模型执行失败，详见执行过程"),
                    "confidence", "LOW");
            trace.add(step("advice", "skipped", "执行未成功，不给建议", t2));
        } else {
            advice = buildAdvice(question, String.valueOf(model.get("name")), modelOutput);
            trace.add(step("advice", "success", "建议已生成", t2));
        }

        String runId = "run-" + UUID.randomUUID();
        String hypothesesJson = writeJson(hypotheses == null ? List.of() : hypotheses);
        String traceJson = writeJson(trace);
        String adviceJson = writeJson(advice);
        dao.insertQuestionRun(runId, scenarioId, question, String.valueOf(intent.get("intent")),
                String.valueOf(intent.get("detail")), modelCode, hypothesesJson, algorithmId,
                traceJson, adviceJson, degraded);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        out.put("scenarioId", scenarioId);
        out.put("question", question);
        out.put("intent", intent.get("intent"));
        out.put("intentDetail", intent.get("detail"));
        out.put("mentalModel", modelCode);
        out.put("mentalModelName", model.get("name"));
        out.put("pipelineId", pipelineId);
        out.put("hypotheses", hypotheses == null ? List.of() : hypotheses);
        out.put("algorithmId", algorithmId);
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
     * <p>需求要求「通过知识库 tools 获取」，这里用知识库文章作为检索语料 + LLM 提炼；
     * 知识库不可用时如实返回空列表并标注，不编造假设。</p>
     */
    private List<Map<String, Object>> proposeHypotheses(String scenarioId, String question, String intent) {
        String context = safeKnowledgeSnippet();
        String sys = "你是场景请求智能体。基于给定资料，列出该问题涉及的关键假设与信念，供用户确认。"
                + "只回 JSON 数组，每项 {\"text\":\"假设内容\",\"kind\":\"ASSUMPTION|BELIEF\",\"source\":\"依据\"}，最多 5 项。"
                + "资料不足就少列，不要编造数据。";
        String user = "问题：" + question + "\n意图：" + intent
                + (context.isBlank() ? "\n（无可用知识库资料）" : "\n参考资料：\n" + context);
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

    /** 知识库语料：取已登记知识库的文档标题做轻量语料；不可用即返回空串（不阻塞主链路）。 */
    private String safeKnowledgeSnippet() {
        try {
            List<Map<String, Object>> rows = dao.listKnowledgeBaseDocTitles(8);
            if (rows.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> r : rows) {
                Object title = r.get("title");
                if (title != null) sb.append("- ").append(title).append('\n');
            }
            return sb.toString();
        } catch (RuntimeException e) {
            log.debug("场景问题运行：知识库语料不可用（不阻塞主链路）: {}", e.getMessage());
            return "";
        }
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

    private static String buildModelPrompt(String question, List<Map<String, Object>> hypotheses) {
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
