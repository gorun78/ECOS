package com.chinacreator.gzcm.engine.cognitive2.service.dsl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 口径公式 DSL 的 V1 语法面单测（详设-09 v1.2 §十一 H-2b，认知侧）。
 *
 * <p>判据 = 卷 09 §十一 H-2 的 EBNF 原文：四则 + 括号 + 白名单函数 + {@code IDENT[IDENT]}，
 * 白名单外一律语法拒绝（禁脚本引擎 ⇒ 不识别任何"其他函数"）。</p>
 */
class CaliberFormulaParserTest {

    @Test
    @DisplayName("文法正例：target=expr / 四则 / 括号 / 指标[维度] / 白名单函数")
    void acceptsGrammar() {
        CaliberFormulaAst.FormulaAst ast = CaliberFormulaParser.parse(
                "node_amount = contract_base * attribution_ratio * realization_rate");
        assertEquals("node_amount", ast.targetName());
        assertInstanceOf(CaliberFormulaAst.BinaryNode.class, ast.expr);

        CaliberFormulaAst.BinaryNode grouped = assertInstanceOf(CaliberFormulaAst.BinaryNode.class,
                CaliberFormulaParser.parse("profit = (revenue - cost_amount) * 2").expr);
        assertEquals("*", grouped.operator());
        assertInstanceOf(CaliberFormulaAst.BinaryNode.class, grouped.left);

        CaliberFormulaAst.QualifiedRefNode qualified = assertInstanceOf(CaliberFormulaAst.QualifiedRefNode.class,
                CaliberFormulaParser.parse("income = amount[ACCEPTANCE]").expr);
        assertEquals("amount", qualified.symbol());
        assertEquals("ACCEPTANCE", qualified.dimension());

        CaliberFormulaAst.FuncNode func = assertInstanceOf(CaliberFormulaAst.FuncNode.class,
                CaliberFormulaParser.parse("r = ROUND(gross_margin, 2)").expr);
        assertEquals("ROUND", func.function());
        assertEquals(2, func.args.size());
    }

    @Test
    @DisplayName("SUM/MIN/MAX/IF 元数区间与 ontology 侧同源（SUM{1} ROUND{1..2} MIN{2} MAX{2} IF{3}）")
    void whitelistArityIsSingleSource() {
        assertEquals(1, CaliberFormulaAst.FUNC_ARITY.get("SUM")[0]);
        assertEquals(1, CaliberFormulaAst.FUNC_ARITY.get("SUM")[1]);
        assertEquals(1, CaliberFormulaAst.FUNC_ARITY.get("ROUND")[0]);
        assertEquals(2, CaliberFormulaAst.FUNC_ARITY.get("ROUND")[1]);
        assertEquals(2, CaliberFormulaAst.FUNC_ARITY.get("MIN")[0]);
        assertEquals(2, CaliberFormulaAst.FUNC_ARITY.get("MIN")[1]);
        assertEquals(2, CaliberFormulaAst.FUNC_ARITY.get("MAX")[0]);
        assertEquals(2, CaliberFormulaAst.FUNC_ARITY.get("MAX")[1]);
        assertEquals(3, CaliberFormulaAst.FUNC_ARITY.get("IF")[0]);
        assertEquals(3, CaliberFormulaAst.FUNC_ARITY.get("IF")[1]);
        assertEquals(5, CaliberFormulaAst.FUNC_ARITY.size());
    }

    @Test
    @DisplayName("拒绝：白名单外函数（无脚本引擎 ⇒ 不解释任何其它函数名）")
    void rejectsNonWhitelistFunction() {
        CaliberFormulaSyntaxException e = assertThrows(CaliberFormulaSyntaxException.class,
                () -> CaliberFormulaParser.parse("x = EXP(revenue)"));
        assertTrue(e.getMessage().contains("outside the whitelist"), e.getMessage());
        assertTrue(e.getMessage().contains("no scripting engine"), e.getMessage());
        assertEquals("EXP", e.getSymbol());
    }

    @Test
    @DisplayName("拒绝：小写函数名（白名单字面量大小写敏感，与对侧一致，禁宽松匹配）")
    void rejectsLowerCaseFunctionName() {
        assertThrows(CaliberFormulaSyntaxException.class,
                () -> CaliberFormulaParser.parse("x = round(revenue, 2)"));
    }

    @Test
    @DisplayName("拒绝：元数越界（IF 三目必须恰 3 个参数）")
    void rejectsArityViolation() {
        CaliberFormulaSyntaxException e = assertThrows(CaliberFormulaSyntaxException.class,
                () -> CaliberFormulaParser.parse("x = IF(revenue, 1)"));
        assertTrue(e.getMessage().contains("expects 3 argument(s) but got 2"), e.getMessage());
    }

    @Test
    @DisplayName("拒绝：缺 target / 缺 '=' / 尾随垃圾 / 空文本")
    void rejectsMalformedFormulaShape() {
        assertThrows(CaliberFormulaSyntaxException.class, () -> CaliberFormulaParser.parse("revenue + 1"));
        assertThrows(CaliberFormulaSyntaxException.class, () -> CaliberFormulaParser.parse("x revenue"));
        assertThrows(CaliberFormulaSyntaxException.class, () -> CaliberFormulaParser.parse("x = 1 2"));
        CaliberFormulaSyntaxException blank = assertThrows(CaliberFormulaSyntaxException.class,
                () -> CaliberFormulaParser.parse("   "));
        assertTrue(blank.getMessage().contains("formula text is blank"), blank.getMessage());
    }

    @Test
    @DisplayName("拒绝：非法字符与未闭合括号，且错误定位到符号行列偏移")
    void rejectsIllegalCharacterWithLocation() {
        String src = "x = revenue % 2";
        CaliberFormulaSyntaxException illegal = assertThrows(CaliberFormulaSyntaxException.class,
                () -> CaliberFormulaParser.parse(src));
        assertTrue(illegal.getMessage().contains("illegal character"), illegal.getMessage());
        assertEquals(1, illegal.getLine());
        assertEquals(src.indexOf('%') + 1, illegal.getColumn());
        assertEquals(src.indexOf('%'), illegal.getOffset());

        CaliberFormulaSyntaxException unclosed = assertThrows(CaliberFormulaSyntaxException.class,
                () -> CaliberFormulaParser.parse("x = (revenue + 1"));
        assertTrue(unclosed.getMessage().contains("expected ')'"), unclosed.getMessage());
    }

    @Test
    @DisplayName("文法无一元负号（与 ontology 侧 EBNF 一致，禁悄悄放宽）")
    void rejectsUnaryMinus() {
        assertThrows(CaliberFormulaSyntaxException.class, () -> CaliberFormulaParser.parse("x = -1"));
    }

    @Test
    @DisplayName("错误面是 400 语义（ECOS-COG-400F），禁 200 + 假数据（X-44）")
    void syntaxErrorCarriesRealHttp400() {
        CaliberFormulaSyntaxException e = assertThrows(CaliberFormulaSyntaxException.class,
                () -> CaliberFormulaParser.parse("x ="));
        assertEquals(400, e.httpStatus);
        assertEquals("ECOS-COG-400F", e.errorCode);
        assertEquals("formula-dsl V1 语法", e.missing);
    }

    @Test
    @DisplayName("Token.location() 形态与 ontology 侧同文（line/column 1 起、offset 0 起）")
    void tokenLocationFormat() {
        CaliberFormulaAst.Token t = new CaliberFormulaAst.Token(
                CaliberFormulaAst.TokenType.IDENT, "revenue", 1, 5, 4);
        assertEquals("'revenue' (line 1, column 5, offset 4)", t.location());
        assertInstanceOf(CaliberFormulaAst.RefNode.class, CaliberFormulaParser.parse("x = revenue").expr);
    }
}
