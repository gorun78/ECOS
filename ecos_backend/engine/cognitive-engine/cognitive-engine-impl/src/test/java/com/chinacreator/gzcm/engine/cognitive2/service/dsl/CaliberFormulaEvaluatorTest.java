package com.chinacreator.gzcm.engine.cognitive2.service.dsl;

import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.FuncNode;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.Node;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.NumberNode;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.Token;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.TokenType;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaEvaluationException.Reason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 口径公式 DSL 求值面单测（详设-09 v1.2 §十一 H-2b）。
 *
 * <p>三条硬判据逐条落到断言：① 只用自有 AST 递归求值（无脚本引擎 ⇒ 白名单外函数在
 * 求值层也拒，见 {@link #evaluatorItselfRejectsNonWhitelistFunction}）；② 全程 BigDecimal
 * （{@link #arithmeticIsBigDecimalExactNotDouble} 与除法显式 scale）；③ 取不到值即失败，
 * 禁返回伪造数值（X-44）。</p>
 */
class CaliberFormulaEvaluatorTest {

    private static CaliberFormulaEvaluator.MapValueSupply supply() {
        return new CaliberFormulaEvaluator.MapValueSupply()
                .put("contract_base", new BigDecimal("1000000"))
                .put("attribution_ratio", new BigDecimal("0.6"))
                .put("realization_rate", new BigDecimal("0.85"))
                .put("amount", new BigDecimal("120000"))
                .put("cost_amount", new BigDecimal("100000"))
                .put("fte", new BigDecimal("10"))
                .put("zero_ratio", BigDecimal.ZERO);
    }

    private static BigDecimal eval(String formula) {
        return CaliberFormulaEvaluator.evaluate(formula, supply());
    }

    // ── 求值正例 ────────────────────────────────────────────────

    @Test
    @DisplayName("PRD-09 §2.2 环节额公式：contract_base × attribution_ratio × realization_rate")
    void evaluatesChainNodeAmountFormula() {
        BigDecimal r = eval("node_amount = contract_base * attribution_ratio * realization_rate");
        assertEquals(0, r.compareTo(new BigDecimal("510000")), r.toPlainString());
        // 乘法 scale = 各因子 scale 之和（0+1+2），BigDecimal 精确无尾差
        assertEquals(3, r.scale());
    }

    @Test
    @DisplayName("四则 + 括号优先级（括号先算，加减与乘除分层）")
    void honoursOperatorPrecedenceAndParentheses() {
        assertEquals(new BigDecimal("190000"),
                eval("profit = (amount + amount) - cost_amount + 50000"));
        assertEquals(new BigDecimal("40000"),
                eval("x = (amount - cost_amount) * 2"));
        assertEquals(new BigDecimal("360000"),
                eval("y = amount + amount * 2"));
    }

    @Test
    @DisplayName("数值一律 BigDecimal：0.1+0.2 精确等于 0.3，无浮点尾差")
    void arithmeticIsBigDecimalExactNotDouble() {
        CaliberFormulaEvaluator.MapValueSupply s = new CaliberFormulaEvaluator.MapValueSupply()
                .put("a", new BigDecimal("0.1"))
                .put("b", new BigDecimal("0.2"));
        // double 语义下 0.1+0.2 = 0.30000000000000004 ⇒ equals 断言即排除浮点参与
        assertEquals(new BigDecimal("0.3"), CaliberFormulaEvaluator.evaluate("t = a + b", s));
    }

    @Test
    @DisplayName("除法显式 scale + HALF_UP（默认 10 位），禁无上下文除法")
    void divisionUsesExplicitScaleAndRounding() {
        CaliberFormulaEvaluator.MapValueSupply s = new CaliberFormulaEvaluator.MapValueSupply()
                .put("amount", new BigDecimal("1"))
                .put("fte", new BigDecimal("3"));
        BigDecimal r = CaliberFormulaEvaluator.evaluate("k = amount / fte", s);
        assertEquals(10, r.scale());
        assertEquals(new BigDecimal("0.3333333333"), r);

        CaliberFormulaAst.FormulaAst ast = CaliberFormulaParser.parse("k = amount / fte");
        BigDecimal tight = CaliberFormulaEvaluator.evaluate(ast, s,
                CaliberFormulaEvaluator.UnitSupply.NONE, new CaliberFormulaEvaluator.Policy(2, 12));
        assertEquals(new BigDecimal("0.33"), tight);
    }

    @Test
    @DisplayName("ROUND：位数入参决定 scale，缺省 scale=0，HALF_UP")
    void roundHonoursScaleArgument() {
        CaliberFormulaEvaluator.MapValueSupply s = new CaliberFormulaEvaluator.MapValueSupply()
                .put("v", new BigDecimal("1234.567"));
        assertEquals(new BigDecimal("1234.57"), eval(s, "t = ROUND(v, 2)"));
        assertEquals(new BigDecimal("1235"), eval(s, "t = ROUND(v)"));
        assertEquals(new BigDecimal("1234.5670000000"), eval(s, "t = ROUND(v, 10)"));
    }

    @Test
    @DisplayName("SUM/MIN/MAX：SUM 恒等取聚合量，MIN/MAX 逐字比大小（BigDecimal 语义）")
    void whitelistFunctionsEvaluate() {
        assertEquals(new BigDecimal("120000"), eval("t = SUM(amount)"));
        assertEquals(new BigDecimal("100000"), eval("t = MIN(amount, cost_amount)"));
        assertEquals(new BigDecimal("120000"), eval("t = MAX(amount, cost_amount)"));
    }

    @Test
    @DisplayName("IF 三目：条件非零取真分支；未选中分支不求值（其取数缺陷不污染本次结果）")
    void ifFunctionIsLazyOnUntakenBranch() {
        assertEquals(new BigDecimal("1"), eval("t = IF(amount, 1, 0)"));
        assertEquals(new BigDecimal("0"), eval("t = IF(zero_ratio, 1, 0)"));

        CaliberFormulaEvaluator.MapValueSupply s = supply();
        // 假分支引用未登记符号：条件为真 ⇒ 不应触达 ⇒ 不报错
        assertEquals(new BigDecimal("1"), CaliberFormulaEvaluator.evaluate("t = IF(amount, 1, ghost_metric)", s));
    }

    @Test
    @DisplayName("指标[维度] 取值：限定维度命中该维度值，裸引用与限定引用取值路径分离")
    void qualifiedReferenceResolvesPerDimension() {
        CaliberFormulaEvaluator.MapValueSupply s = new CaliberFormulaEvaluator.MapValueSupply()
                .put("amount", "ACCEPTANCE", new BigDecimal("90000"))
                .put("amount", "COLLECTION", new BigDecimal("70000"))
                .put("amount", "PLAN", new BigDecimal("100000"));
        assertEquals(new BigDecimal("90000"), CaliberFormulaEvaluator.evaluate("t = amount[ACCEPTANCE]", s));
        assertEquals(new BigDecimal("30000"),
                CaliberFormulaEvaluator.evaluate("t = amount[PLAN] - amount[COLLECTION]", s));
        // 维度大小写不敏感（登记键统一大写）
        assertEquals(new BigDecimal("70000"), CaliberFormulaEvaluator.evaluate("t = amount[collection]", s));
    }

    private static BigDecimal eval(CaliberFormulaEvaluator.MapValueSupply s, String formula) {
        return CaliberFormulaEvaluator.evaluate(formula, s);
    }

    // ── 拒绝面 ──────────────────────────────────────────────────

    @Test
    @DisplayName("拒绝：未知标识符（UNKNOWN_SYMBOL），禁以 0 替真值")
    void rejectsUnknownSymbol() {
        CaliberFormulaEvaluationException e = assertThrows(CaliberFormulaEvaluationException.class,
                () -> eval("t = ghost_metric + 1"));
        assertEquals(Reason.UNKNOWN_SYMBOL, e.getReason());
        assertEquals("ghost_metric", e.getSymbol());
        assertEquals("UNKNOWN_SYMBOL", e.missing);
        assertTrue(e.getMessage().contains("X-44"), e.getMessage());
    }

    @Test
    @DisplayName("拒绝：维度缺失（MISSING_DIMENSION）≠ 未知标识符 —— 符号已登记但该维度无数据")
    void rejectsMissingDimension() {
        CaliberFormulaEvaluator.MapValueSupply s = new CaliberFormulaEvaluator.MapValueSupply()
                .put("amount", "ACCEPTANCE", new BigDecimal("90000"));
        CaliberFormulaEvaluationException e = assertThrows(CaliberFormulaEvaluationException.class,
                () -> CaliberFormulaEvaluator.evaluate("t = amount[SUBCONTRACT]", s));
        assertEquals(Reason.MISSING_DIMENSION, e.getReason());
        assertTrue(e.getMessage().contains("SUBCONTRACT"), e.getMessage());

        // 同符号登记了限定值但裸引用无值 ⇒ 仍是缺数，不是未知符号
        CaliberFormulaEvaluationException bare = assertThrows(CaliberFormulaEvaluationException.class,
                () -> CaliberFormulaEvaluator.evaluate("t = amount", s));
        assertEquals(Reason.MISSING_DIMENSION, bare.getReason());
    }

    @Test
    @DisplayName("拒绝：除零（DIVISION_BY_ZERO），禁 0 或均值兜底")
    void rejectsDivisionByZero() {
        CaliberFormulaEvaluationException e = assertThrows(CaliberFormulaEvaluationException.class,
                () -> eval("t = amount / (fte * 0)"));
        assertEquals(Reason.DIVISION_BY_ZERO, e.getReason());
        assertEquals("/", e.getSymbol());
        assertTrue(e.getMessage().contains("divisor evaluates to 0"), e.getMessage());
    }

    @Test
    @DisplayName("求值层自带白名单二次防御：绕过 V1 直接构造白名单外 AST 也被拒")
    void evaluatorItselfRejectsNonWhitelistFunction() {
        Token exp = new Token(TokenType.IDENT, "EXP", 1, 5, 4);
        Node one = new NumberNode(new Token(TokenType.NUMBER, "1", 1, 9, 8));
        FuncNode smuggled = new FuncNode(exp, List.of(one));
        CaliberFormulaEvaluationException e = assertThrows(CaliberFormulaEvaluationException.class,
                () -> CaliberFormulaEvaluator.evaluateExpression(smuggled, supply(),
                        CaliberFormulaEvaluator.UnitSupply.NONE, CaliberFormulaEvaluator.Policy.DEFAULT));
        assertEquals(Reason.NON_WHITELIST_FUNCTION, e.getReason());
        assertTrue(e.getMessage().contains("whitelist"), e.getMessage());
    }

    @Test
    @DisplayName("拒绝：量纲冲突（金额 + 比率直加），单位代数与 ontology V3 同源")
    void rejectsUnitConflict() {
        CaliberFormulaEvaluationException e = assertThrows(CaliberFormulaEvaluationException.class,
                () -> eval("t = amount + attribution_ratio"));
        assertEquals(Reason.UNIT_CONFLICT, e.getReason());
        assertTrue(e.getMessage().contains("amount+rate direct addition forbidden"), e.getMessage());

        // 金额 × 比率 = 金额（PRD-09 §1.2 V3）⇒ 正例不误伤
        assertEquals(new BigDecimal("72000.0"), eval("t = amount * attribution_ratio"));
        // 金额 / 人数 = 金额（人均口径）
        assertEquals(0, eval("t = amount / fte").compareTo(new BigDecimal("12000")));
    }

    @Test
    @DisplayName("拒绝：MIN/MAX/IF 分支不同量纲（PERSON_COUNT 与 AMOUNT 混取）")
    void rejectsMixedUnitFunctionBranches() {
        CaliberFormulaEvaluationException min = assertThrows(CaliberFormulaEvaluationException.class,
                () -> eval("t = MIN(amount, fte)"));
        assertEquals(Reason.UNIT_CONFLICT, min.getReason());
        assertThrows(CaliberFormulaEvaluationException.class, () -> eval("t = IF(amount, amount, fte)"));
    }

    @Test
    @DisplayName("拒绝：ROUND 位数非整数 / 越界（策略上限 12）")
    void rejectsInvalidRoundScale() {
        CaliberFormulaEvaluationException nonInteger = assertThrows(CaliberFormulaEvaluationException.class,
                () -> eval("t = ROUND(amount, 2.5)"));
        assertEquals(Reason.ROUND_SCALE_INVALID, nonInteger.getReason());

        CaliberFormulaEvaluationException tooDeep = assertThrows(CaliberFormulaEvaluationException.class,
                () -> eval("t = ROUND(amount, 13)"));
        assertEquals(Reason.ROUND_SCALE_INVALID, tooDeep.getReason());
        assertTrue(tooDeep.getMessage().contains("outside 0..12"), tooDeep.getMessage());
    }

    @Test
    @DisplayName("防御：AST 嵌套超 MAX_DEPTH 报数据缺陷，禁 StackOverflowError")
    void rejectsOverDeepNesting() {
        String deep = "t = " + "1 + ".repeat(CaliberFormulaEvaluator.MAX_DEPTH + 8) + "1";
        CaliberFormulaEvaluationException e = assertThrows(CaliberFormulaEvaluationException.class,
                () -> CaliberFormulaEvaluator.evaluate(deep, new CaliberFormulaEvaluator.MapValueSupply()));
        assertEquals(Reason.NESTING_TOO_DEEP, e.getReason());
    }

    @Test
    @DisplayName("求值失败是真实 422（ECOS-COG-422F），禁 200 + 假数据（X-44）")
    void evaluationFailureRendersRealHttp422() {
        CaliberFormulaEvaluationException e = assertThrows(CaliberFormulaEvaluationException.class,
                () -> eval("t = 1 / 0"));
        assertEquals(422, e.httpStatus);
        assertEquals("ECOS-COG-422F", e.errorCode);
    }

    // ── 策略与供给面 ────────────────────────────────────────────

    @Test
    @DisplayName("Policy 只接受 BigDecimal 可行域（0..34），越界即构造期失败")
    void policyValidatesItsOwnBounds() {
        assertThrows(IllegalArgumentException.class, () -> new CaliberFormulaEvaluator.Policy(-1, 12));
        assertThrows(IllegalArgumentException.class, () -> new CaliberFormulaEvaluator.Policy(10, 35));
        assertEquals(10, CaliberFormulaEvaluator.Policy.DEFAULT.divisionScale());
        assertEquals(12, CaliberFormulaEvaluator.Policy.DEFAULT.maxRoundScale());
    }

    @Test
    @DisplayName("UnitSupply.NONE 关闭量纲二次防御（静态面已校验的公式可纯求值）")
    void noneUnitSupplySkipsDimensionCheck() {
        CaliberFormulaAst.FormulaAst ast = CaliberFormulaParser.parse("t = amount + attribution_ratio");
        assertEquals(new BigDecimal("120000.6"), CaliberFormulaEvaluator.evaluate(ast, supply(),
                CaliberFormulaEvaluator.UnitSupply.NONE, CaliberFormulaEvaluator.Policy.DEFAULT));
    }

    @Test
    @DisplayName("内置事实单位表与 ontology 同源字段逐条可查（未登记符号 ⇒ 宽松）")
    void factUnitTableMatchesOntologyRegistry() {
        assertEquals(Optional.of("AMOUNT"), CaliberFormulaEvaluator.CaliberFactUnits.unitOf("contract_base"));
        assertEquals(Optional.of("RATE"), CaliberFormulaEvaluator.CaliberFactUnits.unitOf("attribution_ratio"));
        assertEquals(Optional.of("PERSON_COUNT"), CaliberFormulaEvaluator.CaliberFactUnits.unitOf("fte"));
        assertEquals(Optional.empty(), CaliberFormulaEvaluator.CaliberFactUnits.unitOf("ghost_field"));
    }

    @Test
    @DisplayName("入口守护：null AST / 空公式文本都不静默返回 0")
    void entryGuardsAreFailLoud() {
        assertThrows(IllegalArgumentException.class,
                () -> CaliberFormulaEvaluator.evaluate((CaliberFormulaAst.FormulaAst) null, supply()));
        assertThrows(CaliberFormulaSyntaxException.class, () -> CaliberFormulaParser.parse(null));
    }
}
