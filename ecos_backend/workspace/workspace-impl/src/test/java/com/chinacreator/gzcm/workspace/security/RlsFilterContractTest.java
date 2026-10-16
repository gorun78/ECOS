package com.chinacreator.gzcm.workspace.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RlsFilter} 构造期不变量验收（#142/B4-3c-2，差距 D-B4-17 / D-B4-23）。
 *
 * <p><b>本类钉的是"页面通道行级安全曾经恒等于关闭"这一缺陷</b>：{@code /rls/apply} 的应答里
 * 从来没有 {@code whereClause} 字段，消费者写 {@code data.get("whereClause") == null ? "" : …}
 * —— 空串被理解成"没有行级限制"，于是任何主体的行过滤都被跳过（fail-open）。收口后
 * 「应答未发执行契约」必须是一个<b>拒绝态</b>，而且片段与参数必须<b>成对</b>流动
 * （片段是带 {@code ?} 的 JDBC 形态，只取片段＝悬空占位符或错列绑定，撞 IR05）。</p>
 *
 * <p>每条 DENY 用例都配一条同分支的正向对照（{@code 1=1} 放行 / 完整参数对放行），
 * 防止"恒拒绝"造成的假通过。</p>
 */
@DisplayName("RLS 执行契约：片段与参数成对 + 四条构造期不变量 + 三态分明")
class RlsFilterContractTest {

    private static final String ENTITY = "Contract";

    private static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    // ═══════════════ 不变量 1：缺片段＝漂移＝拒绝（D-B4-17 原始缺陷形态） ═══════════════

    @Test
    void absentWhereClauseDeniesInsteadOfPassingThrough() {
        Map<String, Object> d = data("denyIfEmpty", true, "noPolicyMatched", true);

        RlsFilter rls = RlsFilter.fromData(d, ENTITY);

        assertTrue(rls.denied(), "应答未发 whereClause 必须拒绝（旧形态在此返回空串＝放行全部行）");
        assertEquals(RlsFilter.DENY_CLAUSE, rls.clause());
        assertTrue(rls.denyReason().contains(RlsFilter.DRIFT_ABSENT),
                "拒绝原因必须点名「应答未发执行契约」，禁与「策略 DENY」混淆");
        assertTrue(rls.denyReason().contains(ENTITY), "原因串要能定位到实体");
        // 三态归一出口：拒绝态拼 1=0、不带参数
        assertEquals(RlsFilter.DENY_CLAUSE, rls.clauseForSplice());
        assertTrue(rls.paramsForSplice().isEmpty());
    }

    @Test
    void blankWhereClauseDenies() {
        RlsFilter rls = RlsFilter.fromData(data("whereClause", "   "), ENTITY);

        assertTrue(rls.denied(), "空白片段＝没给执行契约，不得当成「无过滤」");
        assertEquals(RlsFilter.DENY_CLAUSE, rls.clauseForSplice());
    }

    // ═══════════════ 三态分明：恒真 ≠ 未给谓词 ≠ 拒绝 ═══════════════

    @Test
    void allowAllIsDistinctFromDriftAndSplicesNothing() {
        RlsFilter rls = RlsFilter.fromData(data("whereClause", "1=1", "noPolicyMatched", false), ENTITY);

        assertTrue(rls.allowAll(), "1=1＝裁决明确不限制行");
        assertFalse(rls.denied());
        assertNull(rls.denyReason());
        assertEquals("", rls.clauseForSplice(), "恒真不必拼条件（拼了也是噪声）");
        assertTrue(rls.paramsForSplice().isEmpty());
    }

    @Test
    void parenthesisedDenyFormKeepsProducerReason() {
        // 生产者 mergePolicies 给每段加括号："(1=0)" 必须仍判拒绝，否则走成"非平凡谓词"分支
        RlsFilter rls = RlsFilter.fromData(
                data("whereClause", "(1=0)", "rlsDenyReason", "RLS_NO_POLICY_MATCHED",
                        "noPolicyMatched", true), ENTITY);

        assertTrue(rls.denied());
        assertEquals("RLS_NO_POLICY_MATCHED", rls.denyReason(), "策略侧原因必须原样保留供回查");
        assertTrue(rls.noPolicyMatched());
    }

    // ═══════════════ 正向：完整参数对放行 ═══════════════

    @Test
    void clauseAndParamsTravelAsAPair() {
        RlsFilter rls = RlsFilter.fromData(
                data("whereClause", "(department_id = ?)", "whereParams", List.of("d-1"),
                        "denyIfEmpty", true), ENTITY);

        assertFalse(rls.denied());
        assertFalse(rls.allowAll());
        assertEquals("(department_id = ?)", rls.clauseForSplice());
        assertEquals(List.of("d-1"), rls.paramsForSplice(), "参数必须与片段同序成对下推");
        assertTrue(rls.hasParams());
        assertEquals(1, rls.argArray().length);
    }

    // ═══════════════ 不变量 2：? 数与参数数不符＝拒绝 ═══════════════

    @Test
    void placeholderArityMismatchDenies() {
        RlsFilter rls = RlsFilter.fromData(
                data("whereClause", "(a = ? AND b = ?)", "whereParams", List.of("x")), ENTITY);

        assertTrue(rls.denied(), "占位符数≠参数数＝半条 SQL，禁下推");
        assertTrue(rls.denyReason().contains("RLS_PARAM_ARITY_MISMATCH"));
        assertEquals(RlsFilter.DENY_CLAUSE, rls.clauseForSplice());
    }

    // ═══════════════ 不变量 3：参数含 null＝拒绝（D-B4-23） ═══════════════

    @Test
    void nullParamDeniesInsteadOfBindingNull() {
        // 应答里的 null 值只能由可变列表模拟：java.util.List.of((Object) null) 本身抛 NPE，
        // 拿它构造夹具会测不到生产代码（实跑曾以 NPE 形式暴露，见本批订正注）。
        RlsFilter rls = RlsFilter.fromData(
                data("whereClause", "(org_id = ?)",
                        "whereParams", java.util.Arrays.asList((Object) null),
                        "unresolvedBindings", List.of("CONTEXT_ORG")), ENTITY);

        assertTrue(rls.denied(), "未解析上下文值禁作绑定值（绑 null 会把语义变成永假并掩盖缺陷）");
        assertTrue(rls.denyReason().contains("RLS_NULL_PARAM"));
        assertEquals(List.of("CONTEXT_ORG"), rls.unresolvedBindings(), "未解析绑定名必须外发可见");
    }

    // ═══════════════ 不变量 4：片段仍含 :name 占位符＝拒绝 ═══════════════

    @Test
    void residualNamedPlaceholderDenies() {
        RlsFilter rls = RlsFilter.fromData(
                data("whereClause", "(owner = :userId)", "whereParams", List.of()), ENTITY);

        assertTrue(rls.denied(), "生产者未渲染完的 :name 拼进 SQL 必错，禁原样下推");
        assertTrue(rls.denyReason().contains("RLS_UNRENDERED_PLACEHOLDER"));
    }

    // ═══════════════ 辅助形态 ═══════════════

    @Test
    void explicitDenyFactoryCarriesReasonAndUnresolved() {
        RlsFilter rls = RlsFilter.deny("RLS_APPLY_EXCEPTION(read timeout)", true, List.of("CONTEXT_ORG"));

        assertTrue(rls.denied());
        assertEquals(RlsFilter.DENY_CLAUSE, rls.clause());
        assertTrue(rls.noPolicyMatched(), "显式拒绝默认「无命中策略」");
        assertEquals(List.of("CONTEXT_ORG"), rls.unresolvedBindings());
    }

    @Test
    void unrestrictedFactoryIsAllowAllNotDrift() {
        RlsFilter rls = RlsFilter.unrestricted();

        assertTrue(rls.allowAll());
        assertFalse(rls.denied());
        assertEquals("", rls.clauseForSplice());
    }

    @Test
    void missingWhereParamsIsTreatedAsNoParamsOnlyForNoPlaceholderClause() {
        // 裸文本条件（存量 legacy_text 形态）无 ? 也无参数：可下推，不是漂移
        RlsFilter rls = RlsFilter.fromData(data("whereClause", "tenant_id = 't-9'"), ENTITY);

        assertFalse(rls.denied());
        assertEquals("tenant_id = 't-9'", rls.clauseForSplice());
        assertTrue(rls.paramsForSplice().isEmpty());
    }
}
