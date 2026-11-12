package com.chinacreator.gzcm.engine.ai.template;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 逻辑编排 {@code definition_json} 编解码与合并（从 Controller 抽出）。
 *
 * <p><b>为何独立成类</b>：①智能域异常卫生门禁（F06-15 / X-49）禁止控制器内出现
 * {@code catch (Exception|RuntimeException)}，而 JSON 编解码必然要吞受检异常；
 * ②DI-2 弱类型载荷棘轮只统计 Controller 内的 {@code Map<String,Object>} 行，
 * 定义编解码天然以 Map 为载体，放在 Controller 里会顶高基线。</p>
 *
 * <p>读：脏 JSON 返回空定义，不抛。写：序列化失败抛 {@link IllegalStateException}，交由
 * AiErrorAdvice 兜底。</p>
 */
public final class DefinitionJsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DefinitionJsonCodec() {
    }

    /**
     * 新建定义：{@code steps} 为既有必需键，{@code nodes}/{@code edges} 为画布扩展键（additive，非空才写）。
     */
    public static String build(Object steps, Object nodes, Object edges) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("steps", steps == null ? List.of() : steps);
        if (nodes != null) definition.put("nodes", nodes);
        if (edges != null) definition.put("edges", edges);
        return write(definition);
    }

    /** 更新定义：只覆盖出现的键，未出现的保持原值（对齐 withUpdated 语义）。 */
    public static String merge(String existingJson, Object steps, Object nodes, Object edges) {
        Map<String, Object> definition = new LinkedHashMap<>(read(existingJson));
        if (steps != null) definition.put("steps", steps);
        if (nodes != null) definition.put("nodes", nodes);
        if (edges != null) definition.put("edges", edges);
        return write(definition);
    }

    /** 读取 definition；脏 JSON 或空串返回空 Map（调用方按空定义出参）。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> read(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = MAPPER.readValue(json, Map.class);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 取定义中的 {@code steps}；缺键返回空列表。 */
    public static Object stepsOf(String json) {
        Object steps = read(json).get("steps");
        return steps == null ? List.of() : steps;
    }

    public static String write(Map<String, Object> definition) {
        try {
            return MAPPER.writeValueAsString(definition);
        } catch (Exception e) {
            throw new IllegalStateException("AIP-004: definition 序列化失败", e);
        }
    }
}

