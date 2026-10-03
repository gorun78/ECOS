package com.chinacreator.gzcm.engine.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * W40 12-cell 矩阵 — DENY_NO_LEAK 行：未授权用户无任何匹配策略 ⇒
 * <b>fail-closed 全拒</b>（{@code 1=0}）且<b>不泄露策略存在性</b>。
 *
 * <p>不变量：
 * <ol>
 *   <li>{@code denyAll == true}（调用方 MUST 403 ECOS-SEC-401，不得降级放行）</li>
 *   <li>{@code predicateTemplate == "1=0"}（DENY 模板）</li>
 *   <li>{@code policies} 为空（不暴露任何策略元数据 —— 无泄露）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class RlsDenyNoLeakTest {

    @Mock
    JdbcTemplate jdbcTemplate;

    RowLevelSecurityServiceImpl rls;

    @BeforeEach
    void setup() {
        rls = new RowLevelSecurityServiceImpl(jdbcTemplate);
    }

    /** DENY_NO_LEAK：无任何策略 → 1=0 + denyAll，且不回显任何策略信息。 */
    @Test
    @DisplayName("DENY_NO_LEAK: 无匹配策略 ⇒ 1=0 + denyAll + policies 空（不泄露存在性）")
    void pageNoLeak() {
        when(jdbcTemplate.queryForList(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        Map<String, Object> result = rls.apply("fc_project_period_fact", "unauthorized-user");

        assertEquals("1=0", result.get("predicateTemplate"),
                "无匹配策略必须返回 DENY 模板 1=0（fail-closed）");
        assertEquals(Boolean.TRUE, result.get("denyAll"),
                "denyAll 必须为 true（调用方 403，不降级放行）");
        assertEquals(Boolean.TRUE, result.get("denyIfEmpty"), "denyIfEmpty 应为 true");

        // 不泄露：策略摘要列表必须为空
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> policies = (List<Map<String, Object>>) result.get("policies");
        assertTrue(policies.isEmpty(), "无授权响应不得回显任何策略元数据（不泄露存在性）");

        // 绑定必须为空（1=0 无占位符）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bindings = (List<Map<String, Object>>) result.get("bindings");
        assertTrue(bindings.isEmpty(), "DENY 模板不携带任何绑定");
    }
}
