package com.chinacreator.gzcm.engine.security.decision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 详细设计-01 C.1 [3] / D 章 — 三通道唯一裁决返回体（一次 {@code decide} 拿全）。
 *
 * <p>结构（D 章 {@code DecisionBundle}）：</p>
 * <pre>{@code
 * {
 *   rls:     {predicateTemplate, bindings[], combine, denyIfEmpty, policyRefs[]},
 *   columns: {mode: allow|deny, names: [...], policyRefs[]},
 *   masks:   [{column, strategy}],
 *   opa:     {allow, policyRef, obligations[]},
 *   decisionId, traceId
 * }
 * }</pre>
 *
 * <p>{@code policyRefs[]}（#142/B4-3b 只增）= 该源在本主体下<b>实际命中</b>的策略名清单，
 * 是导出件「权限范围」与「水印」字段的唯一合法出处；旧形态只有谓词没有出处，
 * 消费方只能写常量自证（差距文档 D-B4-10/D-B4-11）。</p>
 *
 * <p>结构性意义：把 RLS/CLS/mask/OPA 合并成<b>单入口</b>后，"绕过其中一条"在架构上
 * 不可表达（PRD-07 §1.5-3 "导出旁路 grep = 0" 的结构性实现，而非人工审查）。</p>
 */
public final class DecisionBundle {

    /** 通道语义（C.1 Purpose）。 */
    public enum Purpose { PAGE, EXPORT, AI }

    private final Map<String, Object> rls;
    private final Map<String, Object> columns;
    private final List<Map<String, Object>> masks;
    private final Map<String, Object> opa;
    private final String decisionId;
    private final String traceId;

    public DecisionBundle(Map<String, Object> rls, Map<String, Object> columns,
                          List<Map<String, Object>> masks, Map<String, Object> opa,
                          String decisionId, String traceId) {
        this.rls = rls;
        this.columns = columns;
        this.masks = masks == null ? new ArrayList<>() : masks;
        this.opa = opa;
        this.decisionId = decisionId;
        this.traceId = traceId;
    }

    public Map<String, Object> rls() { return rls; }
    public Map<String, Object> columns() { return columns; }
    public List<Map<String, Object>> masks() { return masks; }
    public Map<String, Object> opa() { return opa; }
    public String decisionId() { return decisionId; }
    public String traceId() { return traceId; }

    /** CLS 是否拒绝全部列（allow 模式且 names 空）。 */
    public boolean columnsDenied() {
        Object mode = columns.get("mode");
        Object names = columns.get("names");
        boolean allowMode = "allow".equals(String.valueOf(mode));
        boolean empty = names instanceof List<?> l && l.isEmpty();
        return allowMode && empty;
    }

    // ── #142/B4-3b 只增：范围证据与水印义务 ──────────────────────────────
    //
    // 动机（差距文档 D-B4-10/D-B4-11）：导出件的「权限范围自证」此前是调用方硬编码的
    // 常量串（写什么就是什么），范围没有可核对出处。本组访问器把「命中了哪条策略」
    // 升为 bundle 的一等信息（{@code rls.policyRefs} / {@code columns.policyRefs}，
    // 由 {@link SecurityDecisionService} 从各源原始返回单点写入），消费方只能从
    // 这里取，不得再自行拼常量 —— 常量与真值的区别就是能不能被审计反查。

    /** RLS 命中策略名（单源 = {@code RowLevelSecurityServiceImpl#apply} 的 policies 清单）。 */
    public List<String> rlsPolicyRefs() {
        return policyNames(rls.get("policyRefs"));
    }

    /** CLS 命中策略名（单源 = {@code ColumnLevelSecurityServiceImpl#getColumns} 的 policies 清单）。 */
    public List<String> clsPolicyRefs() {
        return policyNames(columns.get("policyRefs"));
    }

    /** 合并谓词模板（{@code 1=1} = 无限制；{@code 1=0} = 拒绝；其余 = 需下推）。 */
    public String rlsPredicateTemplate() {
        Object t = rls.get("predicateTemplate");
        return t == null ? null : String.valueOf(t);
    }

    /** 无匹配策略时是否拒绝（{@code denyIfEmpty}，导出通道必须消费）。 */
    public boolean rlsDenyIfEmpty() {
        return Boolean.TRUE.equals(rls.get("denyIfEmpty"));
    }

    /**
     * 范围证据串（导出件「权限范围」字段的唯一合法出处，C.1 [3] auditSeed 同源）。
     *
     * <p>形如 {@code decision=<decisionId> purpose=export rls=[rls-finance-all] cls=[NONE]}：
     * 每个组成部分都可回查 {@code ecos_rls_policy.policy_name} / {@code ecos_cls_policy.policy_name}，
     * 因此「scope=finance-all」这类断言不再是自述。</p>
     */
    public String scopeEvidence(DecisionBundle.Purpose purpose) {
        return "decision=" + decisionId
                + " purpose=" + (purpose == null ? Purpose.PAGE.name().toLowerCase() : purpose.name().toLowerCase())
                + " rls=[" + (rlsPolicyRefs().isEmpty() ? "NONE" : String.join("+", rlsPolicyRefs())) + "]"
                + " cls=[" + (clsPolicyRefs().isEmpty() ? "NONE" : String.join("+", clsPolicyRefs())) + "]";
    }

    /**
     * OPA 水印义务文本（§2.10.2 五件套之四 / 从零实施版 line 2542：
     * {@code obligations.watermark} → 导出文件页眉页脚）。
     *
     * <p>兼容三种义务书写形态：{@code {obligation:"watermark", watermark:"..."}}、
     * {@code {watermark:"..."}}、{@code "watermark"} 字符串项。<b>无义务时返回 null</b>
     * —— 消费方须原样落 {@code NOT_ISSUED} 标记，禁把"没有水印义务"渲染成"已加水印"。</p>
     */
    public String watermarkObligation() {
        Object raw = opa == null ? null : opa.get("obligations");
        if (!(raw instanceof List<?> list)) return null;
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Object wm = m.get("watermark");
                if (wm != null && !String.valueOf(wm).isBlank()) return String.valueOf(wm);
                if ("watermark".equals(String.valueOf(m.get("obligation")))) {
                    Object value = m.get("value");
                    if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
                }
            } else if (item instanceof String s && s.toLowerCase().startsWith("watermark")) {
                return s;
            }
        }
        return null;
    }

    private static List<String> policyNames(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                Object name = m.get("name");
                if (name != null) out.add(String.valueOf(name));
            } else if (o != null) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rls", rls);
        m.put("columns", columns);
        m.put("masks", masks);
        m.put("opa", opa);
        m.put("decisionId", decisionId);
        m.put("traceId", traceId);
        return m;
    }
}
