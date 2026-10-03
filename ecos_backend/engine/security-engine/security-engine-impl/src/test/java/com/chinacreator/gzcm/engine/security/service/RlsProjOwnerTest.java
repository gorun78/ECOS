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
 * W40 12-cell 矩阵 — PROJ_OWNER 行：项目负责人对 {@code fc_project_period_fact}
 * 仅可见"本部门归属项目"下的行 —— 谓词为<b>项目归属 IN 子查询</b>
 * （{@link com.chinacreator.gzcm.engine.security.service.predicate.RlsPredicateValidator#PROJECT_ATTRIBUTION_SUBQUERY}
 * 白名单唯一合法 SELECT 形态），绑定声明 {@code userDeptId} 来源 {@code CONTEXT_ORG}。
 *
 * <p>断言召唤两项不变量：
 * <ol>
 *   <li>{@code predicateTemplate} 命中 IN (SELECT ... biz_project_attribution ...) 子查询形态</li>
 *   <li>{@code bindings[]} 含一项 name=userDeptId、source=CONTEXT_ORG（上下文来源，值服务端还原）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class RlsProjOwnerTest {

    /** 项目归属 IN 子查询（与 RlsPredicateValidator 白名单 / E.6.2 种子一致）。 */
    private static final String SUBQUERY_TEMPLATE =
            "project_id IN (SELECT project_id FROM ecos_dw.biz_project_attribution WHERE department_id = :userDeptId)";

    @Mock
    JdbcTemplate jdbcTemplate;

    RowLevelSecurityServiceImpl rls;

    @BeforeEach
    void setup() {
        rls = new RowLevelSecurityServiceImpl(jdbcTemplate);
    }

    /** PROJ_OWNER：谓词必须是项目归属子查询，且绑定 userDeptId=CONTEXT_ORG。 */
    @Test
    @DisplayName("PROJ_OWNER: 谓词=项目归属 IN 子查询 + binding userDeptId/CONTEXT_ORG")
    void page() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("policy_name", "proj-owner-attribution");
        row.put("table_name", "fc_project_period_fact");
        row.put("resource_id", null);
        row.put("filter_expr", null);
        row.put("predicate_template", SUBQUERY_TEMPLATE);
        row.put("bindings_json", "[{\"name\":\"userDeptId\",\"source\":\"CONTEXT_ORG\"}]");
        row.put("source_kind", "parameterized");
        row.put("priority", 10);
        row.put("enabled", Boolean.TRUE);
        row.put("is_deleted", 0);
        row.put("user_id", null);
        row.put("role_id", null);
        row.put("version_no", "1");
        row.put("description", "PROJ_OWNER 项目归属");

        when(jdbcTemplate.queryForList(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(row));

        Map<String, Object> result = rls.apply("fc_project_period_fact", "proj-owner");

        // (a) 谓词命中 IN 子查询形态
        Object template = result.get("predicateTemplate");
        assertTrue(template instanceof String t && t.startsWith("project_id IN (SELECT project_id "),
                "谓词必须是项目归属 IN 子查询, 实际=" + template);
        assertTrue(String.valueOf(template).contains("biz_project_attribution"),
                "子查询必须锚定 ecos_dw.biz_project_attribution 归属表");
        assertTrue(String.valueOf(template).contains(":userDeptId"),
                "子查询必须含 :userDeptId 占位符（PreparedStatement 绑定, 禁字面量）");

        // (b) 绑定声明含 userDeptId=CONTEXT_ORG
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bindings = (List<Map<String, Object>>) result.get("bindings");
        assertTrue(bindings.stream()
                        .anyMatch(b -> "userDeptId".equals(b.get("name"))
                                && "CONTEXT_ORG".equals(b.get("source"))),
                "bindings 必须声明 userDeptId 来源 CONTEXT_ORG（服务端机构上下文, 客户端不可控）");

        assertFalse((Boolean) result.get("denyAll"), "命中策略后 denyAll 应为 false");
    }
}
