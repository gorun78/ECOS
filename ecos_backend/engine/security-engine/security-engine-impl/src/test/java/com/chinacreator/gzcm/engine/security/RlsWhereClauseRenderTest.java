package com.chinacreator.gzcm.engine.security;

import com.chinacreator.gzcm.engine.security.dao.RowLevelSecurityDao;
import com.chinacreator.gzcm.engine.security.rls.PredicateBinding;
import com.chinacreator.gzcm.engine.security.rls.RlsPredicate;
import com.chinacreator.gzcm.engine.security.rls.RlsPredicateRenderer;
import com.chinacreator.gzcm.engine.security.rls.RlsPredicateValidator;
import com.chinacreator.gzcm.engine.security.rls.RlsSubjectContext;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 详细设计-01 C.2.2 / C.1 [2] —— {@code /rls/apply} 必须交付<b>可绑定执行契约</b>
 * （{@code whereClause} JDBC {@code ?} 片段 + 同序 {@code whereParams}），
 * 未解析即拒绝（#142/B4-3c-2，收口差距 D-B4-17 / D-B4-23）。
 *
 * <p><b>本用例族存在的理由（fail-open 反例的钉死）</b>：三个引擎侧消费者
 * （workspace / ontology / ai）读的都是应答里 {@code whereClause} 字段，而生产者
 * <b>从来没发过这个字段</b> ⇒ 取到 {@code null} ⇒ 各自返回空串 ⇒ 行级安全在页面通道上
 * 恒等于"不加过滤"。因此这里不只测"渲染对"，更测<b>渲染不出来时必须是拒绝形态</b>
 * （{@code 1=0} + {@code rlsDenyReason}），禁把"上下文没接上"降级成"没过滤"。</p>
 *
 * <p><b>反假绿三处对照</b>：①{@link #paramsFollowTemplateTokenOrderNotBindingListOrder()}
 * 用清单序与 token 序<b>刻意错位</b>，若实现按清单序发参会把值绑到别的列上；
 * ②{@link #allowAllPolicyIsDistinguishedFromNoPredicate()} 用 {@code rls-finance-all}
 * 真实策略证明 {@code 1=1}（恒真）与"零命中"（拒绝）是两态，二者模板文本同为 {@code 1=1}
 * 时只有 {@code segments} 能区分；③{@link #unresolvedContextBindingYieldsDenyNotNullParam()}
 * 证明 {@code CONTEXT_ORG} 未解析时参数表<b>非空即拒绝</b>，不会出现 {@code col = ?} 配
 * {@code null} 的"看起来能用"片段。每条拒绝用例都过
 * {@link #assertDenyShape}/{@link #assertRenderShape} 两组不变量。</p>
 */
class RlsWhereClauseRenderTest {

    private static final String TABLE = "ecos_dw.fc_cost_allocation_fact";

    private final RlsPredicateRenderer renderer = new RlsPredicateRenderer();

    // ═══════════════ 放行态：渲染出 JDBC 可绑定片段 ═══════════════

    @Test
    @DisplayName("1. CONTEXT_ORG 已解析 → 标量占位符渲染成 ? 并把值放参数表（IR05 禁内联字面量）")
    void scalarContextBindingRendersPlaceholderAndParam() {
        RlsPredicate p = merged("(department_id = :userDeptId)",
                List.of(binding("userDeptId", PredicateBinding.SRC_CONTEXT_ORG, null)));

        RlsPredicateRenderer.Rendered r = renderer.render(p, new RlsSubjectContext("u-1", "t-1", "d-7", null));

        assertRenderShape(r);
        assertEquals("(department_id = ?)", r.whereClause());
        assertEquals(List.of("d-7"), r.whereParams());
        assertFalse(r.whereClause().contains("d-7"), "绑定值不得内联进 SQL 文本（IR05）");
        assertFalse(r.allowAll());
    }

    @Test
    @DisplayName("2. V246 归属子查询模板 → 子查询原样保留、仅 :name 换成 ?（select 不在词法黑名单）")
    void subqueryTemplateKeepsPlaceholderInsideInList() {
        String template = "(project_id IN (SELECT project_id FROM ecos_dw.biz_project_attribution"
                + " WHERE department_id = :userDeptId))";
        RlsPredicate p = merged(template,
                List.of(binding("userDeptId", PredicateBinding.SRC_CONTEXT_ORG, null)));

        RlsPredicateRenderer.Rendered r = renderer.render(p, new RlsSubjectContext("u-1", "t-1", "d-3", null));

        assertRenderShape(r);
        assertEquals("(project_id IN (SELECT project_id FROM ecos_dw.biz_project_attribution"
                + " WHERE department_id = ?))", r.whereClause());
        assertEquals(List.of("d-3"), r.whereParams());
    }

    @Test
    @DisplayName("3. rls-finance-all（1=1 且有命中段）= 恒真，与零命中策略分态（反「模板文本当命中证据」）")
    void allowAllPolicyIsDistinguishedFromNoPredicate() {
        RlsPredicate allowAll = new RlsPredicate();
        allowAll.setPredicateTemplate("(1=1)");
        allowAll.setSegments(List.of("(1=1)"));
        RlsPredicate none = new RlsPredicate();
        none.setPredicateTemplate("1=1");
        none.setSegments(List.of());

        RlsPredicateRenderer.Rendered granted = renderer.render(allowAll, RlsSubjectContext.ofUserId("u-1"));
        RlsPredicateRenderer.Rendered rejected = renderer.render(none, RlsSubjectContext.ofUserId("u-1"));

        assertRenderShape(granted);
        assertTrue(granted.allowAll(), "segments 非空的 1=1 必须判为恒真而非拒绝");
        assertDenyShape(rejected, "RLS_NO_POLICY_MATCHED");
        assertTrue(rejected.noPolicyMatched());
        assertNotEquals(granted.whereClause(), rejected.whereClause());
    }

    @Test
    @DisplayName("4. STATIC_LIST → 逐项展开 N 个 ? 且参数同序（Validator 上限 50 的渲染侧兑现）")
    void staticListExpandsToOnePlaceholderPerItem() {
        RlsPredicate p = merged("(status IN (:statusList))",
                List.of(binding("statusList", PredicateBinding.SRC_STATIC_LIST, List.of("DRAFT", "RUN", "DONE"))));

        RlsPredicateRenderer.Rendered r = renderer.render(p, RlsSubjectContext.ofUserId("u-1"));

        assertRenderShape(r);
        assertEquals("(status IN (?, ?, ?))", r.whereClause());
        assertEquals(List.of("DRAFT", "RUN", "DONE"), r.whereParams());
    }

    @Test
    @DisplayName("5. 参数序 = 模板 token 出现序，不是 bindings 清单序（多策略合并必错位）")
    void paramsFollowTemplateTokenOrderNotBindingListOrder() {
        // mergePolicies 按"策略段声明顺序"追加 bindings，模板里 token 从左到右是 tenant 再 user；
        // 这里故意让清单序为 [userId, tenantId]，按清单发参就会把用户 id 绑到 tenant_id 列上。
        RlsPredicate p = merged("(tenant_id = :tenantId) AND (owner = :userId)",
                List.of(binding("userId", PredicateBinding.SRC_CONTEXT_USER_ID, null),
                        binding("tenantId", PredicateBinding.SRC_CONTEXT_TENANT_ID, null)));

        RlsPredicateRenderer.Rendered r = renderer.render(p, new RlsSubjectContext("u-9", "t-3", null, null));

        assertRenderShape(r);
        assertEquals("(tenant_id = ?) AND (owner = ?)", r.whereClause());
        assertEquals(List.of("t-3", "u-9"), r.whereParams(),
                "D-B4-17 关键对照：参数顺序必须随模板 token 流，而非绑定清单序");
    }

    // ═══════════════ 拒绝态：渲染不出来一律 1=0 ═══════════════

    @Test
    @DisplayName("6. CONTEXT_ORG 无单源未解析 → 1=0 + rlsDenyReason，禁发 col = ? 配 null 参数（D-B4-23）")
    void unresolvedContextBindingYieldsDenyNotNullParam() {
        // dev 实测：td_user 17 列无 org_id、仓内无 td_org ⇒ orgId 恒 null（见 RlsSubjectContext javadoc）
        RlsPredicate p = merged("(department_id = :userDeptId)",
                List.of(binding("userDeptId", PredicateBinding.SRC_CONTEXT_ORG, null)));

        RlsPredicateRenderer.Rendered r = renderer.render(p, new RlsSubjectContext("u-1", "t-1", null, null));

        assertDenyShape(r, "RLS_CONTEXT_UNRESOLVED");
        assertEquals(List.of("userDeptId"), r.unresolvedBindings());
        assertFalse(r.allowAll());
        assertFalse(r.whereParams().contains("userDeptId"), "未解析的名字不得当成参数值下发");
    }

    @Test
    @DisplayName("7. 上下文属性全缺（subjectCtx=null）→ 拒绝而非「无绑定所以放行」")
    void nullSubjectContextIsNotTreatedAsUnrestricted() {
        RlsPredicate p = merged("(owner = :userId)",
                List.of(binding("userId", PredicateBinding.SRC_CONTEXT_USER_ID, null)));

        assertDenyShape(renderer.render(p, null), "RLS_CONTEXT_UNRESOLVED");
    }

    @Test
    @DisplayName("8. 谓词对象缺失 → 拒绝（禁把 null 渲染成空串，那正是消费者的 fail-open 入口）")
    void nullPredicateYieldsDeny() {
        RlsPredicateRenderer.Rendered r = renderer.render(null, RlsSubjectContext.ofUserId("u-1"));

        assertDenyShape(r, "RLS_MERGED_PREDICATE_ABSENT");
        assertTrue(r.noPolicyMatched());
    }

    @Test
    @DisplayName("9. 有段但模板空白 → 拒绝（RLS_TEMPLATE_BLANK，noPolicyMatched=false 保分态）")
    void blankTemplateWithSegmentsYieldsDeny() {
        RlsPredicate p = new RlsPredicate();
        p.setPredicateTemplate("   ");
        p.setSegments(List.of("   "));

        RlsPredicateRenderer.Rendered r = renderer.render(p, RlsSubjectContext.ofUserId("u-1"));

        assertDenyShape(r, "RLS_TEMPLATE_BLANK");
        assertFalse(r.noPolicyMatched(), "「有策略但模板空」与「零策略」不得混成一态");
    }

    @Test
    @DisplayName("10. 模板含危险语法 → 走单源校验器拒绝（不另立第二套词法，也不降级拼接）")
    void dangerousTemplateRejectedByValidatorLexicon() {
        String template = "(owner = :x OR 1=1; DROP TABLE td_user --)";
        RlsPredicate p = merged(template, List.of(binding("x", PredicateBinding.SRC_CONTEXT_USER_ID, null)));

        RlsPredicateRenderer.Rendered r = renderer.render(p, new RlsSubjectContext("u-1", null, null, null));

        assertDenyShape(r, "RLS_TEMPLATE_REJECTED");
        // 校验器确实在链路上：同一模板直接 validate 必须抛（证明拒绝不是渲染器自己吞了值）
        assertThrows(RlsPredicateValidator.TemplateRejectedException.class,
                () -> new RlsPredicateValidator().validate(template, p.getBindings()));
    }

    @Test
    @DisplayName("11. 占位符缺对应绑定 → 校验器拒绝（未绑定占位符不得透传进 SQL）")
    void unboundPlaceholderIsRejectedNotPassedThrough() {
        String template = "(department_id = :userDeptId)";
        RlsPredicate p = merged(template, List.of());

        assertDenyShape(renderer.render(p, new RlsSubjectContext("u-1", "t-1", "d-1", null)),
                "RLS_TEMPLATE_REJECTED");
    }

    @Test
    @DisplayName("12. PROJECT_ATTRIBUTION_SUBQUERY（值是 SQL 片段、无法绑定）→ 拒绝")
    void subquerySourceIsNotRenderable() {
        RlsPredicate p = merged("(project_id IN (:attribution))",
                List.of(binding("attribution", PredicateBinding.SRC_PROJECT_ATTRIBUTION,
                        "SELECT project_id FROM ecos_dw.biz_project_attribution")));

        assertDenyShape(renderer.render(p, RlsSubjectContext.ofUserId("u-1")), "RLS_SOURCE_NOT_RENDERABLE");
    }

    @Test
    @DisplayName("13. STATIC_LIST 空列表 → 拒绝（校验器只管上限 50，空集在渲染侧按未解析处理）")
    void emptyStaticListYieldsDeny() {
        RlsPredicate p = merged("(status IN (:statusList))",
                List.of(binding("statusList", PredicateBinding.SRC_STATIC_LIST, List.of())));

        assertDenyShape(renderer.render(p, RlsSubjectContext.ofUserId("u-1")), "RLS_STATIC_LIST_INVALID");
    }

    @Test
    @DisplayName("14. 同名绑定解析出两个不同值 → 拒绝（禁静默取其一）")
    void conflictingBindingsForSameNameYieldsDeny() {
        RlsPredicate p = merged("(scope IN (:scope))",
                List.of(binding("scope", PredicateBinding.SRC_STATIC_LIST, List.of("a")),
                        binding("scope", PredicateBinding.SRC_CONTEXT_USER_ID, null)));

        assertDenyShape(renderer.render(p, new RlsSubjectContext("u-1", null, null, null)),
                "RLS_BINDING_CONFLICT");
    }

    // ═══════════════ 生产链：/rls/apply 应答必须自带执行契约 ═══════════════

    @Test
    @DisplayName("15. apply() 应答含 whereClause/whereParams/denyIfEmpty/unresolvedBindings（消费者不再读不存在的字段）")
    void serviceEmitsWhereClauseAndParamsForExecutors() {
        RowLevelSecurityDao dao = mock(RowLevelSecurityDao.class);
        when(dao.findPoliciesForUser(anyString(), anyString())).thenReturn(List.of(
                parameterizedRow("department_id = :userDeptId")));
        RowLevelSecurityServiceImpl svc = new RowLevelSecurityServiceImpl(dao);

        // (a) org 已注入 → 片段可绑定
        Map<String, Object> ok = svc.apply(TABLE, new RlsSubjectContext("u-1", "t-1", "d-7", null));
        assertEquals("(department_id = ?)", ok.get("whereClause"));
        assertEquals(List.of("d-7"), ok.get("whereParams"));
        assertEquals(Boolean.TRUE, ok.get("denyIfEmpty"));
        assertEquals(List.of(), ok.get("unresolvedBindings"));
        assertNull(ok.get("rlsDenyReason"));
        // 旧契约仍在位（D.5.3 兼容，只加不改）
        assertEquals(Boolean.TRUE, ok.get("conditionDeprecated"));
        // params 的 D-B4-23 收口：上下文名 → 解析后的值，不再是恒 null
        assertEquals(Map.of("userDeptId", "d-7"), ok.get("params"));

        // (b) org 无单源 → 应答必须是拒绝形态，且 whereClause 非空串
        Map<String, Object> denied = svc.apply(TABLE, new RlsSubjectContext("u-1", "t-1", null, null));
        assertEquals("1=0", denied.get("whereClause"));
        assertEquals(List.of(), denied.get("whereParams"));
        assertEquals(List.of("userDeptId"), denied.get("unresolvedBindings"));
        assertTrue(String.valueOf(denied.get("rlsDenyReason")).startsWith("RLS_CONTEXT_UNRESOLVED"));

        // (c) 零策略（dev 现网 ecos_rls_policy 实扫 0 行）→ 1=0 + noPolicyMatched
        RowLevelSecurityDao emptyDao = mock(RowLevelSecurityDao.class);
        when(emptyDao.findPoliciesForUser(anyString(), anyString())).thenReturn(List.of());
        Map<String, Object> none = new RowLevelSecurityServiceImpl(emptyDao)
                .apply(TABLE, RlsSubjectContext.ofUserId("u-1"));
        assertEquals("1=0", none.get("whereClause"));
        assertEquals(Boolean.TRUE, none.get("noPolicyMatched"));
        // 兼容字段 condition 仍给 "1=1"（钉现状，非认可：RlsControllerTest 依赖该值，见 B4-3c-2 登记）
        assertEquals("1=1", none.get("condition"));
    }

    // ═══════════════ 不变量（每条用例都要过）═══════════════

    /** 放行态不变量：片段里不得残留 {@code :name}，参数不得为 null，段数与 {@code ?} 数一致。 */
    private static void assertRenderShape(RlsPredicateRenderer.Rendered r) {
        assertFalse(r.denied(), "不应拒绝: " + r.denyReason());
        assertFalse(r.whereClause().matches(".*:\\w+.*"), "占位符未渲染完: " + r.whereClause());
        assertFalse(r.whereParams().contains(null), "参数表不得含 null");
        long marks = r.whereClause().chars().filter(c -> c == '?').count();
        assertEquals(marks, r.whereParams().size(), "? 位数必须与参数个数一致");
        assertEquals(RlsPredicateRenderer.ALLOW_ALL_CLAUSE.equals(RlsPredicateRenderer.normalize(r.whereClause())),
                r.allowAll());
        assertNull(r.denyReason());
        // 拒绝分支不是恒假：放行片段必须区别于 DENY_CLAUSE（否则上面所有「= 1=0」断言都是空断言）
        assertNotEquals(RlsPredicateRenderer.DENY_CLAUSE, r.whereClause(),
                "放行态不得等于拒绝片段，否则拒绝用例是假通过");
    }

    /** 拒绝态不变量：{@code 1=0} + 空参数 + 带原因外发。 */
    private static void assertDenyShape(RlsPredicateRenderer.Rendered r, String reasonPrefix) {
        assertTrue(r.denied(), "必须拒绝");
        assertEquals(RlsPredicateRenderer.DENY_CLAUSE, r.whereClause());
        assertEquals(List.of(), r.whereParams());
        assertTrue(r.denyReason().startsWith(reasonPrefix),
                "denyReason=" + r.denyReason() + " 应前缀 " + reasonPrefix);
        assertFalse(r.allowAll());
        Map<String, Object> map = r.toMap();
        assertEquals(r.whereClause(), map.get("whereClause"));
        assertTrue(map.containsKey("rlsDenyReason"), "拒绝原因必须随应答外发，禁消费者折叠成「无数据」");
    }

    // ═══════════════ 构造辅助 ═══════════════

    private static RlsPredicate merged(String template, List<PredicateBinding> bindings) {
        RlsPredicate p = new RlsPredicate();
        p.setPredicateTemplate(template);
        p.setSegments(List.of(template));
        p.setBindings(bindings);
        return p;
    }

    private static PredicateBinding binding(String name, String source, Object value) {
        return new PredicateBinding(name, source, value);
    }

    /**
     * V246 种子行形态（source_kind=parameterized + bindings_json）。
     *
     * <p>{@code template} 传<b>不带外层括号</b>的原文（与 V246 落库值一致）——
     * 外层 {@code ()} 由 {@code mergePolicies} 加，本用例首轮实跑就是 fixture 多写一层
     * 括号被 {@code ((department_id = ?))} 打回的，留此注释防止再改回去。</p>
     */
    private static Map<String, Object> parameterizedRow(String template) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("policy_name", "rls-dept-scope");
        row.put("filter_expr", "1=1");
        row.put("priority", 90);
        row.put("predicate_template", template);
        row.put("bindings_json", "[{\"name\":\"userDeptId\",\"source\":\"CONTEXT_ORG\",\"value\":null}]");
        row.put("source_kind", "parameterized");
        return row;
    }
}
