package com.chinacreator.gzcm.engine.ontology.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * H-2（详设-09 v1.2 §十一 / PRD-09 v1.2 §1.2 V1）— 口径公式 DSL 受限递归下降解析器。
 *
 * <p><b>文法（EBNF，PRD-09 §1.2 原文，与 cognitive-engine 求值器同源）</b>：</p>
 * <pre>
 *   formula := IDENT '=' expr
 *   expr    := term ( ( '+' | '-' ) term )*
 *   term    := factor ( ( '*' | '/' ) factor )*
 *   factor  := NUMBER | IDENT | IDENT '[' IDENT ']' | '(' expr ')' | FUNC '(' args ')'
 *   args    := expr ( ',' expr )*
 *   FUNC    := 'SUM' | 'ROUND' | 'MIN' | 'MAX' | 'IF'   // 白名单，越界即 V1 语法拒绝
 * </pre>
 *
 * <p><b>安全纪律</b>：本类只做<b>解析与结构校验</b>，不求值、不引入脚本引擎
 * （禁 SpEL/Groovy/ Nashorn — PRD-09 §1.2「禁脚本引擎」；求值属 cognitive-engine，
 * 本册 H-2 边界外）。文法自下而上按优先级组合（expr 加减 &lt; term 乘除），
 * {@code FUNC} 在 IDENT 后紧跟 {@code '('} 时识别并按白名单 + 元数校验。</p>
 *
 * <p><b>符号定位</b>：token 携带 line（1 起）/ column（1 起）/ offset（0 起），
 * 语法错误经 {@link SyntaxError} 携带出错符号原文与位置，满足
 * 「违一即 400，错误信息定位到符号位置」（PRD-09 §1.2）。</p>
 */
public final class CaliberDslParser {

    /** 白名单函数 → 允许的参数个数区间（EBNF FUNC + 元数约束）。 */
    static final Map<String, int[]> FUNC_ARITY = new LinkedHashMap<>();

    static {
        FUNC_ARITY.put("SUM", new int[]{1, 1});
        FUNC_ARITY.put("ROUND", new int[]{1, 2});
        FUNC_ARITY.put("MIN", new int[]{2, 2});
        FUNC_ARITY.put("MAX", new int[]{2, 2});
        FUNC_ARITY.put("IF", new int[]{3, 3});
    }

    private CaliberDslParser() {
    }

    // ────────────────────────────────────────────────────────────
    // Token
    // ────────────────────────────────────────────────────────────

    enum TokenType { IDENT, NUMBER, OP, ASSIGN, LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, EOF }

    /** DSL token：文本 + 行列/偏移位置（column 1 起、offset 0 起）。 */
    static final class Token {
        final TokenType type;
        final String text;
        final int line;
        final int column;
        final int offset;

        Token(TokenType type, String text, int line, int column, int offset) {
            this.type = type;
            this.text = text;
            this.line = line;
            this.column = column;
            this.offset = offset;
        }

        /** 定位短语：{@code 'symbol' (line L, column C, offset N)} — 400 消息统一格式。 */
        String location() {
            return "'" + text + "' (line " + line + ", column " + column + ", offset " + offset + ")";
        }

        @Override
        public String toString() {
            return type + "(" + text + ")@" + line + ":" + column;
        }
    }

    // ────────────────────────────────────────────────────────────
    // AST
    // ────────────────────────────────────────────────────────────

    /** AST 节点基类；opToken/firstToken 供错误定位。 */
    static abstract class Node {
        Token token; // 定位锚点（首个相关 token）
    }

    static final class NumberNode extends Node {
        final String value;
        NumberNode(Token t) { this.token = t; this.value = t.text; }
    }

    static final class RefNode extends Node {
        final Token name;
        RefNode(Token name) { this.token = name; this.name = name; }
    }

    static final class QualifiedRefNode extends Node {
        final Token name;
        final Token qualifier;
        QualifiedRefNode(Token name, Token qualifier) { this.token = name; this.name = name; this.qualifier = qualifier; }
    }

    static final class FuncNode extends Node {
        final Token name;
        final List<Node> args;
        FuncNode(Token name, List<Node> args) { this.token = name; this.name = name; this.args = args; }
    }

    static final class BinaryNode extends Node {
        final Token op;
        final Node left;
        final Node right;
        BinaryNode(Token op, Node left, Node right) { this.token = op; this.op = op; this.left = left; this.right = right; }
    }

    /** formula := IDENT '=' expr 的顶层产物。 */
    static final class FormulaAst {
        final Token target;
        final Node expr;
        FormulaAst(Token target, Node expr) { this.target = target; this.expr = expr; }
    }

    // ────────────────────────────────────────────────────────────
    // 异常（V1 载体）
    // ────────────────────────────────────────────────────────────

    /** V1 语法错误：携带出错符号与位置。 */
    static final class SyntaxError extends RuntimeException {
        private final String symbol;
        private final int line;
        private final int column;
        private final int offset;

        SyntaxError(String message, String symbol, int line, int column, int offset) {
            super(message);
            this.symbol = symbol;
            this.line = line;
            this.column = column;
            this.offset = offset;
        }

        String getSymbol() { return symbol; }
        int getLine() { return line; }
        int getColumn() { return column; }
        int getOffset() { return offset; }
    }

    // ────────────────────────────────────────────────────────────
    // 入口
    // ────────────────────────────────────────────────────────────

    /** 解析 {@code formula := IDENT '=' expr}；失败抛 {@link SyntaxError}。 */
    public static FormulaAst parse(String formulaText) {
        if (formulaText == null || formulaText.isBlank()) {
            throw new SyntaxError("DSL_V1 syntax: formula text is blank", "", 1, 1, 0);
        }
        Lexer lexer = new Lexer(formulaText);
        List<Token> tokens = lexer.tokenize();
        Parser p = new Parser(tokens);
        return p.parseFormula();
    }

    // ────────────────────────────────────────────────────────────
    // Lexer
    // ────────────────────────────────────────────────────────────

    /** 手写字符级 tokenizer：产出带行列/偏移位置的 token（NUMBER 支持小数）。 */
    private static final class Lexer {
        private final String src;
        private int pos;
        private int line = 1;
        private int column = 1;

        Lexer(String src) { this.src = src; }

        private List<Token> tokenize() {
            List<Token> out = new ArrayList<>();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '\n') { advance(); line++; column = 1; continue; }
                if (Character.isWhitespace(c) || c == '\r') { advance(); continue; }
                int start = pos;
                int startLine = line;
                int startCol = column;
                if (Character.isLetter(c) || c == '_') {
                    while (pos < src.length()
                            && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
                        advance();
                    }
                    out.add(new Token(TokenType.IDENT, src.substring(start, pos), startLine, startCol, start));
                } else if (Character.isDigit(c)) {
                    while (pos < src.length() && Character.isDigit(src.charAt(pos))) advance();
                    if (pos < src.length() && src.charAt(pos) == '.'
                            && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1))) {
                        advance();
                        while (pos < src.length() && Character.isDigit(src.charAt(pos))) advance();
                    }
                    out.add(new Token(TokenType.NUMBER, src.substring(start, pos), startLine, startCol, start));
                } else {
                    advance();
                    TokenType t = switch (c) {
                        case '=' -> TokenType.ASSIGN;
                        case '+', '-', '*', '/' -> TokenType.OP;
                        case '(' -> TokenType.LPAREN;
                        case ')' -> TokenType.RPAREN;
                        case '[' -> TokenType.LBRACKET;
                        case ']' -> TokenType.RBRACKET;
                        case ',' -> TokenType.COMMA;
                        default -> throw new SyntaxError(
                                "DSL_V1 syntax: illegal character " + "'" + c + "'"
                                        + " (line " + startLine + ", column " + startCol + ", offset " + start + ")",
                                String.valueOf(c), startLine, startCol, start);
                    };
                    out.add(new Token(t, String.valueOf(c), startLine, startCol, start));
                }
            }
            out.add(new Token(TokenType.EOF, "<eof>", line, column, pos));
            return out;
        }

        private void advance() { pos++; column++; }
    }

    // ────────────────────────────────────────────────────────────
    // Parser（受限递归下降）
    // ────────────────────────────────────────────────────────────

    private static final class Parser {
        private final List<Token> tokens;
        private int idx;

        Parser(List<Token> tokens) { this.tokens = tokens; }

        private Token peek() { return tokens.get(idx); }
        private Token next() { return tokens.get(idx++); }

        private SyntaxError errorAt(Token t, String what) {
            return new SyntaxError("DSL_V1 syntax: " + what + " — unexpected " + t.location(),
                    t.text, t.line, t.column, t.offset);
        }

        FormulaAst parseFormula() {
            Token target = expect(TokenType.IDENT, "formula target IDENT before '='");
            Token assign = peek();
            if (assign.type != TokenType.ASSIGN) {
                throw errorAt(assign, "expected '=' after formula target");
            }
            next();
            Node expr = parseExpr();
            Token eof = peek();
            if (eof.type != TokenType.EOF) {
                throw errorAt(eof, "expected end of formula");
            }
            return new FormulaAst(target, expr);
        }

        // expr := term ( ( '+' | '-' ) term )*
        Node parseExpr() {
            Node left = parseTerm();
            while (peek().type == TokenType.OP
                    && (peek().text.equals("+") || peek().text.equals("-"))) {
                Token op = next();
                left = new BinaryNode(op, left, parseTerm());
            }
            return left;
        }

        // term := factor ( ( '*' | '/' ) factor )*
        Node parseTerm() {
            Node left = parseFactor();
            while (peek().type == TokenType.OP
                    && (peek().text.equals("*") || peek().text.equals("/"))) {
                Token op = next();
                left = new BinaryNode(op, left, parseFactor());
            }
            return left;
        }

        // factor := NUMBER | IDENT | IDENT '[' IDENT ']' | '(' expr ')' | FUNC '(' args ')'
        Node parseFactor() {
            Token t = peek();
            switch (t.type) {
                case NUMBER:
                    next();
                    return new NumberNode(t);
                case LPAREN: {
                    next();
                    Node inner = parseExpr();
                    if (peek().type != TokenType.RPAREN) {
                        throw errorAt(peek(), "expected ')'");
                    }
                    next();
                    return inner;
                }
                case IDENT: {
                    next();
                    if (peek().type == TokenType.LPAREN) {
                        return parseFuncCall(t);
                    }
                    if (peek().type == TokenType.LBRACKET) {
                        next(); // '['
                        Token q = expect(TokenType.IDENT, "qualifier IDENT inside '[ ]'");
                        if (peek().type != TokenType.RBRACKET) {
                            throw errorAt(peek(), "expected ']' after qualifier");
                        }
                        next();
                        return new QualifiedRefNode(t, q);
                    }
                    return new RefNode(t);
                }
                default:
                    throw errorAt(t, "expected factor (NUMBER / IDENT / '(' expr ')' / FUNC '(' args ')')");
            }
        }

        private Node parseFuncCall(Token name) {
            next(); // '('
            if (!FUNC_ARITY.containsKey(name.text)) {
                throw new SyntaxError(
                        "DSL_V1 syntax: function " + name.location()
                                + " is outside the whitelist " + FUNC_ARITY.keySet()
                                + " (only SUM/ROUND/MIN/MAX/IF are allowed; no scripting engine)",
                        name.text, name.line, name.column, name.offset);
            }
            List<Node> args = new ArrayList<>();
            if (peek().type == TokenType.RPAREN) {
                // 空参 → 交由元数校验报错（SUM() 等）
            } else {
                args.add(parseExpr());
                while (peek().type == TokenType.COMMA) {
                    next();
                    args.add(parseExpr());
                }
            }
            if (peek().type != TokenType.RPAREN) {
                throw errorAt(peek(), "expected ')' or ',' in function arguments");
            }
            next();
            int[] arity = FUNC_ARITY.get(name.text);
            if (args.size() < arity[0] || args.size() > arity[1]) {
                throw new SyntaxError(
                        "DSL_V1 syntax: function " + name.text + " expects "
                                + (arity[0] == arity[1] ? arity[0] : arity[0] + ".." + arity[1])
                                + " argument(s) but got " + args.size() + " — " + name.location(),
                        name.text, name.line, name.column, name.offset);
            }
            return new FuncNode(name, args);
        }

        private Token expect(TokenType type, String what) {
            Token t = peek();
            if (t.type != type) {
                throw errorAt(t, "expected " + what);
            }
            return next();
        }
    }
}
