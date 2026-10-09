package com.chinacreator.gzcm.engine.ai.security;

import com.chinacreator.gzcm.engine.ai.guardrail.ToolAdjudicationRequest;
import com.chinacreator.gzcm.engine.ai.guardrail.ToolObligations;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * obligations 执行器（详细设计-06 F06-02 要点 2）：在工具结果<b>回 LLM 之前</b>
 * 对 ALLOW 裁决返回的 {@code mask[]} / {@code rowFilter} 逐条落实。
 *
 * <p>铁律 §2.4-1/2/3 的硬约束：<b>算法只在 security 侧</b>。本类只做三件事 ——
 * 拆结果集、按列/按行调 security 端点、把 security 返回值写回结果结构。
 * 类内不得出现任何脱敏/行过滤算法（正则、字符替换、谓词求值），验收
 * {@code ObligationsApplyTest#engineDoesNotSelfImplementMasking}。</p>
 *
 * <p>fail-closed 取向（F06-02 要点 2「禁先返回再说明」）：</p>
 * <ul>
 *   <li>security 不可用 / 响应不合契约 → 整条结果丢弃，返回 {@link Outcome#failClosed}；</li>
 *   <li>结果文本不可解析成行结构而义务又确实要求处理 → 丢弃（不能假设"没有敏感列"）；</li>
 *   <li>行数超 {@link #MAX_FILTER_ROWS} → 丢弃（逐行裁决超预算时不得"抽样过滤后放行"）；</li>
 *   <li>{@code mask} 项缺规则名 → 丢弃（无规则名即无从调 security 脱敏端点）。</li>
 * </ul>
 */
@Component
public class ToolObligationApplier {

    private static final Logger log = LoggerFactory.getLogger(ToolObligationApplier.class);

    /** 逐行 security 裁决的行数上限；超限按 fail-closed 丢弃，禁抽样放行。 */
    public static final int MAX_FILTER_ROWS = 200;

    private final AiSecurityEngineClient securityClient;
    private final ObjectMapper objectMapper;

    public ToolObligationApplier(AiSecurityEngineClient securityClient, ObjectMapper objectMapper) {
        this.securityClient = securityClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行结果。
     *
     * @param applied    义务是否已落实（无义务时也为 true，json 原样返回）
     * @param json       落实义务后的结果文本；applied=false 时为 null（调用方必须丢弃）
     * @param failReason fail-closed 原因；applied=true 时为 null
     * @param filtered   行过滤后保留行数；未做行过滤时为 -1
     */
    public record Outcome(boolean applied, String json, String failReason, int filtered) {

        static Outcome passThrough(String json) {
            return new Outcome(true, json, null, -1);
        }

        static Outcome applied(String json, int filtered) {
            return new Outcome(true, json, null, filtered);
        }

        static Outcome failClosed(String reason) {
            return new Outcome(false, null, reason, -1);
        }
    }

    /**
     * 对工具结果施加 obligations。
     *
     * @param toolKey      工具标识（仅日志用）
     * @param resultJson   工具原始结果文本
     * @param obligations  ALLOW 裁决携带的义务
     * @param subject      服务端解析的裁决主体（行过滤需要 userRole，禁取入参自报值）
     */
    public Outcome apply(String toolKey, String resultJson, ToolObligations obligations,
                         ToolAdjudicationRequest.Subject subject) {
        if (resultJson == null || resultJson.isBlank()) {
            return Outcome.passThrough(resultJson);
        }
        if (obligations == null || obligations.isEmpty()) {
            return Outcome.passThrough(resultJson);
        }
        List<ToolObligations.Mask> masks = obligations.mask();
        String rowFilter = obligations.rowFilter();

        Object root;
        try {
            root = objectMapper.readValue(resultJson, Object.class);
        } catch (Exception e) {
            return Outcome.failClosed("结果不可解析为行结构，无法执行脱敏/行过滤义务");
        }
        List<Map<String, Object>> rows = asRows(root);
        if (rows == null) {
            return Outcome.failClosed("结果不含可过滤的行结构（对象数组/rows 数组）");
        }

        int kept = -1;
        try {
            if (rowFilter != null && !rowFilter.isBlank()) {
                if (rows.size() > MAX_FILTER_ROWS) {
                    return Outcome.failClosed(
                            "行数 " + rows.size() + " 超逐行裁决上限 " + MAX_FILTER_ROWS + "，丢弃结果");
                }
                rows = filterRows(toolKey, rows, rowFilter, subject);
                kept = rows.size();
            }
            for (ToolObligations.Mask mask : masks) {
                if (mask.rule() == null || mask.rule().isBlank()) {
                    return Outcome.failClosed("mask 义务缺少规则名，无法交 security 执行: column=" + mask.column());
                }
                maskColumn(toolKey, rows, mask);
            }
        } catch (Exception e) {
            log.warn("H10 obligations 执行失败（fail-closed，整条结果丢弃）: tool={} reason={}",
                    toolKey, e.getMessage(), e);
            return Outcome.failClosed("security obligations 执行不可用: " + e.getMessage());
        }

        Object rebuilt = rebuild(root, rows);
        try {
            return Outcome.applied(objectMapper.writeValueAsString(rebuilt), kept);
        } catch (Exception e) {
            return Outcome.failClosed("义务执行后结果重新序列化失败: " + e.getMessage());
        }
    }

    /** 逐行交 security evaluate-filter；不放行的行丢弃。 */
    private List<Map<String, Object>> filterRows(String toolKey, List<Map<String, Object>> rows,
                                                 String rowFilter,
                                                 ToolAdjudicationRequest.Subject subject) throws Exception {
        String userRole = subject != null && !subject.roles().isEmpty() ? subject.roles().get(0) : "viewer";
        List<Map<String, Object>> kept = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> verdict = securityClient.evaluateFilter(rowFilter, row, userRole);
            if (rowAllowed(verdict)) kept.add(row);
        }
        log.debug("H10 rowFilter 完成: tool={} before={} after={}", toolKey, rows.size(), kept.size());
        return kept;
    }

    /**
     * security evaluate-filter 放行判定：只认显式放行标记，缺字段一律视为不放行（fail-closed）。
     * 这里只做布尔读取，不做任何谓词求值 —— 谓词语义全在 security 侧。
     */
    private boolean rowAllowed(Map<String, Object> verdict) {
        if (verdict == null) return false;
        Object flag = verdict.get("allowed");
        if (flag == null) flag = verdict.get("pass");
        if (flag == null) flag = verdict.get("result");
        if (flag instanceof Boolean b) return b;
        if (flag instanceof Number n) return n.intValue() != 0;
        if (flag instanceof String s) {
            String v = s.trim();
            return "true".equalsIgnoreCase(v) || "ALLOW".equalsIgnoreCase(v) || "PASS".equalsIgnoreCase(v);
        }
        return false;
    }

    /** 单列脱敏：列值一次性交 security masking/apply，返回值按 index 写回。 */
    private void maskColumn(String toolKey, List<Map<String, Object>> rows,
                            ToolObligations.Mask mask) throws Exception {
        List<String> values = new ArrayList<>(rows.size());
        List<Integer> targets = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Object v = rows.get(i).get(mask.column());
            if (v == null) continue;
            values.add(String.valueOf(v));
            targets.add(i);
        }
        if (values.isEmpty()) return;

        List<Map<String, Object>> results = securityClient.applyMasking(values, List.of(mask.rule()));
        if (results.size() != values.size()) {
            throw new IllegalStateException(
                    "masking/apply 响应数量与请求不一致: request=" + values.size()
                            + " response=" + results.size());
        }
        for (Map<String, Object> item : results) {
            Object idxObj = item.get("index");
            if (!(idxObj instanceof Number n)) {
                throw new IllegalStateException("masking/apply 响应缺 index");
            }
            int pos = n.intValue();
            if (pos < 0 || pos >= targets.size()) {
                throw new IllegalStateException("masking/apply 响应 index 越界: " + pos);
            }
            Object masked = item.get("masked");
            if (masked == null) {
                throw new IllegalStateException("masking/apply 响应缺 masked 字段");
            }
            rows.get(targets.get(pos)).put(mask.column(), masked);
        }
        log.debug("H10 mask 完成: tool={} column={} rule={} cells={}",
                toolKey, mask.column(), mask.rule(), values.size());
    }

    /**
     * 把结果文本解释为行列表。
     *
     * @return 可原地修改的行列表；{@code null} = 结构不可解释（调用方 fail-closed）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asRows(Object root) {
        if (root instanceof Map<?, ?> map) {
            Map<String, Object> obj = (Map<String, Object>) map;
            Object rowsObj = obj.get("rows");
            if (rowsObj instanceof List<?> list) {
                return toRowList(list);
            }
            if (rowsObj == null) {
                // 单对象结果：视作一行，列级脱敏仍适用
                List<Map<String, Object>> single = new ArrayList<>(1);
                single.add(new LinkedHashMap<>(obj));
                return single;
            }
            return null;
        }
        if (root instanceof List<?> list) {
            return toRowList(list);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toRowList(List<?> list) {
        List<Map<String, Object>> rows = new ArrayList<>(list.size());
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                rows.add(new LinkedHashMap<>((Map<String, Object>) m));
            } else {
                // 非对象行（标量数组）无法按列脱敏/按行裁决
                return null;
            }
        }
        return rows;
    }

    /** 行列表写回原容器形态，保持 count 与 rows 的一致性。 */
    @SuppressWarnings("unchecked")
    private Object rebuild(Object root, List<Map<String, Object>> rows) {
        if (root instanceof Map<?, ?> map) {
            Map<String, Object> obj = new LinkedHashMap<>((Map<String, Object>) map);
            if (obj.containsKey("rows")) {
                obj.put("rows", rows);
                if (obj.containsKey("count")) obj.put("count", rows.size());
                return obj;
            }
            if (rows.size() == 1) return rows.get(0);
            return obj;
        }
        return rows;
    }
}
