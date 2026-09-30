package com.chinacreator.gzcm.engine.ai.oag;

import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PMO-74 H10-T1（N8）— OAG 安全检查节点「默认 DENY」验收。
 *
 * <p>整改前 {@code SecurityCheckerNode} 是空壳放行；现强制经
 * {@link AiSecurityEngineClient} 调 security-engine REST。本类验收口径是清单 §H10-T1
 * 的「security 断连 → OAG 返回 BLOCKED」用例，覆盖三条拒绝路径
 * （黑名单短路 / ABAC DENY / RLS 不可用返回 {@code 1=0}）。</p>
 *
 * <p>含 2 条正向对照（ABAC 放行 + RLS 有条件、RLS 无条件放行），
 * 证明 DENY 断言可观察而非恒真。</p>
 */
class SecurityCheckerNodeDenyTest {

    private static final String USER = "u-42";
    private static final String TENANT = "t-7";

    private final AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);

    private SecurityCheckerNode newNode() {
        return new SecurityCheckerNode(client);
    }

    private OagPipelineContext ctx(String intent, String query) {
        OagPipelineContext c = new OagPipelineContext(USER, TENANT);
        c.setIntent(intent);
        c.setUserQuery(query);
        return c;
    }

    @Test
    @DisplayName("黑名单短路 → BLOCKED，且不触达 security-engine 裁决")
    void blockedPatternShortCircuitsBeforeAdjudication() {
        OagPipelineContext c = newNode().execute(ctx("QUERY", "DROP TABLE ecos_dw.contract"));

        assertFalse(c.isSecurityPassed());
        assertEquals("BLOCKED", c.getStatus());
        assertNotNull(c.getSecurityBlockReason());
        assertTrue(c.getSecurityBlockReason().contains("DROP TABLE"),
                "阻止原因应指名命中的模式: " + c.getSecurityBlockReason());
        verify(client, never()).evaluate(anyString(), anyString(), anyString(), any());
        verify(client, never()).applyRls(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("ACTION 意图 + ABAC 未放行 → 默认 DENY 置 BLOCKED")
    void actionIntentDeniedWhenAbacRejects() {
        when(client.evaluate(anyString(), anyString(), anyString(), any())).thenReturn(false);
        when(client.applyRls(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("tenant_id = 't-7'");

        OagPipelineContext c = newNode().execute(ctx("ACTION", "关闭合同 1024"));

        assertFalse(c.isSecurityPassed());
        assertEquals("BLOCKED", c.getStatus());
        verify(client).audit(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("security 不可用（RLS 返回 1=0）→ 即使非 ACTION 也 BLOCKED")
    void rlsUnavailableDeniesQueryIntent() {
        when(client.applyRls(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("1=0");

        OagPipelineContext c = newNode().execute(ctx("QUERY", "查询有效合同"));

        assertFalse(c.isSecurityPassed());
        assertEquals("BLOCKED", c.getStatus());
        verify(client, never()).evaluate(anyString(), anyString(), anyString(), any());
    }

    /** 正向对照：裁决全部放行时节点应通过，证明上面的 BLOCKED 断言非恒真。 */
    @Test
    @DisplayName("对照-1 ABAC 放行 + RLS 有条件 → 通过并下传 whereClause")
    void passesWhenBothAdjudicationsAllow() {
        when(client.evaluate(anyString(), anyString(), anyString(), any())).thenReturn(true);
        when(client.applyRls(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("tenant_id = 't-7'");

        OagPipelineContext c = newNode().execute(ctx("ACTION", "关闭合同 1024"));

        assertTrue(c.isSecurityPassed());
        assertFalse("BLOCKED".equals(c.getStatus()));
        assertNotNull(c.getRlsFilters());
        assertEquals("tenant_id = 't-7'", c.getRlsFilters().get("whereClause"));
        assertEquals(TENANT, c.getRlsFilters().get("tenantId"));
    }

    /** 正向对照 2：RLS 无附加条件（空串）仍是放行，不得被当作拒绝。 */
    @Test
    @DisplayName("对照-2 RLS 返回空串 → 通过且不带 whereClause")
    void emptyRlsClauseIsAllowNotDeny() {
        when(client.applyRls(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn("");

        OagPipelineContext c = newNode().execute(ctx("QUERY", "查询有效合同"));

        assertTrue(c.isSecurityPassed());
        Map<String, Object> filters = c.getRlsFilters();
        assertNotNull(filters);
        assertFalse(filters.containsKey("whereClause"), "空条件不应写入 whereClause");
    }
}
