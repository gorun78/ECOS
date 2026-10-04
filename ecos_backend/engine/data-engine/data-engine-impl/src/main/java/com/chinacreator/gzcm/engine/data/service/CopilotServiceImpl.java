package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.CopilotService;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatMessage;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatRequest;
import com.chinacreator.gzcm.runtime.llm.gateway.ChatResponse;
import com.chinacreator.gzcm.runtime.llm.gateway.LLMGateway;
import com.chinacreator.gzcm.sysman.config.service.impl.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copilot 服务实现 — LLM 调用统一收敛 runtime/llm-gateway (铁律 §2.5)。
 *
 * <p>PMO-74.3 T2: 删除自持 HttpClient 与厂商 URL；provider/model 经 sysman
 * SysConfigService 门面读 sys_config 分组 (dw.copilot.* / config_group='data-engine')；
 * api-key 不再落 sys_config 明文，改环境变量注入链 (DEEPSEEK_API_KEY → llm.deepseek.api-key)。</p>
 *
 * @author ECOS Pipeline 2.0 Team
 */
@Service
public class CopilotServiceImpl implements CopilotService {

    private static final Logger log = LoggerFactory.getLogger(CopilotServiceImpl.class);

    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final String DEFAULT_PROMPT = "你是 ECOS 数据工程专家，擅长 SQL、Python 和 Pipeline 编排。";

    private final JdbcTemplate jdbc;

    /** llm-gateway 统一出口；未装配时 Copilot 显式降级不裸调厂商 API */
    @Autowired(required = false)
    private LLMGateway llmGateway;

    /** sysman 配置门面 (config_group 单表分组)；可选注入，缺 bean 时走代码默认值 */
    @Autowired(required = false)
    private SysConfigService sysConfigService;

    public CopilotServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── LLM 配置读取 (sys_config 分组 data-engine, 经 sysman 门面) ──

    private String cfg(String key, String defaultValue) {
        if (sysConfigService != null) {
            try {
                String val = sysConfigService.getString(key);
                if (val != null && !val.isBlank()) return val;
            } catch (Exception e) {
                log.warn("读取 Copilot 配置失败 {}: {}", key, e.getMessage());
            }
        }
        return defaultValue;
    }

    private double cfgDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(cfg(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int cfgInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(cfg(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 调用 LLM 完成补全 — 经 llm-gateway，provider 由网关按 model 归一。
     */
    private String callLlm(String systemPrompt, String userPrompt) {
        if (!"true".equalsIgnoreCase(cfg("dw.copilot.enabled", "false"))) {
            return "Copilot 未启用。请在数据工作台配置中开启 dw.copilot.enabled。";
        }
        if (llmGateway == null) {
            log.warn("Copilot callLlm: llm-gateway 未装配, 拒绝直调厂商 API");
            return "LLM 网关不可用：llm-gateway 未装配。";
        }

        String model = cfg("dw.copilot.model", DEFAULT_MODEL);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", systemPrompt));
        messages.add(new ChatMessage("user", userPrompt));

        // F06-05 要点 3（X-23）：引擎不再持 api-key，亦不再 request.setApiKey——
        // llm-gateway 经 SecurityEngineBridge 按 apiKeyRef 服务端解密（缺配即 fail-closed DENY）
        ChatRequest request = new ChatRequest(model, messages,
                cfgDouble("dw.copilot.temperature", 0.2),
                cfgInt("dw.copilot.max_tokens", 4096),
                false);

        try {
            ChatResponse response = llmGateway.call(request);
            if (response != null && response.isSuccess()) {
                return response.getContent() != null ? response.getContent() : "";
            }
            String err = response != null ? response.getErrorMsg() : "null response";
            log.warn("Copilot LLM 调用失败: {}", err);
            return "LLM 调用失败: " + err;
        } catch (Exception e) {
            log.error("Copilot LLM 调用异常", e);
            return "LLM 调用异常: " + e.getMessage();
        }
    }

    // ── Copilot 功能 ──

    @Override
    public Map<String, Object> generateSql(String prompt, String schemaInfo) {
        String systemPrompt = cfg("dw.copilot.default_prompt", DEFAULT_PROMPT)
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
        String systemPrompt = cfg("dw.copilot.default_prompt", DEFAULT_PROMPT)
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
        String systemPrompt = cfg("dw.copilot.default_prompt", DEFAULT_PROMPT)
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
        String systemPrompt = cfg("dw.copilot.default_prompt", DEFAULT_PROMPT)
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

        String systemPrompt = cfg("dw.copilot.default_prompt", DEFAULT_PROMPT)
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
