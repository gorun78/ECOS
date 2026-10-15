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
 * <p><b>OPA 源刻意用真实现 + 不可达地址</b>（{@code http://127.0.0.1:1}）：
 * 全仓只有 {@code ecos.rbac} 一个 rego，<b>不存在</b> {@code data.access} 策略，
 * 因此 {@code decide} 的 opa 面必然 fail-close {@code allow=false}。用例集不把它伪造成 ALLOW
 * ——AI 通道的角色用例属 B4-3c（要先补 rego 策略），页面通道只断行/列面。</p>
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

    private PageChannelHarness(V246SeedFixtures seed, RowLevelSecurityServiceImpl rls,
                               ColumnLevelSecurityServiceImpl cls,
                               SecurityDecisionService decisionService) {
        this.seed = seed;
        this.rls = rls;
        this.cls = cls;
        this.decisionService = decisionService;
    }

    static PageChannelHarness create() throws IOException {
        V246SeedFixtures seed = V246SeedFixtures.load();

        RowLevelSecurityDao rlsDao = mock(RowLevelSecurityDao.class);
        when(rlsDao.findPoliciesForUser(anyString(), anyString()))
                .thenAnswer(inv -> seed.selectRlsPolicies(inv.getArgument(0), inv.getArgument(1)));

        ColumnLevelSecurityDao clsDao = mock(ColumnLevelSecurityDao.class);
        when(clsDao.findPoliciesForUser(anyString(), anyString()))
                .thenAnswer(inv -> seed.selectClsPolicies(inv.getArgument(0), inv.getArgument(1)));

        SecurityDecisionDao decisionDao = mock(SecurityDecisionDao.class);
        when(decisionDao.masksForTable(anyString(), any())).thenReturn(List.of());

        // 真实现 + 不可达地址 ⇒ OPA 源 fail-close（不伪造 ALLOW）
        OpaPolicyService opa = new OpaPolicyService("http://127.0.0.1:1", null);

        RowLevelSecurityServiceImpl rls = new RowLevelSecurityServiceImpl(rlsDao);
        ColumnLevelSecurityServiceImpl cls = new ColumnLevelSecurityServiceImpl(clsDao);
        SecurityDecisionService svc = new SecurityDecisionService(rls, cls, opa, decisionDao);
        return new PageChannelHarness(seed, rls, cls, svc);
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

    /** bundle.columns.names（C.2.3 allow 集）→ 列名列表。 */
    @SuppressWarnings("unchecked")
    static List<String> visibleColumns(DecisionBundle bundle) {
        Object names = bundle.columns().get("names");
        return names instanceof List<?> l ? (List<String>) l : List.of();
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
