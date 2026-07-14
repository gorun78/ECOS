package com.chinacreator.gzcm.dccheng.classification.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.runtime.hermes.HermesEngine;
import com.chinacreator.gzcm.runtime.hermes.scheduler.AgentResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 数据分类控制器 — Agent 自动分级。
 *
 * <h3>端点：</h3>
 * <ol>
 *   <li>POST /api/catalog/assets/{assetId}/auto-classify — Agent自动识别敏感字段并建议分级</li>
 * </ol>
 *
 * <p>调用 HermesEngine 让 AI 扫描字段名和样本数据，识别身份证/手机号/邮箱/银行卡/地址等敏感信息，
 * 返回分级建议和识别标签。</p>
 */
@RestController
@RequestMapping("/api/catalog")
public class ClassificationController {

    private static final Logger log = LoggerFactory.getLogger(ClassificationController.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired(required = false)
    private HermesEngine hermesEngine;

    /** 系统提示词 — 数据分类专家 */
    private static final String SYSTEM_PROMPT =
        "你是数据分类专家。扫描以下字段名和样本数据，识别敏感信息类型："
        + "身份证/手机号/邮箱/银行卡/地址/统一社会信用代码。"
        + "输出纯JSON（不要markdown代码块）："
        + "{\"level\":\"敏感等级\",\"tags\":[\"识别到的类型\"],\"summary\":\"一句话总结\"}。"
        + "敏感等级必须是：公开|内部|敏感|机密|绝密。";

    /**
     * POST /api/catalog/assets/{assetId}/auto-classify
     *
     * <p>Agent 自动识别 PII 字段并建议分级。</p>
     *
     * <p>返回示例：
     * <pre>
     * {
     *   "level": "敏感",
     *   "tags": ["身份证", "手机号", "地址"],
     *   "summary": "识别到3类敏感字段，建议标记为敏感级",
     *   "assetId": "ds-001"
     * }
     * </pre>
     */
    @PostMapping("/assets/{assetId}/auto-classify")
    public ApiResponse<Map<String, Object>> autoClassify(@PathVariable String assetId,
                                                          @RequestBody(required = false) Map<String, Object> body) {
        if (hermesEngine == null) {
            return ApiResponse.internalError("Hermes 引擎未就绪，请确认 Agent Runtime 已启动");
        }

        // 构造扫描 prompt
        String fieldsJson = buildFieldsJson(assetId, body);
        String prompt = "请扫描以下数据资产的字段信息，识别敏感字段：\n\n"
                + "资产ID: " + assetId + "\n"
                + "字段列表:\n" + fieldsJson + "\n\n"
                + "请分析每个字段是否包含敏感信息，输出JSON。";

        log.info("Auto-classify agent invoked for asset: {}", assetId);

        try {
            AgentResult result = hermesEngine.execute("sysman", "sysman-assistant", prompt);

            if (!result.isSuccess()) {
                log.warn("Agent classify failed: {}", result.getErrorMsg());
                // 返回基础退化结果
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("assetId", assetId);
                fallback.put("level", "内部");
                fallback.put("tags", List.of());
                fallback.put("summary", "Agent 调用失败，降级为默认内部级: " + result.getErrorMsg());
                fallback.put("fallback", true);
                return ApiResponse.success(fallback);
            }

            // 解析 Agent 返回的 JSON
            String content = result.getContent();
            Map<String, Object> parsed = parseAgentResponse(content, assetId);

            // 如果解析结果是包装对象（含 fields 数组），从 fields 合成结果
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> extractedFields = extractFieldsList(parsed);
            if (extractedFields != null) {
                Map<String, Object> synthesized = synthesizeFromArray(extractedFields);
                parsed.put("level", synthesized.get("level"));
                parsed.put("tags", synthesized.get("tags"));
                parsed.put("summary", synthesized.get("summary"));
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("assetId", assetId);
            resp.put("level", parsed.getOrDefault("level", "内部"));
            resp.put("tags", parsed.getOrDefault("tags", List.of()));
            resp.put("summary", parsed.getOrDefault("summary", "Agent 分析完成"));
            resp.put("rawContent", content);
            resp.put("tokensInput", result.getTokensInput());
            resp.put("tokensOutput", result.getTokensOutput());
            resp.put("durationMs", result.getDurationMs());

            return ApiResponse.success(resp);

        } catch (Exception e) {
            log.error("Auto-classify 执行异常: assetId={}", assetId, e);
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("assetId", assetId);
            error.put("error", e.getMessage());
            return ApiResponse.internalError("自动分类执行异常: " + e.getMessage());
        }
    }

    // ═══════════════ 工具方法 ═══════════════════════════

    /** 构造字段列表 JSON（可用于对接实际元数据服务） */
    private String buildFieldsJson(String assetId, Map<String, Object> body) {
        // 如果有自定义字段传入，直接使用
        if (body != null && body.containsKey("fields")) {
            try {
                return mapper.writeValueAsString(body.get("fields"));
            } catch (Exception e) {
                log.warn("序列化自定义字段失败", e);
            }
        }

        // 默认 mock 字段列表 — 正式环境对接 MetadataService
        List<Map<String, String>> mockFields = List.of(
            Map.of("name", "user_name", "type", "VARCHAR(50)", "sample", "张三"),
            Map.of("name", "id_card", "type", "VARCHAR(18)", "sample", "430102199001011234"),
            Map.of("name", "phone", "type", "VARCHAR(11)", "sample", "13800138000"),
            Map.of("name", "email", "type", "VARCHAR(100)", "sample", "zhangsan@example.com"),
            Map.of("name", "bank_card", "type", "VARCHAR(19)", "sample", "6222021234567890123"),
            Map.of("name", "address", "type", "VARCHAR(200)", "sample", "湖南省长沙市岳麓区xxx路xxx号"),
            Map.of("name", "company_code", "type", "VARCHAR(18)", "sample", "91430100MA4LXXXXX"),
            Map.of("name", "department", "type", "VARCHAR(50)", "sample", "技术研发部"),
            Map.of("name", "salary", "type", "DECIMAL(10,2)", "sample", "15000.00"),
            Map.of("name", "created_at", "type", "DATETIME", "sample", "2025-01-15 10:30:00")
        );

        try {
            return mapper.writeValueAsString(mockFields);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 解析 Agent 返回的 JSON，去除可能的 markdown 代码块包裹 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAgentResponse(String content, String assetId) {
        if (content == null || content.isBlank()) {
            return Map.of("level", "内部", "tags", List.of(), "summary", "Agent 返回空内容");
        }

        // 提取 JSON：找到第一个 { 或 [ 到最后一个 } 或 ]
        String s = content.trim();
        int braceStart = s.indexOf('{');
        int bracketStart = s.indexOf('[');
        int jsonStart = (braceStart >= 0 && (bracketStart < 0 || braceStart < bracketStart))
                ? braceStart : bracketStart;
        if (jsonStart < 0) {
            return fallbackResult(content);
        }

        int braceEnd = s.lastIndexOf('}');
        int bracketEnd = s.lastIndexOf(']');
        int jsonEnd = Math.max(braceEnd, bracketEnd);
        if (jsonEnd <= jsonStart) {
            return fallbackResult(content);
        }

        String json = s.substring(jsonStart, jsonEnd + 1).trim();

        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e1) {
            try {
                List<Map<String, Object>> arr = mapper.readValue(json,
                        new TypeReference<List<Map<String, Object>>>() {});
                return synthesizeFromArray(arr);
            } catch (Exception e2) {
                log.warn("Agent JSON parse failed: json={}", json.length() > 200 ? json.substring(0, 200) : json);
                return fallbackResult(content);
            }
        }
    }

    private Map<String, Object> fallbackResult(String content) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("level", "内部");
        fallback.put("tags", List.of());
        fallback.put("summary", content.length() > 100 ? content.substring(0, 100) + "..." : content);
        return fallback;
    }

    /** 从 Agent 返回的数组格式合成 {level, tags, summary} */
    private Map<String, Object> synthesizeFromArray(List<Map<String, Object>> arr) {
        List<String> sensitiveFields = new ArrayList<>();
        List<String> allTags = new ArrayList<>();

        for (Map<String, Object> item : arr) {
            // 兼容 "sensitive" / "is_sensitive" / "isSensitive"
            Object sensitiveObj = item.get("sensitive");
            if (sensitiveObj == null) sensitiveObj = item.get("is_sensitive");
            if (sensitiveObj == null) sensitiveObj = item.get("isSensitive");
            boolean isSensitive = sensitiveObj instanceof Boolean ? (Boolean) sensitiveObj : false;

            if (isSensitive) {
                // 兼容 "name" / "field" / "field_name" / "fieldName"
                String fieldName = String.valueOf(item.getOrDefault("name",
                        item.getOrDefault("field",
                        item.getOrDefault("field_name", item.getOrDefault("fieldName", "?")))));
                sensitiveFields.add(fieldName);
                String reason = String.valueOf(item.getOrDefault("reason", ""));
                // 从 reason 提取类型标签
                if (reason.contains("身份证")) allTags.add("身份证");
                if (reason.contains("手机号") || reason.contains("手机")) allTags.add("手机号");
                if (reason.contains("邮箱") || reason.contains("邮件")) allTags.add("邮箱");
                if (reason.contains("银行卡") || reason.contains("银行")) allTags.add("银行卡");
                if (reason.contains("地址")) allTags.add("地址");
                if (reason.contains("姓名") || reason.contains("个人身份")) allTags.add("个人信息");
                if (reason.contains("统一社会信用代码")) allTags.add("统一社会信用代码");
            }
        }

        List<String> uniqueTags = allTags.stream().distinct().collect(Collectors.toList());
        if (uniqueTags.isEmpty() && !sensitiveFields.isEmpty()) {
            uniqueTags = List.of("敏感信息");
        }

        String level;
        int count = sensitiveFields.size();
        if (count >= 5) level = "绝密";
        else if (count >= 3) level = "机密";
        else if (count >= 2) level = "敏感";
        else if (count >= 1) level = "内部";
        else level = "公开";

        String summary = sensitiveFields.isEmpty()
                ? "未识别到敏感字段"
                : "识别到 " + count + " 个敏感字段: " + String.join(", ", sensitiveFields);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", level);
        result.put("tags", uniqueTags);
        result.put("summary", summary);
        result.put("sensitiveFields", sensitiveFields);
        result.put("totalFields", arr.size());
        return result;
    }

    /** 递归从 Map 中提取 fields 列表（兼容各种包装格式） */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractFieldsList(Map<String, Object> map) {
        // 1. 直接包含 fields / sensitive_fields / analysis 键
        if (map.containsKey("fields") && map.get("fields") instanceof List) {
            return (List<Map<String, Object>>) map.get("fields");
        }
        if (map.containsKey("sensitive_fields") && map.get("sensitive_fields") instanceof List) {
            return (List<Map<String, Object>>) map.get("sensitive_fields");
        }
        if (map.containsKey("analysis") && map.get("analysis") instanceof List) {
            return (List<Map<String, Object>>) map.get("analysis");
        }
        // 3. 扁平格式：{"user_name": true, "id_card": true, ...} 
        //    检测：所有值都是 Boolean 类型
        boolean allBoolean = !map.isEmpty();
        for (Object v : map.values()) {
            if (!(v instanceof Boolean)) { allBoolean = false; break; }
        }
        if (allBoolean) {
            List<Map<String, Object>> fields = new ArrayList<>();
            for (Map.Entry<String, Object> e : map.entrySet()) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("name", e.getKey());
                field.put("sensitive", e.getValue());
                fields.add(field);
            }
            return fields;
        }
        // 4. 递归搜索嵌套对象（如 {"assetId": {"fields": [...]}}）
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof Map) {
                List<Map<String, Object>> found = extractFieldsList((Map<String, Object>) entry.getValue());
                if (found != null) return found;
            }
        }
        return null;
    }
}
