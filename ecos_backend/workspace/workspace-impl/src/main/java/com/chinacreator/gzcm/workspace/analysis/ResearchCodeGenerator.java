package com.chinacreator.gzcm.workspace.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;

/**
 * LLM 代码生成器（L3 代码进化的「实现」环节，2026-11-25 用户授权）。
 *
 * <p><b>它只做一件事</b>：把「假设 + 可用数据列 + 目标指标」变成一段**纯计算**代码。
 * 生成完立即交给隔离沙盒执行，本进程既不编译也不运行。</p>
 *
 * <p><b>为什么必须约束提示词</b>：LLM 天性会写「读文件/发请求/装包」。沙盒会拒，
 * 但每拒一次就浪费一轮预算与时间。因此系统提示词把「只能用标准库做纯计算、
 * 必须打印 {@code METRIC <数字>}」写成硬约束，并在代码里做<b>生成后自检</b>。</p>
 *
 * <p><b>失败不编造</b>：LLM 不可用、返回不可解析、生成后自检不过 ⇒ 抛异常，
 * 由调用方显式标记本轮降级。绝不返回「看起来能跑的假代码」。</p>
 */
@Component
public class ResearchCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(ResearchCodeGenerator.class);

    /** 支持的生成语言（与沙盒对齐）。 */
    public static final String LANG_JAVA = "java";
    public static final String LANG_PYTHON = "python";

    /** 指标约定：程序必须在 stdout 打印一行 {@code METRIC <数字>}。 */
    static final String METRIC_CONTRACT = "METRIC";

    private final ObjectProvider<RestTemplate> llmProvider;
    private final String model;

    public ResearchCodeGenerator(
            @Qualifier("workspaceAipPipelineRest") ObjectProvider<RestTemplate> llmProvider) {
        this.llmProvider = llmProvider;
        this.model = firstNonBlank(System.getenv("ECOS_QRUN_LLM_MODEL"),
                System.getenv("LLM_DEFAULT_MODEL"), "Qwen3.8-27B");
    }

    /**
     * 生成一段代码。
     *
     * @param language 目标语言（java / python）
     * @param question 研究问题
     * @param hypothesis 本轮要验证的假设
     * @param columns 可用数据列（快照里真实存在的列名）
     * @param previousEvidence 上轮评估依据（多轮时用于收敛；可为空）
     * @return 生成结果：{@code language}、{@code code}、{@code rationale}
     * @throws CodeGenerationException LLM 不可用/不可解析/自检不过
     */
    public Map<String, Object> generate(String language, String question, String hypothesis,
                                        List<String> columns, String previousEvidence) {
        // 2026-11-26 沙盒仅保留 Python 执行器：生成侧固定 Python，不再产出 Java 代码
        String lang = LANG_PYTHON;
        String raw = callLlm(buildSystemPrompt(lang), buildUserPrompt(
                question, hypothesis, columns, previousEvidence), 1400);
        String code = extractCode(raw);
        if (code.isBlank()) {
            throw new CodeGenerationException("LLM 未返回可识别的代码块");
        }
        // 生成后自检：不通过就早失败，别等沙盒拒（省一轮预算与时间）
        String problem = selfCheck(code, lang);
        if (problem != null) {
            throw new CodeGenerationException("生成代码自检未过：" + problem);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("language", lang);
        out.put("code", code);
        out.put("rationale", extractRationale(raw));
        return out;
    }

    // ── 提示词 ──────────────────────────────────────────────────────────────

    private static String buildSystemPrompt(String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是数据分析代码生成器。只输出代码与一句理由，不要解释过程。\n")
          .append("硬性约束（违反即失败）：\n")
          .append("1) 只能用语言标准库，禁止任何第三方包；\n")
          .append("2) 纯计算：禁止读写文件、禁止网络、禁止启动进程、禁止 eval/反射；\n")
          .append("3) 数据由变量 DATA 直接给出（已内联在代码里），不要再取数；\n")
          .append("4) 结果必须用一行 `").append(METRIC_CONTRACT)
          .append(" <数字>` 打印到标准输出，例如：").append(METRIC_CONTRACT).append(" 42.5\n")
          .append("5) 数字必须是真实计算得出，禁止写死常量冒充结果。\n");
        if (LANG_PYTHON.equals(lang)) {
            sb.append("语言：Python 3（标准库）。输出格式：先 ```python 代码块，再一行 `理由：...`。\n");
        } else {
            sb.append("语言：Java 17。必须有一个 public class Main 与 public static void main(String[] args)。")
              .append("输出格式：先 ```java 代码块，再一行 `理由：...`。\n");
        }
        return sb.toString();
    }

    private static String buildUserPrompt(String question, String hypothesis,
                                          List<String> columns, String previousEvidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("研究问题：").append(nullToEmpty(question)).append('\n');
        sb.append("本轮假设：").append(nullToEmpty(hypothesis)).append('\n');
        sb.append("数据列：").append(columns == null || columns.isEmpty()
                ? "（无，请用内置示例数据）" : String.join(", ", columns)).append('\n');
        if (previousEvidence != null && !previousEvidence.isBlank()) {
            sb.append("上轮结论：").append(previousEvidence).append('\n')
              .append("要求：本轮必须与上轮不同（换切分口径或换指标），禁止重复上轮做法。\n");
        }
        sb.append("请生成代码，用 ").append(METRIC_CONTRACT).append(" 打印一个可比较的数值指标。");
        return sb.toString();
    }

    // ── LLM 调用（与既有 ScenarioQuestionRunService 同一条通道） ──────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String callLlm(String systemPrompt, String userPrompt, int maxTokens) {
        RestTemplate restTemplate = llmProvider.getIfAvailable();
        if (restTemplate == null) {
            throw new CodeGenerationException("LLM 网关未装配（workspaceAipPipelineRest）");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        CallerCredentialSource.applyTo(headers);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);          // 必须显式传：空 model 上游直接 400
        body.put("messages", messages);
        body.put("temperature", 0.2);      // 代码生成要稳，不要发散
        body.put("maxTokens", maxTokens);

        ResponseEntity<Map> resp;
        try {
            resp = restTemplate.exchange("/llm/chat", HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);
        } catch (RuntimeException e) {
            throw new CodeGenerationException("对话网关不可用: " + e.getMessage());
        }
        Map<String, Object> envelope = resp.getBody();
        if (envelope == null) {
            throw new CodeGenerationException("对话网关应答为空");
        }
        Object code = envelope.get("code");
        if (code instanceof Number n && n.intValue() != 0) {
            throw new CodeGenerationException("对话网关业务错误: " + envelope.get("message"));
        }
        Object data = envelope.get("data");
        if (!(data instanceof Map<?, ?> d)) {
            throw new CodeGenerationException("对话网关应答缺 data 段");
        }
        Object content = d.get("content");
        return content == null ? "" : String.valueOf(content);
    }

    // ── 解析与自检 ──────────────────────────────────────────────────────────

    /** 从 ```lang ... ``` 围栏里取代码；无围栏则整体当作代码。 */
    static String extractCode(String raw) {
        if (raw == null) {
            return "";
        }
        int start = raw.indexOf("```");
        if (start < 0) {
            return raw.trim();
        }
        int nl = raw.indexOf('\n', start);
        if (nl < 0) {
            return "";
        }
        int end = raw.indexOf("```", nl);
        if (end < 0) {
            return "";
        }
        return raw.substring(nl + 1, end).trim();
    }

    /** 取代码块之后那句「理由：...」。 */
    static String extractRationale(String raw) {
        if (raw == null) {
            return "";
        }
        int idx = raw.lastIndexOf("理由");
        if (idx < 0) {
            return "";
        }
        String tail = raw.substring(idx).replace("理由：", "").replace("理由:", "").trim();
        int nl = tail.indexOf('\n');
        return (nl > 0 ? tail.substring(0, nl) : tail).trim();
    }

    /**
     * 生成后自检：返回问题描述，null 表示通过。
     *
     * <p>与沙盒侧静态规则<b>互补但不重复</b>：这里只做「结构合法性 + 指标契约」，
     * 安全类拦截由沙盒的 deny 规则负责（沙盒才是安全边界）。</p>
     */
    static String selfCheck(String code, String lang) {
        if (code == null || code.isBlank()) {
            return "代码为空";
        }
        if (!code.contains(METRIC_CONTRACT)) {
            return "缺少 `" + METRIC_CONTRACT + " <数字>` 输出（结果无法被评估）";
        }
        if (LANG_JAVA.equals(lang)) {
            if (!code.contains("class ")) {
                return "Java 代码缺少 class 定义";
            }
            if (!code.contains("static void main")) {
                return "Java 代码缺少 main 方法";
            }
        } else {
            if (!code.contains("print")) {
                return "Python 代码缺少 print 输出";
            }
        }
        return null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    /** 代码生成失败（LLM 不可用/不可解析/自检不过）。调用方据此显式降级。 */
    public static class CodeGenerationException extends RuntimeException {
        public CodeGenerationException(String message) {
            super(message);
        }
    }
}
