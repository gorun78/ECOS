package com.chinacreator.gzcm.engine.ai.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.service.ToolRegistry;
import com.chinacreator.gzcm.engine.ai.service.ToolSchema;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 工具管理端点 — 查看已注册工具、校验参数 Schema。
 *
 * <h3>端点</h3>
 * <ul>
 *   <li>{@code GET /api/v1/agent/tools} — 列出所有已注册工具</li>
 *   <li>{@code GET /api/v1/agent/tools/{name}/validate?args=...} — 校验参数是否符合 Schema</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/agent/tools")
public class AgentToolController {

    private final ToolRegistry toolRegistry;

    public AgentToolController(@Lazy ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 列出所有已注册工具。
     *
     * <pre>GET /api/v1/agent/tools</pre>
     *
     * @return 工具列表，每项包含 name, description, parameters, functionCallSchema
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAll() {
        List<ToolSchema> schemas = toolRegistry.listAll();
        List<Map<String, Object>> result = schemas.stream()
                .map(schema -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", schema.getName());
                    item.put("description", schema.getDescription());

                    // 扁平化参数为 list（前端更易用）
                    List<Map<String, Object>> params = new ArrayList<>();
                    if (schema.getParameters() != null) {
                        for (Map.Entry<String, ToolSchema.ParamDef> e : schema.getParameters().entrySet()) {
                            Map<String, Object> param = new LinkedHashMap<>();
                            param.put("name", e.getKey());
                            param.put("type", e.getValue().getType());
                            param.put("required", e.getValue().isRequired());
                            param.put("description", e.getValue().getDescription());
                            if (e.getValue().getDefaultValue() != null) {
                                param.put("default", e.getValue().getDefaultValue());
                            }
                            if (e.getValue().getEnumValues() != null) {
                                param.put("enum", e.getValue().getEnumValues());
                            }
                            params.add(param);
                        }
                    }
                    item.put("parameters", params);

                    // 附上完整的 function-calling schema
                    item.put("functionCallSchema", schema.toFunctionCallSchema());
                    return item;
                })
                .collect(Collectors.toList());

        return ApiResponse.success(result);
    }

    /**
     * 校验某工具的参数是否符合 Schema 定义。
     *
     * <pre>GET /api/v1/agent/tools/{name}/validate?args=...</pre>
     *
     * <h4>参数</h4>
     * <ul>
     *   <li>{@code name} (path) — 工具名称</li>
     *   <li>{@code args}  (query, JSON string) — 待校验的参数字符串，例如 {@code ?args={"sql":"SELECT 1"}}</li>
     * </ul>
     *
     * @param name 工具名称
     * @param args 待校验的参数 JSON
     * @return 校验结果：valid=true/false，附带缺失必填项和类型提示
     */
    @GetMapping("/{name}/validate")
    public ApiResponse<Map<String, Object>> validate(
            @PathVariable String name,
            @RequestParam(required = false, defaultValue = "{}") String args) {

        ToolSchema schema = toolRegistry.get(name);
        if (schema == null) {
            return ApiResponse.badRequest("工具未注册: " + name);
        }

        Map<String, Object> argMap;
        try {
            argMap = parseJson(args);
        } catch (Exception e) {
            return ApiResponse.badRequest("args 参数不是合法的 JSON: " + e.getMessage());
        }

        List<String> missingRequired = new ArrayList<>();
        List<Map<String, Object>> typeWarnings = new ArrayList<>();

        if (schema.getParameters() != null) {
            for (Map.Entry<String, ToolSchema.ParamDef> entry : schema.getParameters().entrySet()) {
                String paramName = entry.getKey();
                ToolSchema.ParamDef def = entry.getValue();
                Object value = argMap.get(paramName);

                // 必填检查
                if (def.isRequired() && (value == null || (value instanceof String s && s.isBlank()))) {
                    missingRequired.add(paramName);
                    continue;
                }

                // 类型检查（宽松）
                if (value != null && def.getType() != null) {
                    String typeMismatch = checkType(paramName, value, def.getType());
                    if (typeMismatch != null) {
                        Map<String, Object> warning = new LinkedHashMap<>();
                        warning.put("param", paramName);
                        warning.put("expected", def.getType());
                        warning.put("actual", value.getClass().getSimpleName().toLowerCase());
                        warning.put("message", typeMismatch);
                        typeWarnings.add(warning);
                    }
                }
            }
        }

        boolean valid = missingRequired.isEmpty();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", valid);
        result.put("toolName", name);
        if (!missingRequired.isEmpty()) {
            result.put("missingRequired", missingRequired);
        }
        if (!typeWarnings.isEmpty()) {
            result.put("typeWarnings", typeWarnings);
        }
        result.put("message", valid ? "参数校验通过" : "缺少必填参数: " + String.join(", ", missingRequired));

        return ApiResponse.success(result);
    }

    // ─── Helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String json) throws Exception {
        // 使用 Jackson（项目中已有 ObjectMapper）
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.readValue(json, Map.class);
    }

    /**
     * 宽松类型检查：只对明显不匹配的情况给出警告。
     */
    private static String checkType(String paramName, Object value, String expectedType) {
        if (expectedType == null) return null;

        switch (expectedType.toLowerCase()) {
            case "string":
                // 所有值都可 toString，不检查
                return null;
            case "number":
                if (value instanceof Number) return null;
                if (value instanceof String s) {
                    try {
                        Double.parseDouble(s);
                        return null;
                    } catch (NumberFormatException e) {
                        return "期望 number，实际为非数字字符串";
                    }
                }
                return "期望 number，实际为 " + value.getClass().getSimpleName().toLowerCase();
            case "boolean":
                if (value instanceof Boolean) return null;
                return "期望 boolean，实际为 " + value.getClass().getSimpleName().toLowerCase();
            case "array":
                if (value instanceof List) return null;
                return "期望 array，实际为 " + value.getClass().getSimpleName().toLowerCase();
            case "object":
                if (value instanceof Map) return null;
                return "期望 object，实际为 " + value.getClass().getSimpleName().toLowerCase();
            default:
                return null;
        }
    }
}
