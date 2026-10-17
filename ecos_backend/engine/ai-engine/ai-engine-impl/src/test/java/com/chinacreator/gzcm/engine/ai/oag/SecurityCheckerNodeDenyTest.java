package com.chinacreator.gzcm.engine.ai.oag;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.ai.security.AiSecurityEngineClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
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
 * <p>含 2 条正向对照（ABAC 放行 + RLS 非平凡谓词、RLS 恒真 {@code 1=1}），
 * 证明 DENY 断言可观察而非恒真。</p>
 *
 * <p><b>#142/B4-3c-2 订正（D-B4-17）</b>：原对照-2「RLS 返回空串 → 通过」把
 * security-engine 的字段名漂移固化成放行契约（空串在客户端已不可能产生），
 * 现翻正为「空/缺失片段＝形态漂移 → BLOCKED」，见
 * {@link #blankRlsClauseIsDriftAndBlocks()} 的说明；不留双轨。</p>
 *
 * <p><b>#142/B4-3d-10（D-B4-26 余项）</b>：节点改消费带态出口
 * {@code applyRlsExecution}，桩因此停在 {@link RlsExecutionContract.Execution} 上。
 * 折叠后的字符串桩会把「引擎没答」与「这人没权限」压成同一个 {@code "1=0"}，
 * 而那正是本批要关掉的缺陷——桩若停在字面量上，分态判据在测试面就消失了。</p>
 */
class SecurityCheckerNodeDenyTest {

    private static final String USER = "u-42";
    private static final String TENANT = "t-7";
    private static final String SUBJECT = "table=oag_query";

    private final AiSecurityEngineClient client = mock(AiSecurityEngineClient.class);

    private SecurityCheckerNode newNode() {
        return new SecurityCheckerNode(client);
    }

    /** 片段字符串 → 执行契约读数（与生产者应答同一形状）。 */
    private static RlsExecutionContract.Execution executionOf(String clause) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (clause != null) data.put(RlsExecutionContract.KEY_WHERE_CLAUSE, clause);
        return RlsExecutionContract.read(data, SUBJECT)
                .rejectForUnbindableChannel(SUBJECT);
    }

    private void rls(String clause) {
        when(client.applyRlsExecution(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(executionOf(clause));
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
        verify(client, never())
                .applyRlsExecution(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("ACTION 意图 + ABAC 未放行 → 默认 DENY 置 BLOCKED")
    void actionIntentDeniedWhenAbacRejects() {
        when(client.evaluate(anyString(), anyString(), anyString(), any())).thenReturn(false);
        rls("tenant_id = 't-7'");

        OagPipelineContext c = newNode().execute(ctx("ACTION", "关闭合同 1024"));

        assertFalse(c.isSecurityPassed());
        assertEquals("BLOCKED", c.getStatus());
        verify(client).auditQuietly(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("security 策略拒绝（1=0）→ 即使非 ACTION 也 BLOCKED")
    void rlsDenyBlocksQueryIntent() {
        rls("1=0");

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
        rls("tenant_id = 't-7'");

        OagPipelineContext c = newNode().execute(ctx("ACTION", "关闭合同 1024"));

        assertTrue(c.isSecurityPassed());
        assertFalse("BLOCKED".equals(c.getStatus()));
        assertNotNull(c.getRlsFilters());
        assertEquals("tenant_id = 't-7'", c.getRlsFilters().get("whereClause"));
        assertEquals(TENANT, c.getRlsFilters().get("tenantId"));
    }

    /**
     * 恒真形态（#142/B4-3c-2 三态中的 allow-all）：裁决明确"不限制行"，
     * 节点放行并把 {@code 1=1} 原样写进 rlsFilters —— 与"应答漂移给空串"分态。
     */
    @Test
    @DisplayName("对照-2 RLS 返回恒真 1=1 → 通过且 whereClause 原样下传")
    void allowAllClausePassesThrough() {
        rls("1=1");

        OagPipelineContext c = newNode().execute(ctx("QUERY", "查询有效合同"));

        assertTrue(c.isSecurityPassed());
        assertNotNull(c.getRlsFilters());
        assertEquals("1=1", c.getRlsFilters().get("whereClause"),
                "恒真形态必须显式下传，禁在节点里再折叠成「没有条件」");
    }

    /**
     * 翻正（#142/B4-3c-2 / D-B4-17）：原「对照-2 RLS 返回空串 → 通过且不带 whereClause」
     * 是 fail-open 的测试化形态 —— 真实客户端在应答缺 {@code whereClause} 时已归一为
     * 拒绝态，空片段只剩"上游形态漂移"一种来源，按放行断言就是把渗漏钉成契约。
     * 现断言空/null 片段一律 BLOCKED。不留双轨。
     */
    @Test
    @DisplayName("RLS 返回空串/ null（形态漂移）→ BLOCKED，禁当成「无行级限制」")
    void blankRlsClauseIsDriftAndBlocks() {
        OagPipelineContext empty = newNodeWithRls("").execute(ctx("QUERY", "查询有效合同"));
        assertFalse(empty.isSecurityPassed());
        assertEquals("BLOCKED", empty.getStatus());
        assertTrue(String.valueOf(empty.getSecurityBlockReason())
                        .contains(RlsExecutionContract.REASON_WHERE_ABSENT_CODE),
                "阻止原因需指名漂移码: " + empty.getSecurityBlockReason());

        OagPipelineContext nullClause = newNodeWithRls(null).execute(ctx("QUERY", "查询有效合同"));
        assertFalse(nullClause.isSecurityPassed());
        assertEquals("BLOCKED", nullClause.getStatus());
    }

    /**
     * 纵深防御第二道：可用态（FILTERED）却带空片段，在单源读数里构造不出来，
     * 只能由未来的生产者形态漂移产生。节点的 step-4 判腿必须独立于态判定存在，
     * 否则「态＝可用」会被当成「片段一定非空」这一未经证实的推论。
     */
    @Test
    @DisplayName("可用态带空片段（上游漂移）→ 仍 BLOCKED，不因态可用而放行")
    void usableStateWithBlankClauseStillBlocks() {
        AiSecurityEngineClient driftClient = mock(AiSecurityEngineClient.class);
        when(driftClient.applyRlsExecution(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new RlsExecutionContract.Execution(
                        RlsExecutionContract.State.FILTERED, "", List.of(), List.of(),
                        true, false, null));

        OagPipelineContext c = new SecurityCheckerNode(driftClient)
                .execute(ctx("QUERY", "查询有效合同"));

        assertFalse(c.isSecurityPassed());
        assertEquals("BLOCKED", c.getStatus());
        assertTrue(String.valueOf(c.getSecurityBlockReason())
                        .contains(RlsExecutionContract.REASON_WHERE_ABSENT_CODE),
                "漂移腿的对外理由必须指名单源码: " + c.getSecurityBlockReason());
    }

    // ═══════════════ D-B4-26 余项：不可用态与拒绝态分家 ═══════════════

    @Test
    @DisplayName("不可用态 → BLOCKED，阻止原因与审计事件名都带 ECOS-SEC-501（非「无权限」）")
    void unavailableStateBlocksWithAddressableCodeNotPermissionDeny() {
        AiSecurityEngineClient downClient = mock(AiSecurityEngineClient.class);
        when(downClient.applyRlsExecution(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(RlsExecutionContract.Execution.unavailable(SUBJECT + "(connection refused)"));

        OagPipelineContext c = new SecurityCheckerNode(downClient)
                .execute(ctx("QUERY", "查询有效合同"));

        assertFalse(c.isSecurityPassed());
        assertEquals("BLOCKED", c.getStatus(), "不可用仍是 fail-closed，分态不放宽处置");
        assertTrue(String.valueOf(c.getSecurityBlockReason())
                        .contains(RlsExecutionContract.SEC_UNAVAILABLE_CODE),
                "阻止原因需带机读不可用码: " + c.getSecurityBlockReason());
        verify(downClient).auditQuietly(USER, "oag:rls", "apply",
                "UNAVAILABLE:" + RlsExecutionContract.SEC_UNAVAILABLE_CODE);
    }

    @Test
    @DisplayName("反向对照 — 策略拒绝的审计事件名是 DENIED:理由码，不得冒充 UNAVAILABLE")
    void policyDenyAuditsAsDeniedNotUnavailable() {
        rls("1=0");

        OagPipelineContext c = newNode().execute(ctx("QUERY", "查询有效合同"));

        assertEquals("BLOCKED", c.getStatus());
        verify(client).auditQuietly(anyString(), anyString(), anyString(), startsWith("DENIED:"));
        verify(client, never()).auditQuietly(anyString(), anyString(), anyString(),
                startsWith("UNAVAILABLE:"));
        assertFalse(String.valueOf(c.getSecurityBlockReason())
                        .contains(RlsExecutionContract.SEC_UNAVAILABLE_CODE),
                "「你没权限」里出现不可用码＝两态又折回一态");
    }

    private SecurityCheckerNode newNodeWithRls(String clause) {
        AiSecurityEngineClient driftClient = mock(AiSecurityEngineClient.class);
        when(driftClient.applyRlsExecution(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(executionOf(clause));
        return new SecurityCheckerNode(driftClient);
    }
}

