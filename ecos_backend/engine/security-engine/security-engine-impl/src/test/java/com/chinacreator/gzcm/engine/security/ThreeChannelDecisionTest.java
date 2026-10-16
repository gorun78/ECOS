package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.engine.security.dao.SecurityDecisionDao;
import com.chinacreator.gzcm.engine.security.decision.AssetRef;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import com.chinacreator.gzcm.engine.security.decision.DecisionContext;
import com.chinacreator.gzcm.engine.security.decision.SecurityDecisionService;
import com.chinacreator.gzcm.engine.security.service.ColumnLevelSecurityServiceImpl;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import com.chinacreator.gzcm.engine.security.rls.RlsSubjectContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
        // 执行契约段（whereClause/whereParams）= #142/B4-3c-2 D-B4-17 生产者新增字段，
        // 本用例同时钉「bundle 必须把它透传给消费者」——消费者读不到它就等于不过滤。
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
        rlsRes.put("whereClause", "(department_id = ?)");
        rlsRes.put("whereParams", List.of("dept-1"));
        rlsRes.put("unresolvedBindings", List.of());
        rlsRes.put("noPolicyMatched", false);
        // B4-3c-2 起 decide() 走上下文形态的 apply（组织域/密级只服务端派生），
        // 旧的 apply(table, userId) 只保留兼容入口，不再是裁决腿。
        when(rls.apply(anyString(), any(RlsSubjectContext.class))).thenReturn(rlsRes);

        // CLS 源：allow 模式 2 列可读
        Map<String, Object> clsRes = new LinkedHashMap<>();
        clsRes.put("visibleColumns", List.of("col1", "col2"));
        clsRes.put("blockedColumns", List.of("sensitive_col"));
        when(cls.getColumns(anyString(), anyString(), anyList())).thenReturn(clsRes);

        // OPA 源：护栏已评估且放行（应答形态与 B4-3c 后的 OpaPolicyService 单源一致）
        Map<String, Object> opaRes = new LinkedHashMap<>();
        opaRes.put("allow", true);
        opaRes.put("policyState", OpaPolicyService.STATE_ALLOW);
        opaRes.put("policyRef", "ecos." + OpaPolicyService.POLICY_DATA_ACCESS);
        opaRes.put("obligations", List.of());
        when(opa.evaluate(eq(OpaPolicyService.POLICY_DATA_ACCESS), anyMap())).thenReturn(opaRes);

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
        // D-B4-17：裁决入口必须自带可执行片段，消费者不必再回读 /rls/apply（第二裁决腿）
        assertEquals("(department_id = ?)", b.rls().get("whereClause"));
        assertEquals(List.of("dept-1"), b.rls().get("whereParams"));
        assertEquals(Boolean.FALSE, b.rls().get("noPolicyMatched"));
        assertNull(b.rls().get("rlsDenyReason"));

        // 主体属性只服务端派生：userId/tenantId 来自 DecisionContext，org 无单源时如实为 null
        ArgumentCaptor<RlsSubjectContext> subject = ArgumentCaptor.forClass(RlsSubjectContext.class);
        verify(rls).apply(eq("fc_project_period_fact"), subject.capture());
        assertEquals("u1", subject.getValue().userId());
        assertEquals("t1", subject.getValue().tenantId());
        assertNull(subject.getValue().orgId(), "D-B4-23：现网无组织域单源，禁在裁决入口伪造 orgId");

        assertNotNull(b.columns());
        assertEquals("allow", b.columns().get("mode"));
        // 单源访问器为唯一读数面；names 只作兼容别名，必须与权威可读集逐字节等值
        // （#142/B4-3d-2，D-B4-16：别名分叉＝列面出现两套真值，正是 D-B4-25 的成因）
        assertEquals(List.of("col1", "col2"), b.columnsVisible(), "CLS 应按目录 allow 集裁剪");
        assertIterableEquals(b.columnsVisible(),
                (Iterable<?>) b.columns().get("names"), "兼容别名 names 不得与单源可读集分叉");
        assertEquals(ColumnSurfaceContract.UNIVERSE_REQUEST,
                b.columnUniverseSource(), "列全集出处必须落证据（AssetRef.columns 显式请求）");
        assertTrue(b.columnUniverseResolved());
        assertNull(b.clsDenyReason(), "可用列面禁带拒绝理由");

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
        opaDeny.put("policyState", OpaPolicyService.STATE_DENY);
        opaDeny.put("policyRef", "ecos." + OpaPolicyService.POLICY_DATA_ACCESS);
        opaDeny.put("obligations", List.of(Map.of("obligation", "DENIED", "deniedReason", "GUARDRAIL_DENIED")));
        reset(opa);
        when(opa.evaluate(eq(OpaPolicyService.POLICY_DATA_ACCESS), anyMap())).thenReturn(opaDeny);

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

    @Test
    @DisplayName("7. RLS 源抛异常 → bundle.rls 发 1=0 拒绝片段（源异常不得表现为「没有 whereClause」）")
    void rlsSourceFailureYieldsDenyShapeNotMissingField() {
        reset(rls);
        when(rls.apply(anyString(), any(RlsSubjectContext.class)))
                .thenThrow(new RuntimeException("policy store down"));

        DecisionBundle b = svc.decide(asset(), DecisionBundle.Purpose.PAGE, ctx());

        assertEquals("1=0", b.rls().get("whereClause"),
                "D-B4-17：异常态必须与正常态同形，否则消费者按「字段缺失」走放行分支");
        assertEquals("RLS_SOURCE_ERROR", b.rls().get("rlsDenyReason"));
        assertEquals(List.of(), b.rls().get("whereParams"));
        assertEquals(Boolean.TRUE, b.rls().get("noPolicyMatched"));
    }

    @Test
    @DisplayName("8. RLS 应答缺 predicate 段（键漂移）→ 兜底段仍给 1=0，禁「漂移=放行」")
    void missingPredicateSegmentFallsBackToDeny() {
        reset(rls);
        when(rls.apply(anyString(), any(RlsSubjectContext.class))).thenReturn(new LinkedHashMap<>());

        DecisionBundle b = svc.decide(asset(), DecisionBundle.Purpose.EXPORT, ctx());

        assertEquals("1=0", b.rls().get("whereClause"));
        assertEquals("RLS_PREDICATE_SEGMENT_ABSENT", b.rls().get("rlsDenyReason"));
        assertEquals("1=1", b.rls().get("predicateTemplate"),
                "兼容字段仍给旧值（钉现状供日志核对，非认可：执行侧只认 whereClause）");
    }
}
