package com.chinacreator.gzcm.engine.cognitive2.dto;

import com.chinacreator.gzcm.engine.cognitive2.model.CausalChainNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 认知计划视图（{@code POST /api/v1/cognitive/plan} 与 {@code GET /plan/{id}} 共用）。
 *
 * <p>键面与顺序：{@code planId/goal/context/domain/parameters/chain/rootCause/suggestions/
 * status/createdAt}。</p>
 *
 * <p><b>两处显式豁免（不冻结）</b>：</p>
 * <ul>
 *   <li>{@code parameters}：调用方任意参数包，原样回显（旧实现 {@code params != null ? params : Map.of()}）；</li>
 *   <li>{@code chain[]}：元素<b>双形</b>——推理器返回的节点经原 {@code ObjectMapper.convertValue(…, Map)}
 *       展开（字段集随 {@link CausalChainNode} 演进），链长不足时以 4 键补层
 *       {@code depth/node/confidence/source}。机械 record 化会改 wire，按 #147DI 同口径保留 {@code Object}。</li>
 * </ul>
 * <p>{@code suggestions[]} 为固定 2 键（{@code index/suggestion}），强类型。</p>
 */
public record CognitivePlanVO(
        String planId,
        String goal,
        String context,
        String domain,
        Object parameters,
        List<Object> chain,
        String rootCause,
        List<Suggestion> suggestions,
        String status,
        String createdAt) {

    /** 计划建议条目：{@code index + suggestion}。 */
    public record Suggestion(int index, String suggestion) {
    }

    /** 参数优化建议：{@code parameter/currentValue/suggestedValue/reason}。 */
    public record OptimizeSuggestion(
            String parameter,
            Object currentValue,
            Object suggestedValue,
            String reason) {
    }

    /**
     * 推理节点 → wire 元素（旧实现按 {@code Map} 展开；失败降级为 {@code {node: 文本}}）。
     */
    public static Object toChainNode(Object rawNode, ObjectMapper mapper) {
        try {
            return mapper.convertValue(rawNode, Object.class);
        } catch (Exception e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("node", String.valueOf(rawNode));
            return m;
        }
    }

    /** 契约兜底节点：4 键、固定顺序（depth/node/confidence/source）。 */
    public static Object fallbackNode(int depth, String node, double confidence, String source) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("depth", depth);
        m.put("node", node);
        m.put("confidence", confidence);
        m.put("source", source);
        return m;
    }

    /** 建议字符串 → 2 键对象数组（避免直接序列化裸字符串列表）。 */
    public static List<Suggestion> suggestionsOf(List<String> list) {
        List<Suggestion> out = new ArrayList<>();
        int i = 0;
        for (String x : list) {
            out.add(new Suggestion(i, x));
            i += 1;
        }
        return out;
    }
}
