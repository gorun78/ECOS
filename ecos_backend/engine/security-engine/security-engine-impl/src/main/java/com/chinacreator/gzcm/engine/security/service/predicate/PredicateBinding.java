package com.chinacreator.gzcm.engine.security.service.predicate;

import java.util.List;

/**
 * 详细设计-01 C.2.2 — 单个绑定变量声明。
 *
 * @param name    模板内 {@code :name} 占位符名（{@code ^[a-zA-Z_][a-zA-Z0-9_]*$}）
 * @param source  来源，必须命中 {@link BindingSource} 白名单
 * @param value   {@code STATIC_LIST} 时的静态值（≤50 项时逐项绑定）；上下文来源为 null
 * @param items   {@code STATIC_LIST} 的分项（与 value 二选一，多项时非空）
 */
public record PredicateBinding(String name, String source, String value, List<String> items) {

    public static final int STATIC_LIST_MAX = 50;

    public static PredicateBinding ofContext(String name, String source) {
        return new PredicateBinding(name, source, null, null);
    }
}
