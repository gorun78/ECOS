package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.CopilotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Copilot 服务实现 — 基于 LLM (DeepSeek/OpenAI) 的 AI 辅助。
 *
 * @author ECOS Pipeline 2.0 Team
 */
@Service
public class CopilotServiceImpl implements CopilotService {

    private static final Logger log = LoggerFactory.getLogger(CopilotServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final HttpClient httpClient;

    public CopilotServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    // ── LLM 调用配置 ──

    private Map<String, String> getLlmConfig() {
        Map<String, String> config = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT config_key, config_value FROM sys_config WHERE config_group = 'data-engine' AND config_key LIKE 'dw.copilot.%'");
            for (Map<String, Object> row : rows) {
                config.put((String) row.get("config_key"), (String) row.get("config_value"));
            }
        } catch (Exception e) {
            log.warn("读取 Copilot 配置失败: {}", e.getMessage());
        }

        config.putIfAbsent("dw.copilot.enabled", "false");
        config.putIfAbsent("dw.copilot.provider", "deepseek");
        config.putIfAbsent("dw.copilot.model", "deepseek-chat");
        config.putIfAbsent("dw.copilot.temperature", "0.2");
        config.putIfAbsent("dw.copilot.max_tokens", "4096");
        config.putIfAbsent("dw.copilot.default_prompt", "你是 ECOS 数据工程专家，擅长 SQL、Python 和 Pipeline 编排。");

        return config;
    }

    private String getApiKey() {
        // 优先从环境变量读取
        String key = System.getenv("DEEPSEEK_API_KEY");
        if (key != null && !key.isEmpty()) return key;
        key = System.getenv("OPENAI_API_KEY");
        if (key != null && !key.isEmpty()) return key;
        // fallback: 从配置表读取
        try {
            return jdbc.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = 'dw.copilot.api_key'", String.class);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 调用 LLM 完成补全。
     */
    private String callLlm(String systemPrompt, String userPrompt) {
        Map<String, String> config = getLlmConfig();
        if (!"true".equals(config.get("dw.copilot.enabled"))) {
            return "Copilot 未启用。请在数据工作台配置中开启 dw.copilot.enabled。";
        }

        String provider = config.get("dw.copilot.provider");
        String model = config.get("dw.copilot.model");
        String apiKey = getApiKey();
        double temperature = Double.parseDouble(config.get("dw.copilot.temperature"));
        int maxTokens = Integer.parseInt(config.get("dw.copilot.max_tokens"));

        if (apiKey == null || apiKey.isEmpty()) {
            return "API Key 未配置。请设置环境变量 DEEPSEEK_API_KEY 或 OPENAI_API_KEY。";
        }

        try {
            String apiUrl = switch (provider) {
                case "openai" -> "https://api.openai.com/v1/chat/completions";
                case "anthropic" -> "https://api.anthropic.com/v1/messages";
                default -> "https://api.deepseek.com/v1/chat/completions";
            };

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("temperature", temperature);
            body.put("max_tokens", maxTokens);

            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));
            body.put("messages", messages);

            String json = MAPPER.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> respBody = MAPPER.readValue(response.body(), Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) respBody.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                }
            }
            log.warn("LLM 调用返回: status={}, body={}", response.statusCode(), response.body());
            return "LLM 调用失败: HTTP " + response.statusCode();
        } catch (Exception e) {
            log.error("LLM 调用异常", e);
            return "LLM 调用异常: " + e.getMessage();
        }
    }

    // ── Copilot 功能 ──

    @Override
    public Map<String, Object> generateSql(String prompt, String schemaInfo) {
        String systemPrompt = getLlmConfig().get("dw.copilot.default_prompt")
            + "\n你需要根据用户的自然语言描述和表结构信息，生成正确的 SQL 查询。" +
            "\n只返回 SQL 代码，不要加额外解释。" +
            "\n数据库是 PostgreSQL。";

        String userPrompt = "自然语言描述: " + prompt;
        if (schemaInfo != null && !schemaInfo.isEmpty()) {
            userPrompt += "\n\n表结构信息:\n" + schemaInfo;
        }
        userPrompt += "\n\n请生成 SQL:";

        String sql = callLlm(systemPrompt, userPrompt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prompt", prompt);
        result.put("sql", sql.trim());
        result.put("dialect", "PostgreSQL");
        log.info("Copilot NL→SQL: prompt={}, sqlLen={}", truncate(prompt, 50), sql.length());
        return result;
    }

    @Override
    public Map<String, Object> generatePipeline(String description, String availableSources) {
        String systemPrompt = getLlmConfig().get("dw.copilot.default_prompt")
            + "\n你需要根据用户描述生成 Pipeline YAML DSL。格式遵循 ECOS Pipeline v2 规范。" +
            "\nYAML 必须包含: apiVersion, kind, metadata, spec (nodes + edges)。" +
            "\n节点类型: source, transform, aggregate, join, sink。";

        String userPrompt = "需求描述: " + description;
        if (availableSources != null && !availableSources.isEmpty()) {
            userPrompt += "\n\n可用数据源: " + availableSources;
        }
        userPrompt += "\n\n请生成 Pipeline YAML:";

        String yaml = callLlm(systemPrompt, userPrompt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("description", description);
        result.put("yaml", yaml.trim());
        log.info("Copilot NL→Pipeline: desc={}", truncate(description, 50));
        return result;
    }

    @Override
    public Map<String, Object> suggestExpression(String fieldName, String context) {
        String systemPrompt = getLlmConfig().get("dw.copilot.default_prompt")
            + "\n你是一个表达式建议专家。根据字段名和上下文，推荐最合适的 PB 函数。" +
            "\n可用函数类别: string, numeric, date_time, conditional, array, window, casting。" +
            "\n返回 3 个表达式建议，每行一个，格式: `function_name(column_name)  — 说明`";

        String userPrompt = "字段名: " + fieldName;
        if (context != null && !context.isEmpty()) {
            userPrompt += "\n上下文: " + context;
        }

        String suggestion = callLlm(systemPrompt, userPrompt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fieldName", fieldName);
        result.put("suggestions", suggestion.trim());
        log.info("Copilot 表达式建议: field={}", fieldName);
        return result;
    }

    @Override
    public Map<String, Object> generateUdf(String description, String language) {
        String lang = language != null ? language : "python";
        String systemPrompt = getLlmConfig().get("dw.copilot.default_prompt")
            + "\n你需要根据业务逻辑描述生成 " + lang.toUpperCase() + " UDF 代码。"
            + "\n函数签名: def transform(df: pd.DataFrame, params: dict = None) -> pd.DataFrame" +
            "\n只返回代码，不要加额外解释。";

        String userPrompt = "业务逻辑: " + description;
        String code = callLlm(systemPrompt, userPrompt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("description", description);
        result.put("language", lang);
        result.put("code", code.trim());
        log.info("Copilot NL→UDF: lang={}, desc={}", lang, truncate(description, 50));
        return result;
    }

    @Override
    public Map<String, Object> diagnose(String runId, String errorLog) {
        // 从 DB 获取 run 详情
        String errorMsg = errorLog;
        try {
            Map<String, Object> run = jdbc.queryForMap(
                "SELECT * FROM ecos_pipeline_run WHERE id = ?", runId);
            errorMsg = (String) run.getOrDefault("error_msg", "");
        } catch (Exception e) {
            // use provided errorLog
        }

        String systemPrompt = getLlmConfig().get("dw.copilot.default_prompt")
            + "\n你是一个 Pipeline 错误诊断专家。分析执行日志，找出根因并提供修复建议。";

        String userPrompt = "Pipeline 执行 ID: " + runId + "\n错误日志:\n" + errorMsg;
        String diagnosis = callLlm(systemPrompt, userPrompt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("diagnosis", diagnosis.trim());
        log.info("Copilot 错误诊断: runId={}", runId);
        return result;
    }

    private String truncate(String s, int len) {
        if (s == null) return "";
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }
}
