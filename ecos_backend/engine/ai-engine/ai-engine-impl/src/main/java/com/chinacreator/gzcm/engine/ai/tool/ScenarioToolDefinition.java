package com.chinacreator.gzcm.engine.ai.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.engine.ai.service.ToolSchema;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 场景受控工具的<b>声明式</b>定义（详细设计-06 F06-11，PRD-06 §4.1 单源）。
 *
 * <p>本类型只是数据，<b>不含任何执行体</b>：X-12 的病灶是"注册即内置 Java lambda"使权限不可审计，
 * 因此场景工具的执行一律由咽喉 {@code ToolExecutorService} 按 {@code backendKind=REST} 分发，
 * 每次调用恰一条 security-engine 裁决（F06-01）。执行体内不存在可绕过裁决的分支。</p>
 *
 * <p>落地形态 = {@code ecos_ai.ecos_ai_tool_registry}（V200 建表，仅落脚本未实跑）；
 * 表未就位时由 {@link ScenarioToolCatalog} 的同源代码声明兜底，两者字段一一对应。</p>
 */
public record ScenarioToolDefinition(
        String toolKey,
        String name,
        String description,
        Map<String, ToolSchema.ParamDef> argsSchema,
        String backendKind,
        String backendService,
        String backendEndpoint,
        String httpMethod,
        String operation,
        String permissionHint,
        int timeoutMs,
        String resourceScopeProjection) {

    /** V200 {@code backend_kind} 唯一允许值：场景工具后端来源一律经 REST（F06-11 要点 2，禁 SQL 直查他引擎表）。 */
    public static final String BACKEND_KIND_REST = "REST";

    /** V200 {@code operation} 枚举（PRD-06 §1.3 read|write）。 */
    public static final String OPERATION_READ = "read";
    public static final String OPERATION_WRITE = "write";

    /** 咽喉侧 {@code TIMEOUT_SECONDS=30} 的硬上界，声明值不得超过。 */
    public static final int MAX_TIMEOUT_MS = 30_000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ScenarioToolDefinition {
        requireText(toolKey, "toolKey");
        requireText(name, "name");
        if (argsSchema == null || argsSchema.isEmpty()) {
            throw new IllegalArgumentException("[F06-11] 场景工具必须声明参数模式: " + toolKey);
        }
        if (!BACKEND_KIND_REST.equals(backendKind)) {
            // SQL/BUILTIN 分支即 X-13（LLM 生成 SQL 直查他引擎表）与 X-12（不可审计 lambda）的复发形态
            throw new IllegalArgumentException(
                    "[F06-11] 场景工具后端来源只允许 " + BACKEND_KIND_REST + "，实得 " + backendKind
                            + "（禁 SQL 直查他引擎表 / 禁内置执行体）: " + toolKey);
        }
        requireText(backendService, "backendService");
        requireText(backendEndpoint, "backendEndpoint");
        requireText(httpMethod, "httpMethod");
        if (!OPERATION_READ.equals(operation) && !OPERATION_WRITE.equals(operation)) {
            throw new IllegalArgumentException("[F06-11] operation 只允许 read|write，实得 " + operation
                    + "（裁决载荷 action.operation 依声明取，禁按参数猜测）: " + toolKey);
        }
        if (timeoutMs <= 0 || timeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("[F06-11] timeoutMs 必须在 (0, " + MAX_TIMEOUT_MS
                    + "] 内，实得 " + timeoutMs + "]: " + toolKey);
        }
        argsSchema = new LinkedHashMap<>(argsSchema);
    }

    public boolean isWrite() {
        return OPERATION_WRITE.equals(operation);
    }

    /** V200 {@code args_schema_json} 列文本（MC02：JSON 只用 TEXT，禁 jsonb）。 */
    public String argsSchemaJson() {
        try {
            return MAPPER.writeValueAsString(toParametersBlock());
        } catch (Exception e) {
            throw new IllegalStateException("场景工具参数模式序列化失败: " + toolKey, e);
        }
    }

    /** 生成 LLM function-calling 描述（与内置工具同形态，供目录面板使用）。 */
    public Map<String, Object> toFunctionCallSchema() {
        ToolSchema schema = new ToolSchema(toolKey, description == null ? "" : description);
        argsSchema.forEach(schema::addParam);
        return schema.toFunctionCallSchema();
    }

    /** 调用前参数地板校验：返回缺失的必填参数名（空列表即通过）。 */
    public List<String> missingRequiredArgs(Map<String, Object> arguments) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, ToolSchema.ParamDef> e : argsSchema.entrySet()) {
            if (e.getValue() == null || !e.getValue().isRequired()) continue;
            Object v = arguments == null ? null : arguments.get(e.getKey());
            if (v == null || (v instanceof String s && s.isBlank())) {
                missing.add(e.getKey());
            }
        }
        return missing;
    }

    /** 声明允许进 OPA 裁决载荷的入参字段（逗号分，与 V200 {@code resource_scope_projection} 同源）。 */
    public List<String> projectedFields() {
        List<String> fields = new ArrayList<>();
        if (resourceScopeProjection == null || resourceScopeProjection.isBlank()) return fields;
        for (String part : resourceScopeProjection.split(",")) {
            String s = part.trim();
            if (!s.isEmpty()) fields.add(s);
        }
        return fields;
    }

    /** 端点里的 {@code {param}} 占位符名（路径参数必须来自工具入参，禁拼接自由文本）。 */
    public List<String> pathPlaceholders() {
        List<String> names = new ArrayList<>();
        int i = backendEndpoint.indexOf('{');
        while (i >= 0) {
            int j = backendEndpoint.indexOf('}', i + 1);
            if (j < 0) break;
            String name = backendEndpoint.substring(i + 1, j).trim();
            if (!name.isEmpty()) names.add(name);
            i = backendEndpoint.indexOf('{', j);
        }
        return names;
    }

    private Map<String, Object> toParametersBlock() {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Map.Entry<String, ToolSchema.ParamDef> e : argsSchema.entrySet()) {
            ToolSchema.ParamDef def = e.getValue();
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", def.getType() == null ? "string" : def.getType());
            if (def.getDescription() != null) prop.put("description", def.getDescription());
            if (def.getEnumValues() != null && !def.getEnumValues().isEmpty()) {
                prop.put("enum", def.getEnumValues());
            }
            properties.put(e.getKey(), prop);
            if (def.isRequired()) required.add(e.getKey());
        }
        block.put("properties", properties);
        block.put("required", required);
        return block;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("[F06-11] 场景工具声明缺少必填字段 " + field);
        }
    }
}
