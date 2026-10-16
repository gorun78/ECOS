package com.chinacreator.gzcm.engine.security.rolechannel;

import com.chinacreator.gzcm.engine.security.dao.ColumnLevelSecurityDao;
import com.chinacreator.gzcm.engine.security.dao.RowLevelSecurityDao;
import com.chinacreator.gzcm.engine.security.dao.SecurityDecisionDao;
import com.chinacreator.gzcm.engine.security.decision.AssetRef;
import com.chinacreator.gzcm.engine.security.decision.DecisionBundle;
import com.chinacreator.gzcm.engine.security.decision.DecisionContext;
import com.chinacreator.gzcm.engine.security.decision.SecurityDecisionService;
import com.chinacreator.gzcm.engine.security.service.ColumnLevelSecurityServiceImpl;
import com.chinacreator.gzcm.engine.security.service.OpaPolicyService;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * §4.7.2 页面通道用例的<b>真实裁决链</b>装配（#142/B4-3a）。
 *
 * <p>装配的是<b>生产实现</b>：{@link RowLevelSecurityServiceImpl}（谓词合并/参数化）、
 * {@link ColumnLevelSecurityServiceImpl}（列投影）、{@link SecurityDecisionService}（三通道唯一入口）。
 * 只有<b>数据访问边界</b>（两个 DAO 的 {@code findPoliciesForUser}）被替换成
 * {@link V246SeedFixtures} 的语义选取——该语义已由 dev PG 实跑凭证钉住（见夹具类注释），
 * 不是随手写的 stub。</p>
 *
 * <p><b>OPA 源默认用真实现 + 不可达地址</b>（{@code http://127.0.0.1:1}）：
 * 页面/导出通道的行列面用例不依赖护栏结论，让它如实落在<b>不可用态</b>
 * （{@code policyState=OPA_UNAVAILABLE}）而不是伪造 ALLOW。
 * <b>订正（B4-3c）</b>：本类原注释写"全仓不存在 data.access 策略，因此必然 fail-close"，
 * 现状是策略名已合规为单源 {@code data_access}、rego 已落仓
 * （{@code ecos-docker/opa-policies/data_access.rego}）；护栏面的真值用例走
 * {@link #create(String)} 接录制型 oracle 回放（见 {@code OpaOracleReplaySupport}），
 * 仍不伪造。</p>
 *
 * <p>mask 源 stub 成空集：脱敏强制面已由既有
 * {@code MaskEnforcementOnExportAndAiTest} 覆盖，本批不重复断言，也不冒充"已覆盖"。</p>
 */
final class PageChannelHarness {

    /** V246 策略表里的 table_name 带 schema 限定，故 AssetRef 用 public 包装、table 原样传。 */
    static final String SCHEMA = "public";

    private final V246SeedFixtures seed;
    private final RowLevelSecurityServiceImpl rls;
    private final ColumnLevelSecurityServiceImpl cls;
    private final SecurityDecisionService decisionService;
    private final OpaPolicyService opa;

    private PageChannelHarness(V246SeedFixtures seed, RowLevelSecurityServiceImpl rls,
                               ColumnLevelSecurityServiceImpl cls,
                               SecurityDecisionService decisionService, OpaPolicyService opa) {
        this.seed = seed;
        this.rls = rls;
        this.cls = cls;
        this.decisionService = decisionService;
        this.opa = opa;
    }

    static PageChannelHarness create() throws IOException {
        return create("http://127.0.0.1:1");
    }

    /**
     * 指定 OPA base-url 的装配（护栏面用例用）。
     *
     * <p>地址由调用方给出，其余链路（RLS/CLS/decide 入口）与默认装配<b>完全同一实例构造</b> ——
     * oracle 回放时唯一变化的就是护栏源的 HTTP 对端，禁在本方法里另加任何判定分支。</p>
     */
    static PageChannelHarness create(String opaBaseUrl) throws IOException {
        V246SeedFixtures seed = V246SeedFixtures.load();

        RowLevelSecurityDao rlsDao = mock(RowLevelSecurityDao.class);
        when(rlsDao.findPoliciesForUser(anyString(), anyString()))
                .thenAnswer(inv -> seed.selectRlsPolicies(inv.getArgument(0), inv.getArgument(1)));

        ColumnLevelSecurityDao clsDao = mock(ColumnLevelSecurityDao.class);
        when(clsDao.findPoliciesForUser(anyString(), anyString()))
                .thenAnswer(inv -> seed.selectClsPolicies(inv.getArgument(0), inv.getArgument(1)));

        SecurityDecisionDao decisionDao = mock(SecurityDecisionDao.class);
        when(decisionDao.masksForTable(anyString(), any())).thenReturn(List.of());

        // 真实现 + 调用方给定的对端：默认不可达 ⇒ 如实 UNAVAILABLE；oracle 回放 ⇒ 真引擎应答形态
        OpaPolicyService opa = new OpaPolicyService(opaBaseUrl, null);

        RowLevelSecurityServiceImpl rls = new RowLevelSecurityServiceImpl(rlsDao);
        ColumnLevelSecurityServiceImpl cls = new ColumnLevelSecurityServiceImpl(clsDao);
        SecurityDecisionService svc = new SecurityDecisionService(rls, cls, opa, decisionDao);
        return new PageChannelHarness(seed, rls, cls, svc, opa);
    }

    /** 走生产 RLS 全量返回（含 policies 名单与 params），用于断"命中了哪条策略"。 */
    Map<String, Object> applyRls(String table, String userId) {
        return rls.apply(table, userId);
    }

    /** 走生产 CLS 全量返回（visible/blocked/policies）。 */
    Map<String, Object> applyCls(String table, String userId, List<String> allColumns) {
        return cls.getColumns(table, userId, allColumns);
    }

    /** 三通道唯一入口（本用例集只取 PAGE）。 */
    DecisionBundle decidePage(String table, List<String> columns, String userId) {
        return decisionService.decide(new AssetRef(SCHEMA, table, columns),
                DecisionBundle.Purpose.PAGE, context(userId));
    }

    DecisionBundle decide(String table, List<String> columns, String userId, DecisionBundle.Purpose purpose) {
        return decisionService.decide(new AssetRef(SCHEMA, table, columns), purpose, context(userId));
    }

    /**
     * 原样透传装配（护栏面用例用）：资产定位符与上下文（角色/组织域/主体）由调用方给定，
     * 为的是让<b>生产 safeOpa 发出的请求体</b>与录制 oracle 时的输入逐字可比。
     */
    DecisionBundle decide(AssetRef asset, DecisionContext ctx, DecisionBundle.Purpose purpose) {
        return decisionService.decide(asset, purpose, ctx);
    }

    /**
     * 直连护栏源（同一 {@link OpaPolicyService} 实例，非另造客户端）：
     * 供 {@code OpaDocumentShapeTest} 逐例校验<b>策略文档应答形态</b>，以及带
     * {@code input.environment} 的 AI 工具载荷形态输入。
     *
     * <p><b>订正（#142/B4-3d-3）</b>：本方法原注释的理由是"该输入只有 AI 工具裁决载荷会发，
     * {@code safeOpa} 不发，因此不能强塞进行列面入口"。现状是 {@code safeOpa} 已按
     * {@code DataScopeContract} 写出 {@code resource.dataScope}，故
     * <b>行列面断言一律走 {@link #decide(AssetRef, DecisionContext, DecisionBundle.Purpose)}</b>；
     * 用本方法回放带 dataScope 的例会绕开唯一入口（D-B4-20 的原缺陷形态），禁再这么做。</p>
     */
    Map<String, Object> evaluateGuardrail(Map<String, Object> input) {
        return opa.evaluate(OpaPolicyService.POLICY_DATA_ACCESS, input);
    }

    /** 显式上下文（不经 seed 角色归属单源）；仅护栏面用例使用，行列面仍走 {@link #context(String)}。 */
    DecisionContext explicitContext(String userId, String tenantId, String orgId, List<String> roles) {
        return DecisionContext.of(userId, tenantId, orgId, null, List.copyOf(roles), "trace-" + userId);
    }

    /** 上下文角色取夹具的角色归属单源，保证与 seed 的 role_id 同源。 */
    DecisionContext context(String userId) {
        return DecisionContext.of(userId, "t-demo", null, null,
                List.copyOf(V246SeedFixtures.rolesFor(userId)), "trace-" + userId);
    }

    V246SeedFixtures seed() {
        return seed;
    }

    @SuppressWarnings("unchecked")
    static List<String> visible(Map<String, Object> clsResult) {
        Object v = clsResult.get("visibleColumns");
        return v instanceof List<?> l ? (List<String>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    static List<String> blocked(Map<String, Object> clsResult) {
        Object v = clsResult.get("blockedColumns");
        return v instanceof List<?> l ? (List<String>) l : List.of();
    }

    /** apply() 里的命中策略清单（name/filterExpr/priority/sourceKind）。 */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> policies(Map<String, Object> rlsResult) {
        Object v = rlsResult.get("policies");
        return v instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
    }

    static String predicateTemplate(Map<String, Object> rlsResult) {
        Object pred = rlsResult.get("predicate");
        if (pred instanceof Map<?, ?> m) {
            Object t = m.get("predicateTemplate");
            return t == null ? null : String.valueOf(t);
        }
        return null;
    }

    /**
     * bundle 的可读列集 —— <b>只走单源访问器</b>（#142/B4-3d-2，差距 D-B4-16 订正）。
     *
     * <p>原形态是 {@code bundle.columns().get("names")}：那是在列面上承认<b>第三种叫法</b>
     * （生产者叫 {@code visibleColumns}、legacy 契约册叫 {@code allowedColumns}、bundle 又叫
     * {@code names}），并且空集四种事实（策略清空 / 宇宙未解析 / 源异常 / 键面漂移）在键面上
     * 同形，用例读不出区别。{@link DecisionBundle#columnsVisible()} 内部委托
     * {@code ColumnSurfaceContract.read}，与三消费者、生产者共用同一份读取逻辑。</p>
     */
    static List<String> visibleColumns(DecisionBundle bundle) {
        return bundle.columnsVisible();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> bindingsOf(Map<String, Object> rlsResult) {
        Object pred = rlsResult.get("predicate");
        if (pred instanceof Map<?, ?> m && m.get("bindings") instanceof List<?> l) {
            return (List<Map<String, Object>>) l;
        }
        return List.of();
    }

    static boolean denyIfEmpty(Map<String, Object> rlsResult) {
        Object pred = rlsResult.get("predicate");
        return pred instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("denyIfEmpty"));
    }
}
