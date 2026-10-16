package com.chinacreator.gzcm.workspace.security;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * H10-T1b（PMO-74 N7/N16 验收）— {@link AbacQueryFilter} 默认 DENY 行为用例。
 *
 * <p>证明两件事：(1) 行/列裁决全部经 {@link WorkspaceSecurityEngineClient} 走
 * security-engine REST（不再本地解析 td_abac_policy）；(2) security-engine
 * 不可用/拒绝时 fail-closed —— 列裁剪返回空行集、行过滤返回 "1=0"，
 * 绝不回退为「放行原行 / 空条件」。</p>
 *
 * <p><b>#142/B4-3c-2（D-B4-17）RLS 段整体翻正</b>：出口由"只取片段的
 * {@code applyRls}"改为"片段+参数成对的 {@link RlsFilter}"，并把原用例
 * {@code emptyRlsClauseMeansNoPolicyNotDeny}（空串＝无策略＝放行）翻正为
 * {@link #emptyClauseIsNowDenyNotNoPolicy()}——空串/缺字段是应答形态漂移，必须拒绝。
 * 旧断言本身就是 fail-open 的测试化形态，按其 javadoc 自述的承诺删除并翻正，不留双轨。</p>
 */
class AbacQueryFilterDenyTest {

    private static final String ENTITY = "Contract";

    private final WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
    private final AbacQueryFilter filter = new AbacQueryFilter(client);

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    // ═══════════════ CLS：默认 DENY ═══════════════

    @Test
    void clsDenyOrUnavailableReturnsNoRowsInsteadOfOriginalRows() {
        List<Map<String, Object>> rows = List.of(
                row("id", "c-1", "salary", 9000),
                row("id", "c-2", "salary", 12000));
        when(client.allowedColumns(eq(ENTITY), anyList())).thenReturn(List.of());

        List<Map<String, Object>> filtered = filter.filterColumns(ENTITY, rows);

        assertTrue(filtered.isEmpty(), "CLS 拒绝/security 不可用必须置空行集（默认 DENY）");
        assertNotSame(rows, filtered);
        verify(client).allowedColumns(eq(ENTITY), anyList());
    }

    @Test
    void clsAllowPrunesUnlistedColumnsCaseInsensitively() {
        // 正向对照：证明 DENY 用例不是「恒空」stub 造成的假通过
        List<Map<String, Object>> rows = List.of(row("id", "c-1", "NAME", "张三", "salary", 9000));
        when(client.allowedColumns(eq(ENTITY), anyList())).thenReturn(List.of("ID", "name"));

        List<Map<String, Object>> filtered = filter.filterColumns(ENTITY, rows);

        assertEquals(1, filtered.size());
        Map<String, Object> kept = filtered.get(0);
        assertTrue(kept.containsKey("id") && kept.containsKey("NAME"), "允许列（含大小写差异）须保留");
        assertFalse(kept.containsKey("salary"), "未列出的列必须被裁剪掉");
    }

    @Test
    void emptyRowSetShortCircuitsWithoutCallingSecurityEngine() {
        List<Map<String, Object>> empty = List.of();
        assertSame(empty, filter.filterColumns(ENTITY, empty));
        assertNull(filter.filterColumns(ENTITY, null));
        verifyNoInteractions(client);
    }

    // ═══════════════ RLS：执行契约成对下推 + 默认 DENY（#142/B4-3c-2，D-B4-17） ═══════════════
    //
    // 本段用例在 B4-3c-2 被整体翻正：原先 stub 的是 applyRls(...)（只取片段），并断言
    // 「空串=无行级策略，不得误判为 DENY」。那句话正是 fail-open 的测试化形态——
    // 生产者从未发 whereClause，消费者拿到的恒是空串，于是行过滤恒等于不过滤。
    // 现在生产链出口是 applyRlsFilter(...)（RlsFilter：片段 + 同序参数），
    // 「空串/缺字段」一律是拒绝态；"不限制行"只由恒真 1=1 表达。

    @Test
    void rlsDenyYieldsImpossiblePredicateAndCarriesReason() {
        when(client.applyRlsFilter(eq(ENTITY), eq(ENTITY), any()))
                .thenReturn(RlsFilter.deny("RLS_NO_POLICY_MATCHED", true, List.of()));

        RlsFilter rls = filter.buildRowFilter(ENTITY);
        assertTrue(rls.denied());
        assertEquals("RLS_NO_POLICY_MATCHED", rls.denyReason(), "策略侧原因必须可见，禁折叠成「无数据」");
        assertEquals("1=0", rls.clauseForSplice());
        assertTrue(rls.paramsForSplice().isEmpty());
        // 兼容出口（片段单值）与成对出口必须同源，且拒绝态不再是空串
        assertEquals("1=0", filter.buildRowFilterCondition(ENTITY));
        // 裁决出口必须是 security-engine 客户端（entity 同时作 entityType/tableName，无本地 SQL 拼装）。
        // 兼容出口 buildRowFilterCondition 委托成对出口 buildRowFilter，两者各咨询一次 ⇒ 两次调用；
        // 这里断言"次数=2 且入参完全一致"，正是"两个出口同源、没有第二条腿各自解析应答"的证据。
        verify(client, times(2)).applyRlsFilter(ENTITY, ENTITY, Map.of());
    }

    @Test
    void parameterizedPredicateAndParamsSurviveTogether() {
        // 正向对照：完整参数对不被吞掉、不改写、不拆散（拆散＝悬空占位符，撞 IR05）
        when(client.applyRlsFilter(eq(ENTITY), eq(ENTITY), any())).thenReturn(RlsFilter.fromData(
                Map.of("whereClause", "(department_id = ?)", "whereParams", List.of("d-1")), ENTITY));

        RlsFilter rls = filter.buildRowFilter(ENTITY);
        assertFalse(rls.denied());
        assertFalse(rls.allowAll());
        assertEquals("(department_id = ?)", rls.clauseForSplice());
        assertEquals(List.of("d-1"), rls.paramsForSplice());
    }

    @Test
    void allowAllIsTheOnlyFormThatSplicesNothing() {
        when(client.applyRlsFilter(eq(ENTITY), eq(ENTITY), any()))
                .thenReturn(RlsFilter.unrestricted());

        RlsFilter rls = filter.buildRowFilter(ENTITY);
        assertTrue(rls.allowAll());
        assertEquals("", rls.clauseForSplice(), "恒真＝不拼条件（与「未给谓词」分态）");
        // 兼容出口仍回片段原文 1=1：拼进 WHERE 无害且语义诚实（"裁决明确不限制"），
        // 调用方若靠 isEmpty() 判"无过滤"已不成立——由 RlsExecutionContractGateTest 钉住禁回归
        assertEquals("1=1", filter.buildRowFilterCondition(ENTITY));
    }

    @Test
    void emptyClauseIsNowDenyNotNoPolicy() {
        // 翻正 #142/B4-3b 前的 emptyRlsClauseMeansNoPolicyNotDeny：空串＝应答未发执行契约＝拒绝
        when(client.applyRlsFilter(eq(ENTITY), eq(ENTITY), any()))
                .thenReturn(RlsFilter.fromData(Map.of("denyIfEmpty", true), ENTITY));

        RlsFilter rls = filter.buildRowFilter(ENTITY);
        assertTrue(rls.denied(), "缺 whereClause 必须拒绝（旧口径「空串=无策略放行」即 D-B4-17 fail-open）");
        assertTrue(rls.denyReason().contains(RlsFilter.DRIFT_ABSENT));
        assertEquals("1=0", filter.buildRowFilterCondition(ENTITY));
    }

    @Test
    void nullContractFromClientDeniesInsteadOfThrowingNpe() {
        // 客户端返回 null＝形态漂移。这里不能漏成 NPE：500 会把堆栈外泄，
        // 且把"可解释的 DENY"变成"不可解释的崩溃"（分态纪律 §4.5.4）
        when(client.applyRlsFilter(eq(ENTITY), eq(ENTITY), any())).thenReturn(null);

        RlsFilter rls = filter.buildRowFilter(ENTITY);
        assertTrue(rls.denied());
        assertTrue(rls.denyReason().contains(RlsFilter.DRIFT_ABSENT));
        assertEquals("1=0", filter.buildRowFilterCondition(ENTITY));
    }

    // ═══════════════ 兼容入口：不得复活本地策略推导 ═══════════════

    @Test
    void legacyLocalPolicyApisStayEmptyAndTouchNothing() {
        // getHiddenColumns / invalidateCache 是 H10-T1 保留签名的兼容入口：
        // 若将来回退成读 td_abac_policy，本用例会因「无 REST 交互仍返回非空」而失败
        assertTrue(filter.getHiddenColumns(ENTITY).isEmpty());
        filter.invalidateCache();
        verifyNoInteractions(client);
    }
}
