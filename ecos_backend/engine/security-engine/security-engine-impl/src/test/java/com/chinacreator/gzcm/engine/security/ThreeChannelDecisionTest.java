package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.engine.security.dao.SecurityDecisionDao;
import com.chinacreator.gzcm.engine.security.decision.AssetRef;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import com.chinacreator.gzcm.engine.security.decision.DecisionContext;
import com.chinacreator.gzcm.engine.security.decision.SecurityDecisionService;
import com.chinacreator.gzcm.engine.security.service.ColumnLevelSecurityServiceImpl;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 详细设计-01 F01-02 / W33 — 三通道唯一裁决入口。
 *
 * <p>断言 pages / export / ai 三通道<b>都</b>走 {@code SecurityDecisionService#decide}
 * 一次拿全 rls + columns + masks + opa 四源，且对通道入参与资产接受度做结构性收敛：
 * 同一 {@code decide(asset, purpose, ctx)} 入口同时被三种 purpose 触发，不存在"旁路"路径。</p>
 */
class ThreeChannelDecisionTest {

    private RowLevelSecurityServiceImpl rls;
    private ColumnLevelSecurityServiceImpl cls;
    private OpaPolicyService opa;
    private SecurityDecisionDao dao;
    private SecurityDecisionService svc;

    @BeforeEach
    void setUp() {
        rls = mock(RowLevelSecurityServiceImpl.class);
        cls = mock(ColumnLevelSecurityServiceImpl.class);
        opa = mock(OpaPolicyService.class);
        dao = mock(SecurityDecisionDao.class);
        svc = new SecurityDecisionService(rls, cls, opa, dao);

        // RLS 源：参数化策略（含 : 占位符 + bindings，禁裸拼）
        Map<String, Object> rlsRes = new LinkedHashMap<>();
        Map<String, Object> pred = new LinkedHashMap<>();
        pred.put("predicateTemplate", "(department_id = :userDeptId)");
        pred.put("denyIfEmpty", true);
        pred.put("combine", "AND");
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("name", "userDeptId");
        binding.put("source", "CONTEXT_ORG");
        binding.put("value", "dept-1");
        pred.put("bindings", List.of(binding));
        rlsRes.put("predicate", pred);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userDeptId", null);
        rlsRes.put("params", params);
        when(rls.apply(anyString(), anyString())).thenReturn(rlsRes);

        // CLS 源：allow 模式 2 列可读
        Map<String, Object> clsRes = new LinkedHashMap<>();
        clsRes.put("visibleColumns", List.of("col1", "col2"));
        clsRes.put("blockedColumns", List.of("sensitive_col"));
        when(cls.getColumns(anyString(), anyString(), anyList())).thenReturn(clsRes);

        // OPA 源：allow=true（正常放行）
        Map<String, Object> opaRes = new LinkedHashMap<>();
        opaRes.put("allow", true);
        opaRes.put("policyRef", "ecos.data.access");
        opaRes.put("obligations", List.of());
        when(opa.evaluate(anyString(), anyMap())).thenReturn(opaRes);

        // mask 源：目录无登记 → 空集
        when(dao.masksForTable(anyString(), anyString())).thenReturn(List.of());
    }

    private DecisionContext ctx() {
        return DecisionContext.of("u1", "t1", null, null, List.of("ROLE_USER"), "trace-1");
    }

    private AssetRef asset() {
        return new AssetRef("public", "fc_project_period_fact", List.of("col1", "col2", "sensitive_col"));
    }

    @Test
    @DisplayName("1. 页面通道 — decide(PAGE) 一次拿全四源")
    void pageChannelUsesDecide() {
        DecisionBundle b = svc.decide(asset(), DecisionBundle.Purpose.PAGE, ctx());

        assertNotNull(b.rls());
        assertEquals("(department_id = :userDeptId)", b.rls().get("predicateTemplate"));
        assertNotNull(b.rls().get("bindings"), "RLS 必须含参绑定，禁裸字符串");

        assertNotNull(b.columns());
        assertEquals("allow", b.columns().get("mode"));
        assertIterableEquals(List.of("col1", "col2"),
                (Iterable<?>) b.columns().get("names"), "CLS 应按目录 allow 集裁剪");

        assertNotNull(b.opa());
        assertEquals(true, b.opa().get("allow"));
        assertNotNull(b.decisionId(), "每通道必须有独立 decisionId");
        assertEquals("trace-1", b.traceId(), "traceId 传递保持");
    }

    @Test
    @DisplayName("2. 导出通道 — decide(EXPORT) 同一入口接收入参合并出 bundle")
    void exportChannelUsesDecide() {
        DecisionBundle b = svc.decide(asset(), DecisionBundle.Purpose.EXPORT, ctx());
        assertNotNull(b);
        // 导出语义 = 页面语义 + mask 强制 + 审计上卷；本体 bundle 结构必须一致可复跑
        assertAll(
                () -> assertNotNull(b.rls()),
                () -> assertNotNull(b.columns()),
                () -> assertNotNull(b.masks()),
                () -> assertNotNull(b.opa())
        );
    }

    @Test
    @DisplayName("3. AI 通道 — decide(AI) 同一入口；OPA 拒绝时 bundle 保留 opa.allow=false 供上游护栏")
    void aiChannelUsesDecideOpaDeny() {
        Map<String, Object> opaDeny = new LinkedHashMap<>();
        opaDeny.put("allow", false);
        opaDeny.put("policyRef", "ecos.data.access");
        opaDeny.put("obligations", List.of(Map.of("obligation", "DENIED", "deniedReason", "GUARDRAIL_DENIED")));
        reset(opa);
        when(opa.evaluate(anyString(), anyMap())).thenReturn(opaDeny);

        DecisionBundle b = svc.decide(asset(), DecisionBundle.Purpose.AI, ctx());
        assertEquals(false, b.opa().get("allow"), "AI 通道 OPA 拒绝必须显式透传 upstream");
        assertNotNull(b.opa().get("obligations"), "obligations 必须落回 bundle 供护栏决策");
    }

    @Test
    @DisplayName("4. CLS allow 空集 → bundle.columnsDenied()=true（一列不可读 = DENY 全部列）")
    void clsAllowEmptyIsDenied() {
        Map<String, Object> emptyCls = new LinkedHashMap<>();
        emptyCls.put("visibleColumns", List.of());
        emptyCls.put("blockedColumns", List.of());
        reset(cls);
        when(cls.getColumns(anyString(), anyString(), anyList())).thenReturn(emptyCls);

        DecisionBundle b = svc.decide(asset(), DecisionBundle.Purpose.PAGE, ctx());
        assertTrue(b.columnsDenied(), "CLS allow 空集必须 DENY 全部列，非全放开");
    }

    @Test
    @DisplayName("5. OPA 源抛异常 → bundle 内 allow=false（fail-closed，不放大成整条 throw）")
    void opaExceptionFailsClosed() {
        reset(opa);
        when(opa.evaluate(anyString(), anyMap())).thenThrow(new RuntimeException("OPA DOWN"));
        DecisionBundle b = svc.decide(asset(), DecisionBundle.Purpose.AI, ctx());
        assertEquals(false, b.opa().get("allow"), "OPA 源异常 fail-closed");
        assertNotNull(b.opa().get("obligations"));
    }

    @Test
    @DisplayName("6. assetRef.table 为空 → IllegalArgumentException（端点 400）")
    void invalidAssetRefThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> svc.decide(new AssetRef("public", null, null), DecisionBundle.Purpose.PAGE, ctx()));
        assertThrows(IllegalArgumentException.class,
                () -> svc.decide(null, DecisionBundle.Purpose.PAGE, ctx()));
    }
}
