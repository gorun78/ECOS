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

    // ═══════════════ RLS：默认 DENY "1=0" ═══════════════

    @Test
    void rlsDenyOrUnavailableYieldsImpossiblePredicate() {
        when(client.applyRls(eq(ENTITY), eq(ENTITY), any())).thenReturn("1=0");

        assertEquals("1=0", filter.buildRowFilterCondition(ENTITY));
        // 裁决出口必须是 security-engine 客户端（entity 同时作 entityType/tableName，无本地 SQL 拼装）
        verify(client).applyRls(ENTITY, ENTITY, Map.of());
    }

    @Test
    void rlsAllowPassesWhereClauseThroughVerbatim() {
        // 正向对照：放行态片段不被吞掉或改写
        when(client.applyRls(eq(ENTITY), eq(ENTITY), any())).thenReturn("tenant_id = 't-9' AND deleted = '0'");

        assertEquals("tenant_id = 't-9' AND deleted = '0'", filter.buildRowFilterCondition(ENTITY));
    }

    @Test
    void emptyRlsClauseMeansNoPolicyNotDeny() {
        when(client.applyRls(eq(ENTITY), eq(ENTITY), any())).thenReturn("");

        assertEquals("", filter.buildRowFilterCondition(ENTITY), "空串=无行级策略，不得误判为 DENY");
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
