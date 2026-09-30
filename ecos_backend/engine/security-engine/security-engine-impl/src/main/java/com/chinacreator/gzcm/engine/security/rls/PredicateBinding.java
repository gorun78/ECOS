package com.chinacreator.gzcm.engine.security.rls;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 详细设计-01 C.2.2 — RLS 参数化谓词的一个绑定项。
 *
 * <p>{@code template} 中的 {@code :name} 占位符对应一条 binding。绑定值只能来自
 * 白名单 source（服务上下文 / STATIC_LIST），<b>禁止</b>自由文本 SQL 或字符串拼接，
 * 从根本上关闭 S-3"策略即代码"注入面。</p>
 */
public final class PredicateBinding {

    /** 绑定来源白名单枚举（C.2.2 越界即 400）。 */
    public static final String SRC_CONTEXT_USER_ID        = "CONTEXT_USER_ID";
    public static final String SRC_CONTEXT_TENANT_ID      = "CONTEXT_TENANT_ID";
    public static final String SRC_CONTEXT_ORG            = "CONTEXT_ORG";
    public static final String SRC_CONTEXT_CLEARANCE      = "CONTEXT_CLEARANCE_LEVEL";
    public static final String SRC_STATIC_LIST            = "STATIC_LIST";
    public static final String SRC_PROJECT_ATTRIBUTION    = "PROJECT_ATTRIBUTION_SUBQUERY";

    private static final String[] ALL_SOURCES = {
            SRC_CONTEXT_USER_ID, SRC_CONTEXT_TENANT_ID, SRC_CONTEXT_ORG,
            SRC_CONTEXT_CLEARANCE, SRC_STATIC_LIST, SRC_PROJECT_ATTRIBUTION
    };

    private final String name;
    private final String source;
    /** STATIC_LIST 存这里；CONTEXT_* 在应用期由上下文解析，此处为 null。 */
    private final Object value;

    public PredicateBinding(String name, String source, Object value) {
        this.name = name;
        this.source = source;
        this.value = value;
    }

    public String getName() { return name; }
    public String getSource() { return source; }
    public Object getValue() { return value; }

    public static boolean isContextSource(String source) {
        return SRC_CONTEXT_USER_ID.equals(source)
                || SRC_CONTEXT_TENANT_ID.equals(source)
                || SRC_CONTEXT_ORG.equals(source)
                || SRC_CONTEXT_CLEARANCE.equals(source);
    }

    public static boolean isKnownSource(String source) {
        if (source == null) return false;
        for (String s : ALL_SOURCES) {
            if (s.equals(source)) return true;
        }
        return false;
    }

    /** 供 REST 序列化：{name, source, value}。value 可为 null（上下文类）。 */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("source", source);
        m.put("value", value);
        return m;
    }

    public static PredicateBinding fromMap(Map<String, Object> m) {
        Object name = m.get("name");
        Object source = m.get("source");
        Object value = m.get("value");
        return new PredicateBinding(
                name == null ? null : name.toString(),
                source == null ? null : source.toString(),
                value);
    }
}
