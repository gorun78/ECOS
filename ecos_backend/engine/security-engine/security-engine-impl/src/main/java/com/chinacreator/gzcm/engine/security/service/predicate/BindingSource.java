package com.chinacreator.gzcm.engine.security.service.predicate;

import java.util.Set;

/**
 * 详细设计-01 C.2.2 — RLS 谓词绑定变量来源白名单枚举。
 *
 * <p>越界 source 即 400（{@code ECOS-SEC-410}）；允许集 = 本枚举全集，代码单源，
 * 前端 RlsPredicateEditor 只允许从该清单下拉选，禁自由文本（防存量 S-3 扩散）。</p>
 */
public enum BindingSource {
    /** 当前用户 ID（服务端上下文，客户端不可控） */
    CONTEXT_USER_ID,
    /** 当前租户 ID */
    CONTEXT_TENANT_ID,
    /** 当前机构/部门 ID（服务端由 TD_USER 解析，W40） */
    CONTEXT_ORG,
    /** 当前准入等级（clearance_level） */
    CONTEXT_CLEARANCE_LEVEL,
    /** 静态列表（≤50 项，随策略存库，逐项绑定） */
    STATIC_LIST,
    /** 预置项目归属子查询模板（E.6.2 种子，唯一下放 IN 子查询的合法形态） */
    PROJECT_ATTRIBUTION_SUBQUERY;

    public static boolean isAllowed(String source) {
        if (source == null) return false;
        for (BindingSource s : values()) {
            if (s.name().equals(source)) return true;
        }
        return false;
    }

    /** 下拉清单（不含拼接/文本形态） */
    public static Set<String> allowedNames() {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (BindingSource s : values()) {
            names.add(s.name());
        }
        return names;
    }
}
