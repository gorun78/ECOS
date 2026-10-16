package com.chinacreator.gzcm.engine.security.rls;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 详细设计-01 C.2.2 — RLS 参数化谓词对象（替代"条件字符串"）。
 *
 * <p>调用方只能拿到 {@code predicateTemplate}（占位符文本）+ {@code bindings}（名→参数值），
 * 并在查询执行侧用 <b>PreparedStatement 的 ? 占位符</b>绑定值，<b>不得</b>把绑定值拼进 SQL
 * 字符串 —— 从结构上关闭 S-4 双重注入（W31 P0）。</p>
 *
 * <p>序列化形态 = D 章 {@code DecisionBundle.rls}：
 * {@code {predicateTemplate, bindings[], combine, denyIfEmpty}}。存量裸文本策略
 * （{@code source_kind=legacy_text}）由本类的 {@link #legacyText} 工厂包一层，
 * 迁移脚本改写后 source_kind 置 {@code parameterized}。</p>
 */
public final class RlsPredicate {

    /** 无匹配策略时的兜底形态：不设任何谓词 = 全放行（调用方须据 denyIfEmpty 决定放行/拒绝）。 */
    public static final String COMBINE_AND = "AND";
    public static final String COMBINE_OR  = "OR";

    private String predicateTemplate;
    private List<PredicateBinding> bindings = new ArrayList<>();
    private String combine = COMBINE_AND;
    private boolean denyIfEmpty = true;

    /** 每个子谓词段（对应一条 policy），combine 时以 AND/OR 连接。 */
    private List<String> segments = new ArrayList<>();

    public RlsPredicate() { }

    public String getPredicateTemplate() { return predicateTemplate; }
    public void setPredicateTemplate(String t) { this.predicateTemplate = t; }

    public List<PredicateBinding> getBindings() { return bindings; }
    public void setBindings(List<PredicateBinding> b) { this.bindings = b; }

    public String getCombine() { return combine; }
    public void setCombine(String c) { this.combine = (c == null ? COMBINE_AND : c); }

    public boolean isDenyIfEmpty() { return denyIfEmpty; }
    public void setDenyIfEmpty(boolean d) { this.denyIfEmpty = d; }

    public List<String> getSegments() { return segments; }
    public void setSegments(List<String> s) { this.segments = s; }

    /** 是否有至少一个生效谓词段。 */
    public boolean hasPredicate() {
        return predicateTemplate != null && !predicateTemplate.isBlank();
    }

    /**
     * 兼容旧契约 D.5.3：返回旧 {@code condition} 字段（标注 deprecated，仅供日志核对，
     * <b>禁用于拼 SQL</b>）。新消费方改用 {@link #getPredicateTemplate} + {@link #bindValues}。
     */
    public String legacyConditionForLog() {
        return predicateTemplate == null ? "1=1" : predicateTemplate;
    }

    /** 按 binding 名取出绑定值列表（保持占位符顺序），供 PreparedStatement 依次 {@code setXxx}。 */
    public List<Object> bindValues() {
        List<Object> out = new ArrayList<>();
        for (PredicateBinding b : bindings) {
            out.add(b.getValue());
        }
        return out;
    }

    /** REST 序列化（D 章 DecisionBundle.rls 子结构）。
     *
     * <p>#142/B4-3d-5（差距 D-B4-24）：键名取 {@link RlsExecutionContract} 的表达面常量。
     * 本段是<b>表达面</b>（模板文本 + 具名绑定），只供日志/校验核对；
     * 拼 SQL 必须读同契约的执行契约面（{@code whereClause}/{@code whereParams}），
     * 禁在此处另立一套"可读但不可执行"的口径。</p>
     */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(RlsExecutionContract.KEY_PREDICATE_TEMPLATE, predicateTemplate);
        List<Map<String, Object>> bs = new ArrayList<>();
        for (PredicateBinding b : bindings) bs.add(b.toMap());
        m.put("bindings", bs);
        m.put("combine", combine);
        m.put(RlsExecutionContract.KEY_DENY_IF_EMPTY, denyIfEmpty);
        return m;
    }

    /** 单条裸 TEXT 策略（source_kind=legacy_text）包一层，迁移前兜底。 */
    public static RlsPredicate legacyText(String rawFilterExpr) {
        RlsPredicate p = new RlsPredicate();
        p.setPredicateTemplate(rawFilterExpr == null ? "1=1" : rawFilterExpr);
        p.setDenyIfEmpty(true);
        return p;
    }
}
