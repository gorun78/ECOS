package com.chinacreator.gzcm.engine.ai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * F06-02（PMO-74 W144，分册 06 详细设计-06 §F06-02 要点 2）— 咽喉 6e 阶段的
 * <b>obligations 执行器</b>，在<b>工具结果回 LLM 之前</b>消费 security 裁决返回的
 * {@code mask[]} / {@code rowFilter} 义务。
 *
 * <p><b>铁律</b>（分册 01 SEC-02/03 契约、铁律 §2.4-3）：</p>
 * <ul>
 *   <li>{@code mask[]} — 全部经 {@link AiSecurityEngineClient#applyMasking}
 *       （{@code POST /api/v1/security/masking/apply}）执行；
 *       ai-engine 侧<b>只传待脱敏值 + 规则名</b>，<b>不实现任何脱敏算法</b>
 *       （本地正则/字符替换 0 命中，由 {@code ObligationsApplyTest#engineDoesNotSelfImplementMasking}
 *       源码 grep 门禁锁）。</li>
 *   <li>{@code rowFilter} — 行级过滤算法只存在于 security 侧（RLS 属 §2.4-1 空间）。
 *       ai-engine 侧不解析/不套用 whereClause：凡带 {@code rowFilter} 的裁决
 *       → 本执行器一律 <b>整条丢弃 + FAIL_CLOSED</b>（§2.4-6 宁可误拒不可误放），
 *       reason 携带义务原样摘要。</li>
 *   <li>security 不可用 / 响应不可解析 / 长度不一致 → 整条丢弃（
 *       {@link AiSecurityEngineClient.EngineUnavailableException} 或兜底 catch），
 *       <b>禁「先返回未脱敏再补一句说明」形态</b>（X-18 直接的翻案面）。</li>
 * </ul>
 *
 * <p><b>结果</b>为 {@link Outcome}：{@code success=true} →
 * {@link #rows} 是<b>脱敏后的</b>行字典，LLM 可见内容只看得到 masked；
 * {@code success=false} → 上游（{@code ToolExecutorService}）把该次工具结果替换为
 * {@code ToolResult.fail}，reason 明示"GUARDRAIL_SCALE / FAIL_CLOSED"。</p>
 *
 * <p><b>与 H8-T4 一致</b>：全 dec 决策走 security-engine，本执行器<b>不</b>独立调
 * {@link AiSecurityEngineClient#evaluate} —— 避免 I-1（一次 execute 恰好 1 次 ABAC）被破坏。</p>
 */
@Component
public class AgentToolObligationsApplier {

    private static final Logger log = LoggerFactory.getLogger(AgentToolObligationsApplier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiSecurityEngineClient security;

    public AgentToolObligationsApplier(AiSecurityEngineClient security) {
        this.security = security;
    }

    /** obligations 执行结果。{@code success=false} 时 {@code failClosedReason} 必填。 */
    public record Outcome(boolean success, List<Map<String, Object>> rows, String failClosedReason) {
        static Outcome ok(List<Map<String, Object>> rows, int originalRowCount) {
            return new Outcome(true, rows,
                    rows.size() < originalRowCount
                            ? "masked ok（部分行/字段被 security 判定需删除或删空，处理后 " + rows.size() + " 行）"
                            : "masked ok");
        }
        static Outcome failClosed(String reason) {
            return new Outcome(false, List.of(), reason);
        }
    }

    /**
     * 执行 obligations（在 LLM 看到结果<b>之前</b>）。
     *
     * @param obligations  ARBITER 返回的 obligations（可空/空 → no-op，返回原 rows 的视图）
     * @param rows         工具执行产出的行字典列表（通常是 SQL 查询结果；无 rows → 空 list）
     * @param toolName     工具名（仅日志）
     * @param userLabel    主体标识（仅日志；<b>不</b>进入 mask 请求 body）
     */
    public Outcome apply(List<Map<String, Object>> obligations,
                         List<Map<String, Object>> rows,
                         String toolName,
                         String userLabel) {
        // 快路径：无 obligations → no-op，直接放行原 rows（保留不变式：无义务即无成本）
        if (obligations == null || obligations.isEmpty()) {
            List<Map<String, Object>> view = rows == null ? List.of() : new ArrayList<>(rows);
            return Outcome.ok(view, view.size());
        }
        if (rows == null) rows = List.of();

        try {
            List<Map<String, Object>> current = new ArrayList<>(rows);
            for (Map<String, Object> ob : obligations) {
                if (ob == null || ob.isEmpty()) continue;
                String kind = normKind(ob);
                if ("mask".equalsIgnoreCase(kind)) {
                    current = applyMask(ob, current, toolName, userLabel);
                } else if ("rowfilter".equalsIgnoreCase(kind) || "row_filter".equalsIgnoreCase(kind)) {
                    // 行级过滤算法不在引擎侧（§2.4-1 RLS 统一走 security）。
                    // 本执行器不解析 whereClause、不按谓词筛行 —— 一律 FAIL_CLOSED 整条丢弃，
                    // 与「不入本地 SQL 求值器」的架构立场一致。
                    log.warn("F06-02 rowFilter obligation — engine-side 不实现行过滤算法 → 整条结果 FAIL_CLOSED 丢弃: tool={} user={} ob={}",
                            toolName, userLabel, summarize(ob),
                            new IllegalStateException("rowFilter enforced-only-via-security"));
                    return Outcome.failClosed(
                            "GUARDRAIL_FAIL_CLOSED: security 判定该工具结果带 rowFilter 义务，"
                                    + "ai-engine 侧不实现行过滤算法（铁律 §2.4-1），整条结果丢弃");
                } else {
                    // 未知 obligation 类型 → 保守 FAIL_CLOSED（防未知义务被忽略 = 绕过）
                    log.warn("F06-02 未知 obligation 类型 → FAIL_CLOSED 丢弃: tool={} user={} kind={} ob={}",
                            toolName, userLabel, kind, summarize(ob));
                    return Outcome.failClosed(
                            "GUARDRAIL_FAIL_CLOSED: 未识别的 obligation 类型 '" + kind + "'，"
                                    + "保守丢弃整条结果（防义务被静默忽略）");
                }
            }
            return Outcome.ok(current, rows.size());
        } catch (AiSecurityEngineClient.EngineUnavailableException e) {
            log.warn("F06-02 security masking 不可用/不可解析 → 整条结果 FAIL_CLOSED 丢弃: "
                    + "tool={} user={} reason={}", toolName, userLabel, e.getMessage(), e);
            return Outcome.failClosed("GUARDRAIL_FAIL_CLOSED: security masking 端点不可用/响应不可解析 → "
                    + "整条结果丢弃（禁「先返回未脱敏再说明」，铁律 §2.4-6）");
        } catch (Exception e) {
            log.warn("F06-02 obligations 执行异常 → 整条结果 FAIL_CLOSED 丢弃: tool={} user={}",
                    toolName, userLabel, e);
            return Outcome.failClosed("GUARDRAIL_FAIL_CLOSED: obligations 执行异常 → 整条结果丢弃");
        }
    }

    // ── mask 消费：值抽取 → security 端点 → 值回写 ────────────────────────

    private List<Map<String, Object>> applyMask(Map<String, Object> ob,
                                                 List<Map<String, Object>> rows,
                                                 String toolName, String userLabel) {
        // obligation 形态：{"kind":"mask","field":"email","rule":"email"} 或
        //                  {"kind":"mask","fields":[{"field":"email","rule":"email"},...]}
        LinkedHashMap<String, String> rules = extractMaskRules(ob);
        if (rules.isEmpty()) {
            log.warn("F06-02 mask 义务空缺 field/rule → 保守 FAIL_CLOSED: tool={} ob={}", toolName, summarize(ob));
            throw new AiSecurityEngineClient.EngineUnavailableException("mask obligation 缺 field/rule");
        }

        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (row == null || row.isEmpty()) continue;
            Map<String, Object> masked = new LinkedHashMap<>(row);
            for (Map.Entry<String, String> e : rules.entrySet()) {
                String field = e.getKey();
                Object v = masked.get(field);
                if (v == null) continue;
                masked.put(field, callSecurityMask(String.valueOf(v), e.getValue(), toolName, userLabel, field));
            }
            out.add(masked);
        }
        return out;
    }

    /**
     * 委托 security 端点脱敏<b>单值</b>。security 不可用 → 上抛
     * {@link AiSecurityEngineClient.EngineUnavailableException}（调用方丢弃整条）。
     */
    private String callSecurityMask(String raw, String rule,
                                    String toolName, String userLabel, String field) {
        try {
            List<String> masked = security.applyMasking(List.of(raw), List.of(rule));
            if (masked.size() != 1) {
                throw new AiSecurityEngineClient.EngineUnavailableException(
                        "masking 返回长度异常: got=" + masked.size());
            }
            return masked.get(0);
        } catch (AiSecurityEngineClient.EngineUnavailableException e) {
            log.debug("F06-02 security masking 不可用于 field={} rule={}（外层会 FAIL_CLOSED 整条）: tool={} user={}",
                    field, rule, toolName, userLabel);
            throw e;
        }
    }

    /** 从 obligation 变量抽取 {@code field → rule} 映射（LinkedHashSet 保持顺序）。 */
    private LinkedHashMap<String, String> extractMaskRules(Map<String, Object> ob) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        // 单键形态
        Object f = firstNonBlank(ob, "field", "fieldName", "column");
        Object r = firstNonBlank(ob, "rule", "ruleName", "maskRule");
        if (f != null && r != null) {
            out.put(String.valueOf(f), String.valueOf(r));
        }
        // 复数形态：fields / mask
        Object fieldsObj = ob.get("fields") != null ? ob.get("fields") : ob.get("mask");
        if (fieldsObj instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> m)) continue;
                Object kf = firstNonBlank(m, "field", "fieldName", "column");
                Object kr = firstNonBlank(m, "rule", "ruleName", "maskRule");
                if (kf != null && kr != null) {
                    out.put(String.valueOf(kf), String.valueOf(kr));
                }
            }
        }
        return out;
    }

    private static Object firstNonBlank(Map<?, ?> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof String s && !s.isBlank()) return s;
            if (v != null) {
                String s = String.valueOf(v);
                if (!s.isBlank()) return s;
            }
        }
        return null;
    }

    private static String normKind(Map<String, Object> ob) {
        Object k = ob.get("kind") != null ? ob.get("kind") : ob.get("type");
        return k == null ? "" : String.valueOf(k).toLowerCase(Locale.ROOT);
    }

    /** 日志摘要（<b>不带</b> field 值原文，防 masking 值泄漏到 log；仅取 kind 与字段名） */
    private static String summarize(Map<String, Object> ob) {
        try {
            Map<String, Object> brief = new LinkedHashMap<>();
            Object k = ob.get("kind") != null ? ob.get("kind") : ob.get("type");
            if (k != null) brief.put("kind", k);
            Object f = firstNonBlank(ob, "field", "fieldName", "column");
            if (f != null) brief.put("field", f);
            return MAPPER.writeValueAsString(brief);
        } catch (Exception e) {
            return "(unsummarizable)";
        }
    }
}
