package com.chinacreator.gzcm.engine.cognitive2.service.dsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 口径公式 DSL 的 AST / Token 共享契约（H-2b，详设-09 v1.2 §十一 H-2 认知侧半区）。
 *
 * <p><b>与 ontology 侧同源</b>：节点形态与
 * {@code ontology-engine-impl} 的 {@code CaliberDslParser}（H-2 V1 交付）逐一对应
 * （NumberNode / RefNode / QualifiedRefNode / FuncNode / BinaryNode / FormulaAst，
 * {@code FUNC_ARITY} 白名单与元数区间逐字相同）。之所以在认知侧<b>独立实现同一 AST</b>
 * 而非直接依赖：① 铁律 §2.1 禁跨引擎 import {@code *-engine-impl}（ArchUnit 守门）；
 * ② 对侧 AST 类型是包内可见（package-private）类，跨包不可引用。两者都指向
 * "最小改动 = 认知侧同文法自实现"，不新增 Maven 模块、不动 ontology 已有实现。</p>
 *
 * <p><b>文法（EBNF，PRD-09 v1.2 §1.2 原文，与 {@link CaliberFormulaParser} 注释同源）</b>：</p>
 * <pre>
 *   formula := IDENT '=' expr
 *   expr    := term ( ( '+' | '-' ) term )*
 *   term    := factor ( ( '*' | '/' ) factor )*
 *   factor  := NUMBER | IDENT | IDENT '[' IDENT ']' | '(' expr ')' | FUNC '(' args ')'
 *   args    := expr ( ',' expr )*
 *   FUNC    := 'SUM' | 'ROUND' | 'MIN' | 'MAX' | 'IF'   // 白名单，越界即 V1 语法拒绝
 * </pre>
 */
public final class CaliberFormulaAst {

    /** 白名单函数 → 允许的参数个数区间（与 ontology {@code CaliberDslParser.FUNC_ARITY} 同源）。 */
    public static final Map<String, int[]> FUNC_ARITY;

    static {
        Map<String, int[]> arity = new LinkedHashMap<>();
        arity.put("SUM", new int[]{1, 1});
        arity.put("ROUND", new int[]{1, 2});
        arity.put("MIN", new int[]{2, 2});
        arity.put("MAX", new int[]{2, 2});
        arity.put("IF", new int[]{3, 3});
        FUNC_ARITY = Collections.unmodifiableMap(arity);
    }

    private CaliberFormulaAst() {
    }

    // ────────────────────────────────────────────────────────────
    // Token
    // ────────────────────────────────────────────────────────────

    /** 符号类别（与 ontology 侧 {@code TokenType} 同集合）。 */
    public enum TokenType { IDENT, NUMBER, OP, ASSIGN, LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, EOF }

    /** DSL token：文本 + 行列/偏移位置（line/column 1 起、offset 0 起），供错误定位。 */
    public static final class Token {
        public final TokenType type;
        public final String text;
        public final int line;
        public final int column;
        public final int offset;

        public Token(TokenType type, String text, int line, int column, int offset) {
            this.type = type;
            this.text = text;
            this.line = line;
            this.column = column;
            this.offset = offset;
        }

        /** 定位短语：{@code 'symbol' (line L, column C, offset N)} — 与 ontology 侧同格式。 */
        public String location() {
            return "'" + text + "' (line " + line + ", column " + column + ", offset " + offset + ")";
        }

        @Override
        public String toString() {
            return type + "(" + text + ")@" + line + ":" + column;
        }
    }

    // ────────────────────────────────────────────────────────────
    // Node
    // ────────────────────────────────────────────────────────────

    /** AST 节点基类；{@code token} 为错误定位锚点。 */
    public abstract static class Node {
        public final Token token;

        protected Node(Token token) {
            this.token = token;
        }
    }

    /** 十进制字面量原文（不做任何 double 转换，求值期直接进 BigDecimal）。 */
    public static final class NumberNode extends Node {
        public final String value;

        public NumberNode(Token token) {
            super(token);
            this.value = token.text;
        }
    }

    /** 裸符号引用（指标码 / 事实字段）。 */
    public static final class RefNode extends Node {
        public final Token name;

        public RefNode(Token name) {
            super(name);
            this.name = name;
        }

        public String symbol() {
            return name.text;
        }
    }

    /** {@code IDENT '[' IDENT ']'}：符号 + 限定维度（如 {@code amount[ACCEPTANCE]}）。 */
    public static final class QualifiedRefNode extends Node {
        public final Token name;
        public final Token qualifier;

        public QualifiedRefNode(Token name, Token qualifier) {
            super(name);
            this.name = name;
            this.qualifier = qualifier;
        }

        public String symbol() {
            return name.text;
        }

        public String dimension() {
            return qualifier.text;
        }
    }

    /** 白名单函数调用。 */
    public static final class FuncNode extends Node {
        public final Token name;
        public final List<Node> args;

        public FuncNode(Token name, List<Node> args) {
            super(name);
            this.name = name;
            this.args = Collections.unmodifiableList(new ArrayList<>(args));
        }

        public String function() {
            return name.text;
        }
    }

    /** 二元算符节点（+ - * /）。 */
    public static final class BinaryNode extends Node {
        public final Token op;
        public final Node left;
        public final Node right;

        public BinaryNode(Token op, Node left, Node right) {
            super(op);
            this.op = op;
            this.left = left;
            this.right = right;
        }

        public String operator() {
            return op.text;
        }
    }

    /** {@code formula := IDENT '=' expr} 的顶层产物。 */
    public static final class FormulaAst {
        public final Token target;
        public final Node expr;

        public FormulaAst(Token target, Node expr) {
            this.target = target;
            this.expr = expr;
        }

        public String targetName() {
            return target.text;
        }
    }
}
