package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
 *
 * <p><b>#142/B4-3d-2（D-B4-15 / D-B4-16）列面段改腿</b>：列裁决 stub 由
 * {@code allowedColumns}（{@code /cls/columns}）换成 {@code decide(..., purpose=page)}
 * （三通道唯一入口），并新增 {@code never()} 断言钉住"页面通道不再另走分散面"。
 * 四态细分（漂移 / 宇宙未解析 / 源异常 / 策略拒绝）由
 * {@code WorkspacePageChannelDecisionMergeTest} 逐一具名覆盖。</p>
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

    // ═══════════════ CLS：默认 DENY（#142/B4-3d-2 页面通道合流 /decision） ═══════════════
    //
    // 本段在 B4-3d-2 整体改腿：列裁决此前 stub 的是 client.allowedColumns(...)（= /cls/columns 分散面），
    // 而导出通道走 /decision —— 同一主体同一张表的列面由两个端点各答一次，"绕不过"在结构上不成立
    // （差距文档 D-B4-15）。现在页面通道的唯一裁决腿是 decide(purpose=page)，且请求必须带上
    // 结果行的实际列键作为列全集（universe），否则 security 侧只能回 COLUMN_UNIVERSE_UNRESOLVED。

    /** 按 {@code ColumnSurfaceContract} 的权威形态造一版 bundle 的 {@code columns} 段。 */
    private static Map<String, Object> columnSegment(List<String> visible, List<String> blocked,
                                                     String denyReason) {
        Map<String, Object> seg = new LinkedHashMap<>();
        seg.put(ColumnSurfaceContract.KEY_VISIBLE_COLUMNS, visible);
        seg.put(ColumnSurfaceContract.KEY_BLOCKED_COLUMNS, blocked);
        seg.put(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_SOURCE, ColumnSurfaceContract.UNIVERSE_REQUEST);
        seg.put(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_RESOLVED, true);
        if (denyReason != null) seg.put(ColumnSurfaceContract.KEY_CLS_DENY_REASON, denyReason);
        return seg;
    }

    private static Map<String, Object> bundleWithColumns(Map<String, Object> columnSegment) {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("columns", columnSegment);
        return bundle;
    }

    @Test
    void clsDenyReturnsNoRowsAndGoesThroughTheSingleDecisionEntry() {
        List<Map<String, Object>> rows = List.of(
                row("id", "c-1", "salary", 9000),
                row("id", "c-2", "salary", 12000));
        when(client.decide(eq(ENTITY), anyList(), eq(DecisionChannelContract.PURPOSE_PAGE)))
                .thenReturn(bundleWithColumns(columnSegment(List.of(), List.of("salary"),
                        ColumnSurfaceContract.DENY_POLICY)));

        List<Map<String, Object>> filtered = filter.filterColumns(ENTITY, rows);

        assertTrue(filtered.isEmpty(), "CLS 策略拒绝必须置空行集（默认 DENY）");
        assertNotSame(rows, filtered);
        // 列全集＝结果行实际列键（顺序即行内键序），禁传空表（空表＝security 侧宇宙未解析）
        verify(client).decide(ENTITY, List.of("id", "salary"), DecisionChannelContract.PURPOSE_PAGE);
        // 第二腿必须真的断了：/cls/columns 消费者面不再参与页面通道裁决
        verify(client, never()).allowedColumns(anyString(), anyList());
    }

    @Test
    void clsAllowPrunesUnlistedColumnsCaseInsensitively() {
        // 正向对照：证明 DENY 用例不是「恒空」stub 造成的假通过
        List<Map<String, Object>> rows = List.of(row("id", "c-1", "NAME", "张三", "salary", 9000));
        when(client.decide(eq(ENTITY), anyList(), eq(DecisionChannelContract.PURPOSE_PAGE)))
                .thenReturn(bundleWithColumns(columnSegment(List.of("ID", "name"), List.of("salary"), null)));

        List<Map<String, Object>> filtered = filter.filterColumns(ENTITY, rows);

        assertEquals(1, filtered.size());
        Map<String, Object> kept = filtered.get(0);
        assertTrue(kept.containsKey("id") && kept.containsKey("NAME"), "允许列（含大小写差异）须保留");
        assertFalse(kept.containsKey("salary"), "未列出的列必须被裁剪掉");
        verify(client, never()).allowedColumns(anyString(), anyList());
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
