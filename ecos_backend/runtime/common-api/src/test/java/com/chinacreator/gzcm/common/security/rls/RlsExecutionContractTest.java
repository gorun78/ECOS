package com.chinacreator.gzcm.common.security.rls;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RLS 执行契约单源（{@link RlsExecutionContract}）的形态与不变量用例
 * （#142/B4-3d-5，差距 D-B4-24 / D-B4-26 / D-B4-23）。
 *
 * <p><b>为什么单源的用例必须落在底座模块</b>：四通道（页面／本体／AI／数据）此前各抄一份
 * 读数与拒绝归一，同一条应答在四处可以得到四种结论。收口后判定逻辑只在这里一份，
 * 于是「判据本身对不对」第一次成为一个可测命题——通道用例（{@code RlsFilterContractTest}、
 * {@code AiRlsFailClosedTest}、{@code OntologyRlsFailClosedTest}）只能验证"有没有委托"，
 * 验证不了委托过去之后的四态与四道构造期不变量。</p>
 *
 * <p>本类逐条钉住的架构承诺：
 * ① 缺片段＝漂移即拒绝，绝不等价于「无过滤」（D-B4-17 的 fail-open 入口）；
 * ② 恒真 {@code 1=1}／非平凡谓词／拒绝／不可用 <b>四态互斥</b>，
 * 其中「不可用 ≠ 无权限」是详设-01 §4.5.4 的硬要求（D-B4-26 在类型层的收口）；
 * ③ {@code ?} 与参数个数必须相符、参数不得含 {@code null}（D-B4-23）、
 * 片段不得残留 {@code :name}——违反任何一条即拒绝，禁降级成"看起来能用"；
 * ④ 无参数绑定通道叠加的 {@code rejectForUnbindableChannel} 只作用于 {@code FILTERED}
 * 且真含 {@code ?} 的片段，不得把恒真或裸文本条件一并误杀（否则通道会自行放宽判据绕过单源）。</p>
 */
@DisplayName("RLS 执行契约单源：四态互斥 + 四道构造期不变量 + 通道级拒绝")
class RlsExecutionContractTest {

    private static final String SUBJECT = "entity=scenario_run";

    private static RlsExecutionContract.Execution read(Object... kv) {
        return RlsExecutionContract.read(data(kv), SUBJECT);
    }

    private static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    // ═══════════════ ① 缺片段＝漂移即拒绝 ═══════════════

    @Test
    @DisplayName("data 段缺失或非 Map → DENIED（外层形态漂移不得读成可用）")
    void absentOrMalformedDataSegmentDenies() {
        for (Object seg : new Object[]{null, "1=1", 42}) {
            RlsExecutionContract.Execution exec = RlsExecutionContract.read(seg, SUBJECT);
            assertEquals(RlsExecutionContract.State.DENIED, exec.state(), "data 段形态非法: " + seg);
            assertTrue(exec.reason().startsWith(RlsExecutionContract.REASON_NO_DATA_SEGMENT),
                    "理由必须指名无 data 段: " + exec.reason());
            assertEquals(RlsExecutionContract.DENY_CLAUSE, exec.clauseForSplice());
            assertFalse(exec.usable());
        }
    }

    @Test
    @DisplayName("应答缺 whereClause → DENIED + RLS_WHERE_CLAUSE_ABSENT，绝不是「无过滤」")
    void absentWhereClauseDeniesInsteadOfNoFilter() {
        RlsExecutionContract.Execution exec = read("visibleColumns", List.of());
        assertEquals(RlsExecutionContract.State.DENIED, exec.state());
        assertTrue(exec.reason().contains(RlsExecutionContract.REASON_WHERE_ABSENT_CODE),
                "拒绝理由须带裸码供审计检索: " + exec.reason());
        assertFalse(exec.clauseForSplice().isEmpty(),
                "D-B4-17 的原始缺陷形态就是这里返回空串＝行过滤恒等于关闭");
        assertEquals(RlsExecutionContract.DENY_CLAUSE, exec.clauseForSplice());
        assertTrue(exec.noPolicyMatched(), "本地不变量拒绝时保守按「无命中策略」表达");
    }

    @Test
    @DisplayName("whereClause 为 null 值／空白串 → 同上拒绝（空串与缺失同态，都不可执行）")
    void blankOrNullWhereClauseDenies() {
        assertEquals(RlsExecutionContract.State.DENIED, read("whereClause", null).state());
        assertEquals(RlsExecutionContract.State.DENIED, read("whereClause", "   ").state());
        assertEquals(RlsExecutionContract.State.DENIED, read("whereClause", "").state());
    }

    // ═══════════════ ② 四态互斥（§4.5.4 分态） ═══════════════

    @Test
    @DisplayName("恒真 1=1 与非平凡谓词、拒绝、不可用四态各自唯一可判")
    void fourStatesAreMutuallyExclusive() {
        RlsExecutionContract.Execution allow = read("whereClause", "1=1");
        RlsExecutionContract.Execution filtered = read("whereClause", "org_id = ?",
                "whereParams", List.of("o-1"));
        RlsExecutionContract.Execution denied = read("whereClause", "1=0");
        RlsExecutionContract.Execution unavailable = RlsExecutionContract.Execution
                .unavailable("connect timed out");

        assertEquals(RlsExecutionContract.State.ALLOW_ALL, allow.state());
        assertEquals(RlsExecutionContract.State.FILTERED, filtered.state());
        assertEquals(RlsExecutionContract.State.DENIED, denied.state());
        assertEquals(RlsExecutionContract.State.UNAVAILABLE, unavailable.state());

        for (RlsExecutionContract.Execution e : List.of(allow, filtered, denied, unavailable)) {
            int truthy = countTrue(e.allowAll(), e.filtered(), e.denied(), e.unavailable());
            assertEquals(1, truthy, "四态判据必须互斥（折叠即分态丢失）: " + e.state());
        }
        assertTrue(allow.usable() && filtered.usable());
        assertFalse(denied.usable() || unavailable.usable());
    }

    @Test
    @DisplayName("不可用态不是拒绝态：理由词表分列，消费者据此走 503 而非 403（D-B4-26）")
    void unavailableIsDistinctFromDenyInReasonVocabulary() {
        RlsExecutionContract.Execution unavailable = RlsExecutionContract.Execution
                .unavailable("端点寻址失败");
        assertTrue(unavailable.reason().startsWith(RlsExecutionContract.REASON_SERVICE_UNAVAILABLE),
                "不可用理由必须用不可用词表: " + unavailable.reason());
        assertFalse(unavailable.reason().contains(RlsExecutionContract.REASON_DENY_FROM_SECURITY_ENGINE),
                "「护栏/引擎没接上」禁与「策略拒绝」共用理由串");
        assertFalse(unavailable.denied(), "denied() 只说策略拒绝；不可用另有一态");
        // 拼 SQL 时仍归一为 1=0（不可用不许读成「不限制」），但状态本身保留分态
        assertEquals(RlsExecutionContract.DENY_CLAUSE, unavailable.clauseForSplice());
        assertTrue(unavailable.paramsForSplice().isEmpty());
    }

    @Test
    @DisplayName("恒真态可由内部调用链显式构造（工厂名带 of 前缀避开实例谓词同名冲突）")
    void ofAllowAllFactoryBuildsUsableAllowAll() {
        RlsExecutionContract.Execution exec = RlsExecutionContract.Execution.ofAllowAll();
        assertEquals(RlsExecutionContract.State.ALLOW_ALL, exec.state());
        assertEquals(RlsExecutionContract.ALLOW_ALL_CLAUSE, exec.clause());
        assertNull(exec.reason(), "放行态无理由串");
        assertTrue(exec.clauseForSplice().isEmpty(), "恒真不必拼进 WHERE");
    }

    // ═══════════════ 生产者拒绝态透传 ═══════════════

    @Test
    @DisplayName("应答给 1=0（含带括号形态）→ DENIED，且保留生产者理由与 noPolicyMatched")
    void producerDenyIsPassedThroughNotRewritten() {
        RlsExecutionContract.Execution exec = read("whereClause", "(1=0)",
                "rlsDenyReason", "RLS_CONTEXT_UNRESOLVED(userDeptId)", "noPolicyMatched", false);
        assertEquals(RlsExecutionContract.State.DENIED, exec.state());
        assertEquals("RLS_CONTEXT_UNRESOLVED(userDeptId)", exec.reason(),
                "拒绝原因必须随应答外发，禁消费者折叠成「无数据」");
        assertFalse(exec.noPolicyMatched(), "拒绝态的 noPolicyMatched 由应答说了算");

        RlsExecutionContract.Execution noReason = read("whereClause", "1=0");
        assertEquals(RlsExecutionContract.REASON_DENY_FROM_SECURITY_ENGINE, noReason.reason(),
                "生产者拒绝但没给理由时给兜底码，不留空");
        assertTrue(noReason.noPolicyMatched(), "键缺失按保守值 true");
    }

    @Test
    @DisplayName("恒真带括号（(1=1)）与空白包裹仍判 ALLOW_ALL，但片段原文照存不改写")
    void allowAllNormalizationKeepsOriginalClause() {
        RlsExecutionContract.Execution exec = read("whereClause", "  (1=1)  ");
        assertEquals(RlsExecutionContract.State.ALLOW_ALL, exec.state());
        assertEquals("(1=1)", exec.clause(), "原文归一只用于判态，交给消费者拼的片段不得被改写");
    }

    // ═══════════════ ③ 四道构造期不变量 ═══════════════

    @Test
    @DisplayName("不变量一：? 数与参数数不符 → DENIED + RLS_PARAM_ARITY_MISMATCH")
    void arityMismatchDenies() {
        RlsExecutionContract.Execution missingParams = read("whereClause", "org_id = ?");
        assertEquals(RlsExecutionContract.State.DENIED, missingParams.state());
        assertTrue(missingParams.reason().contains(RlsExecutionContract.REASON_PARAM_ARITY_MISMATCH),
                "未绑定的 ? 拼进 SQL 即语法错误或被截断语义: " + missingParams.reason());

        RlsExecutionContract.Execution tooManyParams = read("whereClause", "(a = ? AND b = ?)",
                "whereParams", List.of("x"));
        assertEquals(RlsExecutionContract.State.DENIED, tooManyParams.state());
        assertTrue(tooManyParams.reason().contains(RlsExecutionContract.REASON_PARAM_ARITY_MISMATCH));

        RlsExecutionContract.Execution extra = read("whereClause", "a = ?",
                "whereParams", Arrays.asList("x", "y"));
        assertEquals(RlsExecutionContract.State.DENIED, extra.state());
    }

    @Test
    @DisplayName("不变量二：参数含 null → DENIED + RLS_NULL_PARAM（D-B4-23 的绑定面收口）")
    void nullParameterDenies() {
        RlsExecutionContract.Execution exec = read("whereClause", "org_id = ?",
                "whereParams", Arrays.asList((Object) null),
                "unresolvedBindings", List.of("CONTEXT_ORG"));
        assertEquals(RlsExecutionContract.State.DENIED, exec.state());
        assertTrue(exec.reason().contains(RlsExecutionContract.REASON_NULL_PARAM),
                "上下文未解析值禁作绑定值: " + exec.reason());
        assertEquals(List.of("CONTEXT_ORG"), exec.unresolvedBindings(),
                "未解析清单要留着给告警定位，不能只落在理由串里");
    }

    @Test
    @DisplayName("不变量三：片段仍含 :name → DENIED + RLS_UNRENDERED_PLACEHOLDER")
    void unrenderedPlaceholderDenies() {
        RlsExecutionContract.Execution exec = read("whereClause", "owner = :userId");
        assertEquals(RlsExecutionContract.State.DENIED, exec.state());
        assertTrue(exec.reason().contains(RlsExecutionContract.REASON_UNRENDERED_PLACEHOLDER),
                "生产者未完成渲染时禁把占位符拼进 SQL: " + exec.reason());
    }

    @Test
    @DisplayName("不变量四：可用片段必须成对返回（clause + 同序 params）")
    void filteredStateCarriesClauseAndParamsTogether() {
        RlsExecutionContract.Execution exec = read("whereClause", "(org_id = ? AND owner_id = ?)",
                "whereParams", Arrays.asList("o-1", "u-42"));
        assertEquals(RlsExecutionContract.State.FILTERED, exec.state());
        assertTrue(exec.needsBinding());
        assertEquals(List.of("o-1", "u-42"), exec.paramsForSplice());
        assertEquals("(org_id = ? AND owner_id = ?)", exec.clauseForSplice());
        assertNull(exec.reason());
    }

    @Test
    @DisplayName("裸文本条件（无 ? 无 :name）＝可下推的非平凡谓词，不需绑参")
    void literalPredicateIsFilteredWithoutBinding() {
        RlsExecutionContract.Execution exec = read("whereClause", "tenant_id = 't-7'");
        assertEquals(RlsExecutionContract.State.FILTERED, exec.state());
        assertFalse(exec.needsBinding());
        assertTrue(exec.paramsForSplice().isEmpty());
        assertEquals("tenant_id = 't-7'", exec.clauseForSplice());
    }

    // ═══════════════ ④ 通道级拒绝 ═══════════════

    @Test
    @DisplayName("无参数通道：FILTERED 且含 ? 才拒；恒真与裸文本原样通过（不误杀也不放过）")
    void unbindableChannelRefusesOnlyParameterizedFragments() {
        RlsExecutionContract.Execution parameterized = read("whereClause", "org_id = ?",
                "whereParams", List.of("o-1"));
        RlsExecutionContract.Execution refused = parameterized.rejectForUnbindableChannel("table=t");
        assertEquals(RlsExecutionContract.State.DENIED, refused.state());
        assertTrue(refused.reason().contains(RlsExecutionContract.REASON_PARAM_UNBINDABLE),
                "拒绝理由须指名通道无绑参腿: " + refused.reason());
        assertTrue(refused.reason().contains("org_id = ?"), "理由要带上被拒片段供定位");

        RlsExecutionContract.Execution literal = read("whereClause", "tenant_id = 't-7'");
        assertSame(literal, literal.rejectForUnbindableChannel("table=t"),
                "裸文本条件本可原样下传，多包一层拒绝即误杀");
        RlsExecutionContract.Execution allow = read("whereClause", "1=1");
        assertSame(allow, allow.rejectForUnbindableChannel("table=t"));
        RlsExecutionContract.Execution denied = read("whereClause", "1=0");
        assertSame(denied, denied.rejectForUnbindableChannel("table=t"),
                "已是拒绝态时保留原理由，不得被通道层换成通用码");
    }

    // ═══════════════ 键面默认值与归一 ═══════════════

    @Test
    @DisplayName("denyIfEmpty 键缺失按 true（默认拒绝），显式 false 才按应答值")
    void denyIfEmptyDefaultsToTrue() {
        assertTrue(read("whereClause", "1=0").denyIfEmpty());
        assertFalse(read("whereClause", "1=0", "denyIfEmpty", false).denyIfEmpty());
        assertTrue(read("visibleColumns", List.of()).denyIfEmpty());
    }

    @Test
    @DisplayName("normalize 剥括号与空白：四通道必须同一同态判据")
    void normalizeIsParenAndWhitespaceInsensitive() {
        assertEquals("1=0", RlsExecutionContract.normalize(" (1=0) "));
        assertEquals("1=1", RlsExecutionContract.normalize("(1\n=1)"));
        assertEquals("", RlsExecutionContract.normalize(null));
    }

    private static int countTrue(boolean... flags) {
        int n = 0;
        for (boolean f : flags) if (f) n++;
        return n;
    }
}
