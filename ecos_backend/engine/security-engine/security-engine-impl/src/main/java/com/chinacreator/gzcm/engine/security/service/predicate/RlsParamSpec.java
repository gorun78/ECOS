package com.chinacreator.gzcm.engine.security.service.predicate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 详细设计-01 C.2.2 — RLS 参数化谓词对象（{@code predicateTemplate} + {@code bindings[]}）。
 *
 * <p>调用方只能拿到 {@link #template()} 与 {@link #bindings()}，无法拿到"已代入值的字符串条件"，
 * 由 02 册 QueryExecutor 以 {@code ?} 占位生成 {@code PreparedStatement}（W31）。</p>
 */
public final class RlsParamSpec {

    /** 策略未命中且 denyIfEmpty=true 时返回：空谓词模板 + 空绑定 + denyIfEmpty 标记。 */
    public static final String DENY_TEMPLATE = "1=0";

    /** 财务负责人类"全量"唯一合法形态（E.6.2 种子登记于 ecos_security_asset.guard_combo）。 */
    public static final String ALL_ROWS_INLINE = "1=1";

    private final String template;
    private final List<PredicateBinding> bindings;
    private final boolean denyIfEmpty;
    private final boolean noPolicies;

    public RlsParamSpec(String template, List<PredicateBinding> bindings, boolean denyIfEmpty, boolean noPolicies) {
        this.template = template;
        this.bindings = Collections.unmodifiableList(new ArrayList<>(bindings == null ? List.of() : bindings));
        this.denyIfEmpty = denyIfEmpty;
        this.noPolicies = noPolicies;
    }

    public static RlsParamSpec allRows() {
        return new RlsParamSpec(ALL_ROWS_INLINE, List.of(), true, false);
    }

    public String template() { return template; }

    public List<PredicateBinding> bindings() { return bindings; }

    /** 无匹配策略且 denyIfEmpty=true → true（调用方 MUST 403 ECOS-SEC-401，不得降级放行）。 */
    public boolean denyAll() { return noPolicies && denyIfEmpty; }

    public boolean denyIfEmpty() { return denyIfEmpty; }

    public boolean noPolicies() { return noPolicies; }

    /** JSON 形态（供 DecisionBundle/apply 响应序列化）。 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("predicateTemplate", template);
        m.put("bindings", bindings.stream().map(RlsParamSpec::bindingToMap).toList());
        m.put("denyIfEmpty", denyIfEmpty);
        return m;
    }

    static Map<String, Object> bindingToMap(PredicateBinding b) {
        return toMap(b);
    }

    /**
     * 公共只读映射（跨包调用安全：位于 {@code .predicate} 包，暴露给 02 册
     * data-engine-impl 的 {@code RlsQueryFilter} 消费）。
     */
    public static Map<String, Object> toMap(PredicateBinding b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", b.name());
        m.put("source", b.source());
        if (b.value() != null) m.put("value", b.value());
        if (b.items() != null) m.put("items", b.items());
        return m;
    }
}
