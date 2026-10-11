package com.chinacreator.gzcm.engine.cognitive2.service.dsl;

import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.BinaryNode;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.FuncNode;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.Node;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.NumberNode;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.QualifiedRefNode;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.RefNode;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.Token;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 口径公式 DSL 受限递归下降解析器（认知侧，H-2b；文法与 ontology 侧 H-2 V1 交付同源）。
 *
 * <pre>
 *   formula := IDENT '=' expr
 *   expr    := term ( ( '+' | '-' ) term )*
 *   term    := factor ( ( '*' | '/' ) factor )*
 *   factor  := NUMBER | IDENT | IDENT '[' IDENT ']' | '(' expr ')' | FUNC '(' args ')'
 *   args    := expr ( ',' expr )*
 *   FUNC    := 'SUM' | 'ROUND' | 'MIN' | 'MAX' | 'IF'   // 白名单，越界即语法拒绝
 * </pre>
 *
 * <p><b>禁脚本引擎</b>：本类只做词法/语法分析，产出 {@link CaliberFormulaAst.FormulaAst}；
 * 求值由 {@link CaliberFormulaEvaluator} 对自有 AST 递归完成。全链路无 SpEL / Groovy /
 * Nashorn / 反射方法调用（PRD-09 v1.2 §1.2「禁脚本引擎」）。</p>
 */
public final class CaliberFormulaParser {

    private CaliberFormulaParser() {
    }

    /** 解析 {@code formula := IDENT '=' expr}；任何语法违例抛 {@link CaliberFormulaSyntaxException}。 */
    public static CaliberFormulaAst.FormulaAst parse(String formulaText) {
        if (formulaText == null || formulaText.isBlank()) {
            throw new CaliberFormulaSyntaxException("DSL_V1 syntax: formula text is blank", "", 1, 1, 0);
        }
        List<Token> tokens = new Lexer(formulaText).tokenize();
        return new Parser(tokens).parseFormula();
    }

    // ────────────────────────────────────────────────────────────
    // Lexer（字符级手写词法，NUMBER 支持小数）
    // ────────────────────────────────────────────────────────────

    private static final class Lexer {
        private final String src;
        private int pos;
        private int line = 1;
        private int column = 1;

        Lexer(String src) {
            this.src = src;
        }

        List<Token> tokenize() {
            List<Token> out = new ArrayList<>();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '\n') {
                    advance();
                    line++;
                    column = 1;
                    continue;
                }
                if (Character.isWhitespace(c) || c == '\r') {
                    advance();
                    continue;
                }
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
                    while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                        advance();
                    }
                    if (pos < src.length() && src.charAt(pos) == '.'
                            && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1))) {
                        advance();
                        while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                            advance();
                        }
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
                        default -> throw new CaliberFormulaSyntaxException(
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

        private void advance() {
            pos++;
            column++;
        }
    }

    // ────────────────────────────────────────────────────────────
    // Parser
    // ────────────────────────────────────────────────────────────

    private static final class Parser {
        private final List<Token> tokens;
        private int idx;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        private Token peek() {
            return tokens.get(idx);
        }

        private Token next() {
            return tokens.get(idx++);
        }

        private CaliberFormulaSyntaxException errorAt(Token t, String what) {
            return new CaliberFormulaSyntaxException(
                    "DSL_V1 syntax: " + what + " — unexpected " + t.location(),
                    t.text, t.line, t.column, t.offset);
        }

        CaliberFormulaAst.FormulaAst parseFormula() {
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
            return new CaliberFormulaAst.FormulaAst(target, expr);
        }

        // expr := term ( ( '+' | '-' ) term )*
        private Node parseExpr() {
            Node left = parseTerm();
            while (peek().type == TokenType.OP
                    && (peek().text.equals("+") || peek().text.equals("-"))) {
                Token op = next();
                left = new BinaryNode(op, left, parseTerm());
            }
            return left;
        }

        // term := factor ( ( '*' | '/' ) factor )*
        private Node parseTerm() {
            Node left = parseFactor();
            while (peek().type == TokenType.OP
                    && (peek().text.equals("*") || peek().text.equals("/"))) {
                Token op = next();
                left = new BinaryNode(op, left, parseFactor());
            }
            return left;
        }

        // factor := NUMBER | IDENT | IDENT '[' IDENT ']' | '(' expr ')' | FUNC '(' args ')'
        private Node parseFactor() {
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
                        next();
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
            next();
            Map<String, int[]> arity = CaliberFormulaAst.FUNC_ARITY;
            if (!arity.containsKey(name.text)) {
                throw new CaliberFormulaSyntaxException(
                        "DSL_V1 syntax: function " + name.location()
                                + " is outside the whitelist " + arity.keySet()
                                + " (only SUM/ROUND/MIN/MAX/IF are allowed; no scripting engine)",
                        name.text, name.line, name.column, name.offset);
            }
            List<Node> args = new ArrayList<>();
            if (peek().type != TokenType.RPAREN) {
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
            int[] range = arity.get(name.text);
            if (args.size() < range[0] || args.size() > range[1]) {
                throw new CaliberFormulaSyntaxException(
                        "DSL_V1 syntax: function " + name.text + " expects "
                                + (range[0] == range[1] ? range[0] : range[0] + ".." + range[1])
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
