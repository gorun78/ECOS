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
 * W40 12-cell 矩阵 — FIN_ALL 行：财务负责人对 {@code fc_project_period_fact}
 * 获得全量行可见性（{@code 1=1} 全放行谓词，E.6.2 种子唯一合法 1=1 形态）。
 *
 * <p>Mock {@link JdbcTemplate#queryForList} 返回一条 {@code source_kind=parameterized}
 * 且 {@code predicate_template="1=1"} 的策略；{@code apply} 应原样返回 {@code 1=1}
 * 且绑定为空，不放任 fall-through。</p>
 */
@ExtendWith(MockitoExtension.class)
class RlsFinAllTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    RowLevelSecurityServiceImpl rls;

    @BeforeEach
    void setup() {
        rls = new RowLevelSecurityServiceImpl(jdbcTemplate);
    }

    /** FIN_ALL：财务全量行 —— predicateTemplate 必须为 1=1（全放行）。 */
    @Test
    @DisplayName("FIN_ALL: fc_project_period_fact 财务全量行 predicateTemplate=1=1")
    void page() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("policy_name", "fin-all-all-rows");
        row.put("table_name", "fc_project_period_fact");
        row.put("resource_id", null);
        row.put("filter_expr", null);
        row.put("predicate_template", "1=1");
        row.put("bindings_json", "[]");
        row.put("source_kind", "parameterized");
        row.put("priority", 10);
        row.put("enabled", Boolean.TRUE);
        row.put("is_deleted", 0);
        row.put("user_id", null);
        row.put("role_id", null);
        row.put("version_no", "1");
        row.put("description", "FIN_ALL 财务全量");

        when(jdbcTemplate.queryForList(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(row));

        Map<String, Object> result = rls.apply("fc_project_period_fact", "finance-user");

        assertEquals("1=1", result.get("predicateTemplate"),
                "FIN_ALL 必须返回全放行谓词 1=1");
        // 全放行无绑定
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bindings = (List<Map<String, Object>>) result.get("bindings");
        assertTrue(bindings.isEmpty(), "1=1 全放行不应携带任何绑定变量");
        assertFalse((Boolean) result.get("denyAll"), "全放行策略不存在时应 denyAll=false");
        // 兼容字段同步暴露（禁用于拼 SQL，仅日志核对）
        assertEquals("1=1", result.get("condition"));
        assertTrue((Boolean) result.get("conditionDeprecated"),
                "condition 兼容字段必须标注 deprecated=true");
    }
}
