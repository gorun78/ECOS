package com.chinacreator.gzcm.engine.ai.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.hermes.HermesEngine;
import com.chinacreator.gzcm.runtime.hermes.scheduler.AgentResult;
import com.chinacreator.gzcm.aimod.SemanticQueryService;
import com.chinacreator.gzcm.aimod.ObjectQLParser;

/**
 * 自然语言查询 (NLQ) 控制器 — 将中文业务短语翻译为 ObjectQL 并执行。
 *
 * <h3>端点</h3>
 * <pre>
 * POST /api/query/nlq
 * Body: {"question": "华东高价值客户有哪些"}
 * Response: {
 *   "code": 0,
 *   "data": {
 *     "translated": "{\"entity\":\"Customer\",\"filter\":...}",
 *     "results": [{"id": "c1", "name": "某某公司", ...}]
 *   }
 * }
 * </pre>
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>接收自然语言问题</li>
 *   <li>调用 {@link SemanticQueryService#translate(String)} → ObjectQL JSON</li>
 *   <li>translate 返回 null → 调 Agent Runtime 解析（HermesEngine fallback）</li>
 *   <li>{@link ObjectQLParser#parse(String)} → SQL → JDBC 执行</li>
 *   <li>返回翻译结果 + 数据</li>
 * </ol>
 */
@RestController
@RequestMapping({"/api/v1/query", "/api/query"})
public class NLQController {

    private static final Logger log = LoggerFactory.getLogger(NLQController.class);

    private final JdbcTemplate jdbc;
    private final SemanticQueryService semanticQueryService;
    private final HermesEngine hermesEngine;

    public NLQController(JdbcTemplate jdbc, HermesEngine hermesEngine) {
        this.jdbc = jdbc;
        this.hermesEngine = hermesEngine;
        this.semanticQueryService = new SemanticQueryService();
    }

    /**
     * 自然语言查询。
     *
     * @param body {"question": "华东高价值客户"}
     * @return 翻译后的 ObjectQL + 查询结果
     */
    @PostMapping("/nlq")
    public ApiResponse<Map<String, Object>> nlq(@RequestBody Map<String, Object> body) {
        try {
            // 1. 提取问题
            Object questionObj = body.get("question");
            if (questionObj == null || questionObj.toString().trim().isEmpty()) {
                return ApiResponse.badRequest("缺少 question 字段");
            }
            String question = questionObj.toString().trim();
            log.info("NLQ 查询: {}", question);

            // 2. 语义翻译 → ObjectQL
            Map<String, Object> objectQL = semanticQueryService.translate(question);

            // 3. 词典无匹配 → Agent Runtime fallback（调用 HermesEngine）
            if (objectQL == null) {
                log.warn("NLQ 词典无匹配: {}，进入 Agent Runtime fallback", question);
                try {
                    AgentResult agentResult = hermesEngine.execute("sysman", "nlq-fallback", question);
                    Map<String, Object> fallbackResult = new LinkedHashMap<>();
                    if (agentResult.isSuccess()) {
                        log.info("Agent Runtime fallback 成功: session={}, duration={}ms",
                                agentResult.getSessionId(), agentResult.getDurationMs());
                        fallbackResult.put("translated", null);
                        fallbackResult.put("results", Collections.emptyList());
                        fallbackResult.put("agent_answer", agentResult.getContent());
                        fallbackResult.put("message", "词典无匹配，已通过 Agent Runtime 解析。");
                    } else {
                        log.warn("Agent Runtime fallback 失败: {}", agentResult.getErrorMsg());
                        fallbackResult.put("translated", null);
                        fallbackResult.put("results", Collections.emptyList());
                        fallbackResult.put("message", "词典无匹配，Agent Runtime 解析失败: " + agentResult.getErrorMsg());
                    }
                    return ApiResponse.success(fallbackResult);
                } catch (Exception e) {
                    log.error("Agent Runtime fallback 异常", e);
                    Map<String, Object> fallbackResult = new LinkedHashMap<>();
                    fallbackResult.put("translated", null);
                    fallbackResult.put("results", Collections.emptyList());
                    fallbackResult.put("message", "Agent Runtime 调用异常: " + e.getMessage());
                    return ApiResponse.success(fallbackResult);
                }
            }

            // 4. 规范化 ObjectQL 格式（复合 filter → filters 数组）
            Map<String, Object> normalizedQL = normalizeObjectQL(objectQL);
            log.info("NLQ normalized QL: {}", normalizedQL);
            String objectQLJson = toJson(normalizedQL);
            log.info("NLQ ObjectQL JSON: {}", objectQLJson);
            ObjectQLParser.ParsedQuery pq = ObjectQLParser.parse(objectQLJson);
            log.info("NLQ → ObjectQL: {} | SQL: {} | params={}", objectQLJson, pq.getSql(), pq.getParams());

            // 5. 执行 SQL
            List<Map<String, Object>> rows;
            try {
                if (pq.getParams().isEmpty()) {
                    rows = jdbc.queryForList(pq.getSql());
                } else {
                    rows = jdbc.queryForList(pq.getSql(), pq.getParamsArray());
                }
            } catch (Exception ex) {
                // 表不存在等场景优雅降级
                log.warn("NLQ SQL 执行失败: {} — {}", pq.getSql(), ex.getMessage());
                Map<String, Object> gracefulResult = new LinkedHashMap<>();
                gracefulResult.put("translated", objectQLJson);
                gracefulResult.put("results", Collections.emptyList());
                gracefulResult.put("message", "SQL 执行失败（表可能不存在）: " + ex.getMessage());
                return ApiResponse.success(gracefulResult);
            }

            // 6. 字段名转小写
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    m.put(e.getKey() != null ? e.getKey().toLowerCase() : null, e.getValue());
                }
                normalized.add(m);
            }

            // 7. 组装响应
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("translated", objectQLJson);
            result.put("results", normalized);
            return ApiResponse.success(result);

        } catch (IllegalArgumentException e) {
            log.warn("NLQ 参数错误: {}", e.getMessage());
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("NLQ 查询失败", e);
            return ApiResponse.internalError("查询执行失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════
    // 工具方法 — ObjectQL 格式规范化
    // ═══════════════════════════════════════════════════════

    /**
     * 将 SemanticQueryService 的复合 filter 格式转换为 ObjectQLParser 支持的格式。
     * <ul>
     *   <li>SemanticQueryService 生成: {"entity":..., "filter":{"operator":"AND","conditions":[...]}}</li>
     *   <li>ObjectQLParser 需要:   {"entity":..., "filters":[...]}</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeObjectQL(Map<String, Object> ql) {
        // 检查 filter 是否为复合格式 (operator + conditions)
        Object filter = ql.get("filter");
        if (filter instanceof Map) {
            Map<String, Object> fMap = (Map<String, Object>) filter;
            if (fMap.containsKey("operator") && fMap.containsKey("conditions")) {
                Map<String, Object> result = new LinkedHashMap<>(ql);
                result.remove("filter");
                result.put("filters", fMap.get("conditions"));
                return result;
            }
        }

        // 跨 entity 的复合查询 — 取第一个子查询
        if (ql.containsKey("composite") && ql.containsKey("queries")) {
            Object queries = ql.get("queries");
            if (queries instanceof List && !((List<?>) queries).isEmpty()) {
                return normalizeObjectQL((Map<String, Object>) ((List<?>) queries).get(0));
            }
        }

        return ql;
    }

    // ═══════════════════════════════════════════════════════
    // 工具方法 — Map → JSON 字符串
    // ═══════════════════════════════════════════════════════

    /**
     * 将 Map 序列化为 JSON 字符串（无外部依赖）。
     */
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("\"").append(entry.getKey()).append("\": ");
            appendValue(sb, entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append("\"").append(escapeJson((String) value)).append("\"");
        } else if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            sb.append(toJson((Map<String, Object>) value));
        } else if (value instanceof List) {
            sb.append("[");
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) sb.append(", ");
                appendValue(sb, item);
                first = false;
            }
            sb.append("]");
        } else {
            sb.append("\"").append(escapeJson(value.toString())).append("\"");
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
