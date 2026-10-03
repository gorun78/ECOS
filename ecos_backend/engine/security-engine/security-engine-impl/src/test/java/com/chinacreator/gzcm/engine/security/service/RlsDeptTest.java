package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * W40 12-cell 矩阵 — DEPT 行：部门用户对 {@code fc_cost_allocation_fact}
 * 仅可见本部门行 —— 谓词 {@code department_id = :userDeptId}，
 * 绑定 {@code userDeptId} 来源 {@code CONTEXT_ORG}。
 *
 * <p>这是对"列名 = :context-binding"最简参数化形态的锁定：
 * 等值谓词 + 单一上下文绑定，无子查询、无字面量，属 C.2.2 白名单核心形态。</p>
 */
@ExtendWith(MockitoExtension.class)
class RlsDeptTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    RowLevelSecurityServiceImpl rls;

    @BeforeEach
    void setup() {
        rls = new RowLevelSecurityServiceImpl(jdbcTemplate);
    }

    /** DEPT：谓词必须为 department_id = :userDeptId，绑定 userDeptId=CONTEXT_ORG。 */
    @Test
    @DisplayName("DEPT: fc_cost_allocation_fact 部门行 predicateTemplate=department_id = :userDeptId")
    void page() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("policy_name", "dept-scoped");
        row.put("table_name", "fc_cost_allocation_fact");
        row.put("resource_id", null);
        row.put("filter_expr", null);
        row.put("predicate_template", "department_id = :userDeptId");
        row.put("bindings_json", "[{\"name\":\"userDeptId\",\"source\":\"CONTEXT_ORG\"}]");
        row.put("source_kind", "parameterized");
        row.put("priority", 10);
        row.put("enabled", Boolean.TRUE);
        row.put("is_deleted", 0);
        row.put("user_id", null);
        row.put("role_id", null);
        row.put("version_no", "1");
        row.put("description", "DEPT 部门限流");

        when(jdbcTemplate.queryForList(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(row));

        Map<String, Object> result = rls.apply("fc_cost_allocation_fact", "dept-user");

        assertEquals("department_id = :userDeptId", result.get("predicateTemplate"),
                "DEPT 谓词必须为列名=上下文绑定的等值形态");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bindings = (List<Map<String, Object>>) result.get("bindings");
        assertEquals(1, bindings.size(), "仅一个绑定变量");
        assertEquals("userDeptId", bindings.get(0).get("name"));
        assertEquals("CONTEXT_ORG", bindings.get(0).get("source"));

        assertFalse((Boolean) result.get("denyAll"), "命中策略后 denyAll 应为 false");
        assertTrue((Boolean) result.get("denyIfEmpty"),
                "denyIfEmpty 恒 true（无匹配策略时 fail-closed 403）");
    }
}
