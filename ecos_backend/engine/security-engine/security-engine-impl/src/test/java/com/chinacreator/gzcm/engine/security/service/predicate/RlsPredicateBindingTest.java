package com.chinacreator.gzcm.engine.security.service.predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F01-01 / W30 — RLS 参数化谓词对象契约测试。
 *
 * <p>断言核心不变量：调用方只能拿到 {@code predicateTemplate} + {@code bindings[]}
 * 声明，禁拿"已代入值的字符串条件"（S-2 外泄面结构性关闭）。
 * 12 格矩阵中 RLS 各角色的谓词语义由 RlsFinAllTest / RlsProjOwnerTest /
 * RlsDeptTest / RlsDenyNoLeakTest 覆盖。</p>
 */
@DisplayName("F01-01 RLS 参数化谓词契约")
class RlsPredicateBindingTest {

    @Test
    @DisplayName("allRows() — 财务负责人全量行形态：1=1 + 空绑定 + denyIfEmpty=true")
    void allRows_form() {
        RlsParamSpec spec = RlsParamSpec.allRows();
        assertEquals("1=1", spec.template());
        assertTrue(spec.bindings().isEmpty());
        assertTrue(spec.denyIfEmpty());
        assertFalse(spec.denyAll(), "allRows 本身不触发 denyAll");
        assertFalse(spec.noPolicies());
    }

    @Test
    @DisplayName("denyIfEmpty=true + noPolicies=true → denyAll=true（403 ECOS-SEC-401 语义）")
    void denyAll_whenNoPoliciesAndDenyIfEmpty() {
        RlsParamSpec spec = new RlsParamSpec(RlsParamSpec.DENY_TEMPLATE, List.of(), true, true);
        assertTrue(spec.denyAll());
        assertEquals("1=0", spec.template());
    }

    @Test
    @DisplayName("denyIfEmpty=false → 即便无策略也不 denyAll（放行语义）")
    void noDenyAll_whenDenyIfEmptyFalse() {
        RlsParamSpec spec = new RlsParamSpec(RlsParamSpec.DENY_TEMPLATE, List.of(), false, true);
        assertFalse(spec.denyAll());
    }

    @Test
    @DisplayName("toMap() — 序列化含 predicateTemplate / bindings / denyIfEmpty 三键")
    void toMap_containsRequiredKeys() {
        RlsParamSpec spec = new RlsParamSpec(
                "department_id = :userDeptId",
                List.of(new PredicateBinding("userDeptId", "CONTEXT_ORG", null, null)),
                true, false);
        Map<String, Object> m = spec.toMap();
        assertEquals("department_id = :userDeptId", m.get("predicateTemplate"));
        assertEquals(true, m.get("denyIfEmpty"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> b = (List<Map<String, Object>>) m.get("bindings");
        assertNotNull(b);
        assertEquals(1, b.size());
        assertEquals("userDeptId", b.get(0).get("name"));
        assertEquals("CONTEXT_ORG", b.get(0).get("source"));
    }

    @Test
    @DisplayName("binding 含 items（STATIC_LIST）→ toMap 逐项保留")
    void toMap_staticListItemsPreserved() {
        Map<String, Object> m = RlsParamSpec.toMap(
                new PredicateBinding("deptIds", "STATIC_LIST", null, List.of("d1", "d2")));
        assertEquals("deptIds", m.get("name"));
        assertEquals("STATIC_LIST", m.get("source"));
        assertEquals(List.of("d1", "d2"), m.get("items"));
    }

    @Test
    @DisplayName("bindings() 不可变 — 调用方无法篡改谓词绑定")
    void bindings_immutable() {
        RlsParamSpec spec = new RlsParamSpec("1=1",
                List.of(new PredicateBinding("x", "CONTEXT_USER_ID", null, null)), true, false);
        assertThrows(UnsupportedOperationException.class,
                () -> spec.bindings().add(new PredicateBinding("y", "CONTEXT_ORG", null, null)));
    }

    @Test
    @DisplayName("绑定名白名单形态 — 占位符仅字母数字下划线（^:name regex 由校验器保证）")
    void placeholderExtraction_roundTrip() {
        String template = "org_id = :tenantId AND user_id = :userId";
        List<PredicateBinding> bs = List.of(
                new PredicateBinding("tenantId", "CONTEXT_TENANT_ID", null, null),
                new PredicateBinding("userId", "CONTEXT_USER_ID", null, null));
        assertDoesNotThrow(() -> RlsPredicateValidator.validateTemplate(template, bs));
        assertEquals(List.of("tenantId", "userId"), RlsPredicateValidator.placeholdersOf(template));
    }
}
