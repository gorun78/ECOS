package com.chinacreator.gzcm.engine.data.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * QueryExecutorBindingTest — W31 {@link RlsQueryFilter#render} 参数化谓词安全渲染（C.2.2）。
 *
 * <p>不启 Spring、不连 PG：直接调用静态 {@code render(Map, orgId)}，验证
 * fail-closed 拒（deny / 无模板 / 越界 source / 注入字符 / 残留占位符）与
 * 合法 CONTEXT_ORG 绑定代入。渲染契约 = 值域白名单之外的字符一律 {@link RlsQueryFilter.RlsRenderException}。</p>
 */
class QueryExecutorBindingTest {

    private static Map<String, Object> base(Map<String, Object> extra) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("denyAll", false);
        m.putAll(extra);
        return m;
    }

    @Test
    @DisplayName("denyAll=true → 显式拒 REL_RLS_DENY")
    void render_denyAll_throwsRlsRenderException() {
        Map<String, Object> data = base(Map.of("denyAll", true));
        assertThrows(RlsQueryFilter.RlsRenderException.class,
                () -> RlsQueryFilter.render(data, null));
    }

    @Test
    @DisplayName("denyIfEmpty=true（无匹配策略短路）→ 拒 REL_RLS_DENY")
    void render_denyIfEmpty_throwsRlsRenderException() {
        Map<String, Object> data = base(Map.of(
                "denyIfEmpty", true,
                "predicateTemplate", "1=1"));
        assertThrows(RlsQueryFilter.RlsRenderException.class,
                () -> RlsQueryFilter.render(data, null));
    }

    @Test
    @DisplayName("缺 predicateTemplate → 拒 REL_RLS_NO_TEMPLATE")
    void render_noTemplate_throws() {
        Map<String, Object> data = base(Map.of("denyAll", false));
        assertThrows(RlsQueryFilter.RlsRenderException.class,
                () -> RlsQueryFilter.render(data, null));
    }

    @Test
    @DisplayName("1=1 全量行谓词 → 直接放行")
    void render_1eq1_returnsPermit() {
        Map<String, Object> data = base(Map.of(
                "denyIfEmpty", false,
                "predicateTemplate", "1=1"));
        assertEquals("1=1", RlsQueryFilter.render(data, null));
    }

    @Test
    @DisplayName("CONTEXT_ORG 绑定：值来自 orgId 入参（忽略 binding.value 字段），占位符已消解")
    void render_binding_substitution_withSafeValue() {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("name", "dept");
        b.put("source", "CONTEXT_ORG");
        b.put("value", "SHOULD_BE_IGNORED"); // CONTEXT_ORG 取值不走 binding.value，防误信客户端
        Map<String, Object> data = base(Map.of(
                "denyIfEmpty", false,
                "predicateTemplate", "department_id = :dept",
                "bindings", List.of(b)));

        String result = RlsQueryFilter.render(data, "dept001");

        // 非数值 SAFE 值按 renderScalar 规则加单引号内联
        assertEquals("department_id = 'dept001'", result);
        assertFalse(result.contains(":dept"), "占位符 :dept 必须被消解");
    }

    @Test
    @DisplayName("绑定值含注入字符（分号/引号）→ 拒 REL_RLS_UNSAFE_VALUE")
    void render_injectionChar_inValue_rejected() {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("name", "dept");
        b.put("source", "CONTEXT_ORG");
        Map<String, Object> data = base(Map.of(
                "denyIfEmpty", false,
                "predicateTemplate", "department_id = :dept",
                "bindings", List.of(b)));

        // 注入串带引号 + 分号 + 注释符
        assertThrows(RlsQueryFilter.RlsRenderException.class,
                () -> RlsQueryFilter.render(data, "x' OR 1=1; --"));
    }

    @Test
    @DisplayName("模板残留未声明绑定 :unknown → 拒 REL_RLS_UNRESOLVED_BINDING")
    void render_residualBindingAfterSub_rejected() {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("name", "dept");
        b.put("source", "CONTEXT_ORG");
        Map<String, Object> data = base(Map.of(
                "denyIfEmpty", false,
                "predicateTemplate", "department_id = :dept AND status = :unknown",
                "bindings", List.of(b)));

        assertThrows(RlsQueryFilter.RlsRenderException.class,
                () -> RlsQueryFilter.render(data, "dept001"));
    }

    @Test
    @DisplayName("越界来源 source=MALICIOUS_SOURCE → 拒 REL_RLS_SOURCE_UNSUPPORTED")
    void render_sourceNotSupported_rejected() {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("name", "dept");
        b.put("source", "MALICIOUS_SOURCE");
        Map<String, Object> data = base(Map.of(
                "denyIfEmpty", false,
                "predicateTemplate", "department_id = :dept",
                "bindings", List.of(b)));

        assertThrows(RlsQueryFilter.RlsRenderException.class,
                () -> RlsQueryFilter.render(data, "dept001"));
    }
}
