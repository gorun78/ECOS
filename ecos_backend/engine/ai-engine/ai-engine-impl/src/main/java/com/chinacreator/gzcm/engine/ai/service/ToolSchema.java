package com.chinacreator.gzcm.engine.ai.service;

import java.util.*;

/**
 * 工具 Schema 定义 — 描述工具的名称、参数和描述。
 * <p>
 * 通过 {@link #toFunctionCallSchema()} 生成兼容 OpenAI / DeepSeek
 * function-calling 的 JSON Schema 格式。
 * </p>
 */
public class ToolSchema {

    private String name;
    private String description;
    private Map<String, ParamDef> parameters;

    public ToolSchema() {
        this.parameters = new LinkedHashMap<>();
    }

    public ToolSchema(String name, String description) {
        this();
        this.name = name;
        this.description = description;
    }

    // ─── Getters / Setters ──────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, ParamDef> getParameters() { return parameters; }
    public void setParameters(Map<String, ParamDef> parameters) { this.parameters = parameters; }

    /**
     * 添加一个参数定义。
     */
    public ToolSchema addParam(String name, ParamDef def) {
        if (this.parameters == null) {
            this.parameters = new LinkedHashMap<>();
        }
        this.parameters.put(name, def);
        return this;
    }

    // ─── Function-calling schema ────────────────────────────────────────

    /**
     * 生成 OpenAI / DeepSeek function-calling 兼容的 JSON Schema。
     * <pre>{@code
     * {
     *   "name": "query_db",
     *   "description": "查询数据库",
     *   "parameters": {
     *     "type": "object",
     *     "properties": {
     *       "sql": { "type": "string", "description": "SQL 语句" },
     *       "params": { "type": "array", "description": "绑定参数" }
     *     },
     *     "required": ["sql"]
     *   }
     * }
     * }</pre>
     */
    public Map<String, Object> toFunctionCallSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", name);
        schema.put("description", description != null ? description : "");

        Map<String, Object> paramsBlock = new LinkedHashMap<>();
        paramsBlock.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        if (parameters != null && !parameters.isEmpty()) {
            for (Map.Entry<String, ParamDef> entry : parameters.entrySet()) {
                String paramName = entry.getKey();
                ParamDef def = entry.getValue();

                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", def.type != null ? def.type : "string");
                if (def.description != null && !def.description.isEmpty()) {
                    prop.put("description", def.description);
                }
                if (def.defaultValue != null) {
                    prop.put("default", def.defaultValue);
                }
                if (def.enumValues != null && !def.enumValues.isEmpty()) {
                    prop.put("enum", def.enumValues);
                }
                properties.put(paramName, prop);

                if (def.required) {
                    required.add(paramName);
                }
            }
        }

        paramsBlock.put("properties", properties);
        if (!required.isEmpty()) {
            paramsBlock.put("required", required);
        }

        schema.put("parameters", paramsBlock);
        return schema;
    }

    // ─── Inner class: ParamDef ─────────────────────────────────────────

    /**
     * 单个参数定义。
     */
    public static class ParamDef {
        /** JSON Schema 类型（string, number, boolean, array, object） */
        private String type;
        /** 是否必填 */
        private boolean required;
        /** 默认值 */
        private Object defaultValue;
        /** 参数描述 */
        private String description;
        /** 枚举值的可选列表（仅 string 类型有效） */
        private List<String> enumValues;

        public ParamDef() {}

        public ParamDef(String type, boolean required, String description) {
            this.type = type;
            this.required = required;
            this.description = description;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }

        public Object getDefaultValue() { return defaultValue; }
        public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<String> getEnumValues() { return enumValues; }
        public void setEnumValues(List<String> enumValues) { this.enumValues = enumValues; }
    }
}
