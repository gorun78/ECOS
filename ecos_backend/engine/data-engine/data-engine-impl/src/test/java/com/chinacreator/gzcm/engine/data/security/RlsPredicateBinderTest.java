package com.chinacreator.gzcm.engine.data.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RLS 片段「位置形态 → 命名形态」适配器门禁（#142/B4-3d-5，差距 D-B4-24 的 data-engine 侧）。
 *
 * <p><b>本类为什么必须存在</b>：{@code /rls/apply} 的执行契约交付 {@code whereClause}（含 {@code ?}）
 * ＋同序 {@code whereParams}，而 data-engine 唯一被审计过的绑定链路是
 * {@code QueryExecutionServiceImpl#bindNamedParameters} → {@code JdbcConnector} 的
 * {@code PreparedStatement} 重载（{@code QueryExecutorBindingTest} 钉的就是这条）。
 * 适配器是两条形态之间<b>唯一</b>的桥：桥一旦写歪，歪的方式不是「报错」而是
 * <b>把值绑到别的列</b>——最典型的是把字符串字面量里的 {@code ?} 也当成占位符，
 * 于是占位符数比参数多一位，其后果是整条谓词的列/值错位（比拒绝更危险，因为它看起来在返回数据）。</p>
 *
 * <p><b>断言口径</b>：逐条对模板文本做等值断言，而不是「包含 {@code :rlsP0}」这种弱断言——
 * 弱断言对「改写顺序错乱」和「多写了一个 {@code :rlsP}」都不变红。
 * 字面量用例（{@link #questionMarkInsideStringLiteralIsNotAPlaceholder}、
 * {@link #questionMarkInsideQuotedIdentifierIsNotAPlaceholder}）是本类的<b>反变异</b>核心：
 * 若有人把字符态机退回 {@code clause.replace("?", …)} 的一次性替换，这两条先红。</p>
 */
@DisplayName("B4-3d-5/D-B4-24：RLS 片段适配 —— 占位符改写只认字面量外的 ?，值绝不入 SQL 文本")
class RlsPredicateBinderTest {

    @Test
    @DisplayName("两个 ? → 按模板出现序改写为 :rlsP0/:rlsP1，值同名并入参数表")
    void rewritesPositionalMarksInTemplateOrder() {
        RlsPredicateBinder.Bound bound = RlsPredicateBinder.toNamedForm(
                "(org_id = ? AND owner_id = ?)", List.of("o-42", "u-7"), Map.of());

        assertTrue(bound.ok(), bound.reason());
        assertEquals("(org_id = :rlsP0 AND owner_id = :rlsP1)", bound.template());
        assertEquals(2, bound.namedParams().size());
        // 「出现序」是本适配器唯一的对齐依据：值错位＝把别人的行过滤条件绑到自己身上
        assertEquals("o-42", bound.namedParams().get("rlsP0"));
        assertEquals("u-7", bound.namedParams().get("rlsP1"));
    }

    @Test
    @DisplayName("字符串字面量内的 ?（含 '' 转义）不是占位符，必须原样保留")
    void questionMarkInsideStringLiteralIsNotAPlaceholder() {
        String clause = "note = 'what? or not''really' AND org_id = ?";
        RlsPredicateBinder.Bound bound =
                RlsPredicateBinder.toNamedForm(clause, List.of("o-42"), Map.of());

        assertTrue(bound.ok(), bound.reason());
        assertEquals("note = 'what? or not''really' AND org_id = :rlsP0", bound.template());
        assertEquals(1, bound.namedParams().size());
        assertEquals(1, RlsPredicateBinder.countPlaceholders(clause));
        // IR05：值只在参数表里，SQL 文本里一个客户端值都不许出现
        assertFalse(bound.template().contains("o-42"), "绑定值进了 SQL 文本");
    }

    @Test
    @DisplayName("双引号标识符内的 ? 不是占位符（列名带 ? 时禁少绑一位）")
    void questionMarkInsideQuotedIdentifierIsNotAPlaceholder() {
        String clause = "\"weird?col\" = ? AND status = ?";
        RlsPredicateBinder.Bound bound =
                RlsPredicateBinder.toNamedForm(clause, List.of("v-1", "ACTIVE"), Map.of());

        assertTrue(bound.ok(), bound.reason());
        assertEquals("\"weird?col\" = :rlsP0 AND status = :rlsP1", bound.template());
        assertEquals(2, RlsPredicateBinder.countPlaceholders(clause));
        assertEquals("v-1", bound.namedParams().get("rlsP0"));
        assertEquals("ACTIVE", bound.namedParams().get("rlsP1"));
    }

    @Test
    @DisplayName("零占位片段原样通过（不产生空 rlsP0 之类的幻影绑定）")
    void clauseWithoutPlaceholderPassesThroughUnchanged() {
        String clause = "status = 'ACTIVE'";
        RlsPredicateBinder.Bound bound =
                RlsPredicateBinder.toNamedForm(clause, List.of(), Map.of("userId", "u-7"));

        assertTrue(bound.ok(), bound.reason());
        assertEquals(clause, bound.template());
        assertTrue(bound.namedParams().isEmpty(), "无占位符却生出了绑定项");
        assertEquals(0, RlsPredicateBinder.countPlaceholders(clause));
    }

    @Test
    @DisplayName("? 数与参数数不符 → 失败且不给模板（禁返回「看起来能用」的半截谓词）")
    void arityMismatchFailsWithoutATemplate() {
        RlsPredicateBinder.Bound tooFew = RlsPredicateBinder.toNamedForm(
                "(a = ? AND b = ?)", List.of("v-1"), Map.of());
        assertFalse(tooFew.ok());
        assertTrue(tooFew.reason().startsWith(RlsPredicateBinder.REASON_ARITY), tooFew.reason());
        assertNull(tooFew.template(), "失败态仍交付模板＝调用方可能照拼");
        assertTrue(tooFew.namedParams().isEmpty());

        RlsPredicateBinder.Bound tooMany = RlsPredicateBinder.toNamedForm(
                "org_id = ?", List.of("o-42", "o-43"), Map.of());
        assertFalse(tooMany.ok());
        assertTrue(tooMany.reason().startsWith(RlsPredicateBinder.REASON_ARITY), tooMany.reason());
    }

    @Test
    @DisplayName("片段缺失/空白 → 失败（恒真与拒绝态都不该走本适配器，走到即调用序错位）")
    void absentOrBlankClauseFails() {
        RlsPredicateBinder.Bound absent = RlsPredicateBinder.toNamedForm(null, List.of(), Map.of());
        assertFalse(absent.ok());
        assertEquals(RlsPredicateBinder.REASON_CLAUSE_ABSENT, absent.reason());

        RlsPredicateBinder.Bound blank = RlsPredicateBinder.toNamedForm("   ", List.of(), Map.of());
        assertFalse(blank.ok());
        assertEquals(RlsPredicateBinder.REASON_CLAUSE_ABSENT, blank.reason());
    }

    @Test
    @DisplayName("合成分段名与调用方已有参数名冲突 → 拒绝（禁覆盖用户参数值，那是越权面）")
    void collisionWithCallerParamNameFails() {
        Map<String, Object> callerParams = new LinkedHashMap<>();
        callerParams.put(RlsPredicateBinder.PARAM_NAME_PREFIX + "0", "user-supplied");

        RlsPredicateBinder.Bound bound = RlsPredicateBinder.toNamedForm(
                "org_id = ?", List.of("o-42"), callerParams);

        assertFalse(bound.ok());
        assertTrue(bound.reason().startsWith(RlsPredicateBinder.REASON_NAME_COLLISION), bound.reason());
        assertTrue(bound.reason().contains("rlsP0"), bound.reason());
        assertNull(bound.template());
    }

    @Test
    @DisplayName("冲突只发生在真正落到的分段位（第二 ? 撞 rlsP1 也须拒绝）")
    void collisionIsDetectedPerSlotNotOnlyAtFirstSlot() {
        RlsPredicateBinder.Bound bound = RlsPredicateBinder.toNamedForm(
                "a = ? AND b = ?", List.of("v-1", "v-2"),
                Map.of(RlsPredicateBinder.PARAM_NAME_PREFIX + "1", "user-supplied"));

        assertFalse(bound.ok());
        assertTrue(bound.reason().startsWith(RlsPredicateBinder.REASON_NAME_COLLISION), bound.reason());
        assertTrue(bound.reason().contains("rlsP1"), bound.reason());
    }

    @Test
    @DisplayName("多作用域续编（D-B4-63）：slotOffset 让第二张表从下一槽起编，两个合法片段不再互撞")
    void slotOffsetContinuesNumberingAcrossScopes() {
        // 无 offset 时第二次调用会撞 rlsP0 —— 那是"合法多表"被判成越权冲突的形态
        RlsPredicateBinder.Bound second = RlsPredicateBinder.toNamedForm(
                "b = ? OR c = ?", List.of("v-b", "v-c"),
                Map.of(RlsPredicateBinder.PARAM_NAME_PREFIX + "0", "v-a"), 1);

        assertTrue(second.ok(), second.reason());
        assertEquals("b = :rlsP1 OR c = :rlsP2", second.template());
        assertEquals(Map.of("rlsP1", "v-b", "rlsP2", "v-c"), second.namedParams());

        // offset 不豁免冲突检测：真正落到已占用名字仍须拒绝，且报的是实际落到的槽（rlsP1 而非 rlsP0）
        RlsPredicateBinder.Bound collide = RlsPredicateBinder.toNamedForm(
                "d = ?", List.of("v-d"),
                Map.of(RlsPredicateBinder.PARAM_NAME_PREFIX + "1", "user-supplied"), 1);
        assertFalse(collide.ok());
        assertTrue(collide.reason().contains("rlsP1"), collide.reason());
    }

    @Test
    @DisplayName("三参形态＝offset 0（既有调用点语义一字未变）")
    void threeArgFormDelegatesToZeroOffset() {
        RlsPredicateBinder.Bound plain = RlsPredicateBinder.toNamedForm("a = ?", List.of("v"), Map.of());
        RlsPredicateBinder.Bound explicit = RlsPredicateBinder.toNamedForm("a = ?", List.of("v"), Map.of(), 0);

        assertEquals(plain.template(), explicit.template());
        assertEquals(plain.namedParams(), explicit.namedParams());
    }

    @Test
    @DisplayName("计数与改写共用同一字符态机：countPlaceholders == 改写出的绑定数")
    void counterAndRewriterAgree() {
        List<String> clauses = List.of(
                "org_id = ?",
                "(a = ? AND b = ?)",
                "note = 'what? no' AND org_id = ?",
                "\"c?\" = ? AND d = 'x?'");
        for (String clause : clauses) {
            long marks = RlsPredicateBinder.countPlaceholders(clause);
            List<Object> values = new java.util.ArrayList<>();
            for (int i = 0; i < marks; i++) {
                values.add("v-" + i);
            }
            RlsPredicateBinder.Bound bound = RlsPredicateBinder.toNamedForm(clause, values, Map.of());
            assertTrue(bound.ok(), clause + " → " + bound.reason());
            assertEquals(marks, bound.namedParams().size(),
                    "两套口径错位（计数与改写各算各的）: " + clause);
        }
    }

    @Test
    @DisplayName("null 绑定值不得让适配层抛 NPE（拒绝属契约层，本层禁成为崩溃源）")
    void nullBindingValueDoesNotCrashTheAdapter() {
        // 钉现状非认可：null 参数在 RlsExecutionContract.read 里已被 RLS_NULL_PARAM 拒掉
        // （D-B4-23），正常调用序到不了这里。本用例守的是「若有人把 Map.copyOf 换回 Bound.of」
        // 就当场 500 —— 那会把一个可解释的拒绝态变成崩溃，故适配层用 unmodifiableMap。
        RlsPredicateBinder.Bound bound = RlsPredicateBinder.toNamedForm(
                "org_id = ?", Collections.singletonList(null), Map.of());

        assertTrue(bound.ok(), bound.reason());
        assertEquals("org_id = :rlsP0", bound.template());
        assertNull(bound.namedParams().get("rlsP0"));
    }

    @Test
    @DisplayName("返回的参数表不可变（防调用方把绑定值改坏后再交给绑定链路）")
    void boundParamsAreImmutable() {
        RlsPredicateBinder.Bound bound = RlsPredicateBinder.toNamedForm(
                "org_id = ?", List.of("o-42"), Map.of());

        assertTrue(bound.ok(), bound.reason());
        assertThrows(UnsupportedOperationException.class,
                () -> bound.namedParams().put("rlsP0", "tampered"),
                "参数表可被改写＝绑定值在送达 PreparedStatement 前还有第二个写入口");
    }
}
