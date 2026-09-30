package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 详细设计-01 F01-01 / W30 — RLS 从"条件字符串 + params 恒空"改造为"参数化谓词"。
 *
 * <p>断言：</p>
 * <ul>
 *   <li>命中参数化策略 → {@code predicate.predicateTemplate} 含 {@code :name} 占位符，
 *       <b>不含</b>裸值字面量；</li>
 *   <li>{@code params} <b>非空</b>（name→value 绑定映射），关闭 S-2 params 恒空；</li>
 *   <li>旧 {@code condition} 字段仍返回且标注 {@code conditionDeprecated=true}（兼容 D.5.3）。</li>
 * </ul>
 */
class RlsPredicateBindingTest {

    private RowLevelSecurityServiceImpl svc;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        svc = new RowLevelSecurityServiceImpl(jdbc);
    }

    @Test
    @DisplayName("1. 参数化策略 → predicateTemplate 含占位符、params 非空、无裸值")
    void parameterizedPolicyYieldsPlaceholdersAndParams() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(policyRow(
                "parameterized", "department_id = :userDeptId",
                "[{\"name\":\"userDeptId\",\"source\":\"CONTEXT_ORG\",\"value\":null}]")));

        Map<String, Object> r = svc.apply("fc_project_period_fact", "u1");

        @SuppressWarnings("unchecked")
        Map<String, Object> pred = (Map<String, Object>) r.get("predicate");
        assertNotNull(pred, "必须返回参数化谓词对象");
        String tpl = (String) pred.get("predicateTemplate");
        assertTrue(tpl.contains(":userDeptId"), "模板必须含占位符: " + tpl);
        assertTrue(!tpl.matches(".*dept_a.*"), "模板不得含具体值字面量: " + tpl);

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) r.get("params");
        assertNotNull(params);
        assertTrue(params.containsKey("userDeptId"), "params 必须含占位符绑定键");

        assertEquals(Boolean.TRUE, r.get("conditionDeprecated"),
                "旧 condition 字段必须标注 deprecated");
        assertNotNull(r.get("condition"), "兼容 condition 字段仍在（仅供日志）");
    }

    @Test
    @DisplayName("2. 无策略 → 1=1 兜底 + denyIfEmpty=true（调用方据此 DENY）")
    void noPolicyYieldsOneEqualsOneAndDenyIfEmpty() {
        when(jdbc.queryForList(anyString(), any(), any(), any(), any())).thenReturn(List.of());

        Map<String, Object> r = svc.apply("t", "u1");

        @SuppressWarnings("unchecked")
        Map<String, Object> pred = (Map<String, Object>) r.get("predicate");
        assertEquals("1=1", pred.get("predicateTemplate"));
        assertEquals(Boolean.TRUE, pred.get("denyIfEmpty"), "无策略默认 denyIfEmpty=true（不泄露存在性）");
    }

    @Test
    @DisplayName("3. 存量裸文本策略(legacy) → 包一层 legacy_text，params 空但 template 保留")
    void legacyTextPolicyWrapped() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(
                List.of(policyRow("legacy", "clearance_level >= 4", null)));

        Map<String, Object> r = svc.apply("t", "u1");
        @SuppressWarnings("unchecked")
        Map<String, Object> pred = (Map<String, Object>) r.get("predicate");
        assertTrue(((String) pred.get("predicateTemplate")).contains("clearance_level >= 4"));
    }

    private static Map<String, Object> policyRow(String kind, String filterExpr, String bindingsJson) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("filter_expr", filterExpr);
        m.put("priority", 10);
        m.put("policy_name", "p-" + kind);
        m.put("predicate_template", "parameterized".equals(kind) ? filterExpr : null);
        m.put("bindings_json", bindingsJson);
        m.put("source_kind", kind);
        return m;
    }
}
