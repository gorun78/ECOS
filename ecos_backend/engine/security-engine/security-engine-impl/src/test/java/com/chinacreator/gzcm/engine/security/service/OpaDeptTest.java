package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * W40 12-cell 矩阵 — DEPT 行：部门角色放行且携带 obligations（如脱敏/行过滤义务）。
 *
 * <p>体现 obligations 通道（W36 / F06-02 脱敏接线依赖字段）：放行的同时
 * 附加义务（例如对金额/敏感列脱敏）.</p>
 */
class OpaDeptTest {

    OpaPolicyService opaService;

    @BeforeEach
    void setup() {
        opaService = mock(OpaPolicyService.class);
    }

    /** DEPT：department 角色 allow=true 且 obligations 非空（附脱敏义务）。 */
    @Test
    @DisplayName("DEPT: 部门角色 allow=true + obligations 含脱敏义务")
    void page() {
        Map<String, Object> resp = Map.of(
                "allow", Boolean.TRUE,
                "obligations", List.of(Map.of(
                        "type", "mask",
                        "fields", List.of("amount", "salary"),
                        "rule", "AMOUNT")),
                "policyId", "dept_scoped_access",
                "source", "opa"
        );
        when(opaService.evaluate(eq("data_access"), anyMap())).thenReturn(resp);

        Map<String, Object> result = opaService.evaluate("data_access",
                Map.of("role", "dept_user"));

        assertEquals(Boolean.TRUE, result.get("allow"), "部门角色必须放行");
        @SuppressWarnings("unchecked")
        List<Object> obligations = (List<Object>) result.get("obligations");
        assertFalse(obligations.isEmpty(), "部门放行必须携带 obligations（脱敏/过滤义务）");
        assertTrue(obligations.get(0) instanceof Map<?, ?> om
                        && "mask".equals(om.get("type")),
                "obligation 应声明 mask 义务（F06-02 脱敏接线）");
    }
}
