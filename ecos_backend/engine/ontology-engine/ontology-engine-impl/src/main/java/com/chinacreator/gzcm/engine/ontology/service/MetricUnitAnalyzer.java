package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.model.UnitDimension;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 指标表达式单位推导器（F03-02 V2 门禁的"可执行定义"，替代 PRD-03 的"单位可推导且不冲突"）。
 *
 * <p>把表达式抽象为四则树，叶子带单位（{@link UnitDimension}），按文档白名单推导：
 * <ul>
 *   <li>{@code 金额±金额 = 金额}、{@code 月±月 = 月}</li>
 *   <li>{@code 金额/金额 = 比率}、{@code 金额/人数 = 金额}</li>
 *   <li>常量（数字字面量，{@link UnitDimension#NONE}）对运算透明：{@code X op 常量 = X}</li>
 * </ul>
 * 其他组合 → 冲突（{@code conflicted=true}，{@code errorCode=ECOS-ONTO-021}）并返回推导链供 400 响应。
 *
 * <p>纯函数无 IO，可确定性单测（{@code MetricUnitDerivationTest} 覆盖 10 项指标 + 反例）。
 */
@Component
public class MetricUnitAnalyzer {

    /** 出错码：单位推导冲突（V2）。 */
    public static final String CONFLICT_CODE = "ECOS-ONTO-021";

    /** 推导结果。 */
    public static final class Result {
        public boolean conflicted;
        public String derivedUnit;   // 推导出的顶层单位（简单名，如 AMOUNT）；conflicted 时可能为 null
        public List<String> chain = new ArrayList<>();
        public String errorCode;     // 冲突时 = ECOS-ONTO-021，成功时 null
        public String message;

        public static Result ok(UnitDimension u, List<String> chain) {
            Result r = new Result();
            r.derivedUnit = u != null ? u.name() : null;
            r.chain = chain;
            return r;
        }

        public static Result conflict(String msg, List<String> chain) {
            Result r = new Result();
            r.conflicted = true;
            r.errorCode = CONFLICT_CODE;
            r.message = msg;
            r.chain = chain;
            return r;
        }
    }

    /** 推导表达式单位。leafUnits：表达式叶子标识符 → 单位（未登记的叶子按 {@link UnitDimension#NONE} 处理并记入链）。 */
    public Result analyze(String expression, Map<String, UnitDimension> leafUnits) {
        Tokenizer tk = new Tokenizer(expression);
        Parser p = new Parser(tk, leafUnits);
        Node root = p.parseExpr();
        String rest = p.peek().text;
        List<String> chain = new ArrayList<>();
        if (rest != null && !rest.isBlank()) {
            return Result.conflict("表达式存在无法解析的尾部: '" + rest.trim() + "'", chain);
        }
        UnitDimension u = root.unit;
        if (u == null) {
            root.describe(chain);
            return Result.conflict("表达式单位推导失败：存在单位不兼容的运算组合（比率−金额、SUM(比率) 等），请检查推导链", chain);
        }
        root.describe(chain);
        return Result.ok(u, chain);
    }

    // ── AST：带单位的节点 ────────────────────────────────
    private static final class Node {
        final UnitDimension unit;
        String text; // 非 final：func 构造器经 funcTag 二次赋值
        Node left;
        Node right;
        String op;
        Node arg; // 聚合函数的实参

        Node(UnitDimension unit, String text) { this.unit = unit; this.text = text; }
        Node(UnitDimension unit, Node left, String op, Node right) {
            this.unit = unit; this.left = left; this.op = op; this.right = right;
            this.text = left.text + " " + op + " " + right.text;
        }
        Node(UnitDimension unit, String func, Node arg) {
            this.unit = unit; this.funcTag(func); this.arg = arg;
            this.text = func + "(" + arg.text + ")";
        }

        /** 聚合函数量虽作用于集合，其单位 = 实参单位（SUM(金额)=金额）。 */
        private void funcTag(String func) { this.text = func + "("; this.funcName = func; }
        private String funcName;

        void describe(List<String> chain) {
            String u = unit != null ? unit.zh() : "<冲突/未定>";
            if (funcName != null) {
                chain.add(funcName + " 作用于 " + arg.text + " ⇒ 单位保持 " + u);
                arg.describe(chain);
            } else if (left != null) {
                chain.add("(" + text + ") ⇒ 单位 " + u);
                left.describe(chain);
                right.describe(chain);
            } else {
                chain.add("叶子/常量 " + text + " ⇒ 单位 " + u);
            }
        }
    }

    /** 二元合并：返回 Optional.empty 表示单位冲突。 */
    private static Optional<UnitDimension> combine(UnitDimension a, UnitDimension b, String op) {
        if (a == UnitDimension.NONE) {
            return Optional.of(b);        // 常量透明
        }
        if (b == UnitDimension.NONE) {
            return Optional.of(a);
        }
        if (op.equals("+") || op.equals("-")) {
            if (a == UnitDimension.AMOUNT && b == UnitDimension.AMOUNT) return Optional.of(UnitDimension.AMOUNT);
            if (a == UnitDimension.MONTH && b == UnitDimension.MONTH) return Optional.of(UnitDimension.MONTH);
            if (a == b) return Optional.of(a); // 同类同单位相加减保守放行
            return Optional.empty();
        }
        if (op.equals("/")) {
            if (a == UnitDimension.AMOUNT && b == UnitDimension.AMOUNT) return Optional.of(UnitDimension.RATIO);
            if (a == UnitDimension.AMOUNT && b == UnitDimension.HEADCOUNT) return Optional.of(UnitDimension.AMOUNT);
            return Optional.empty();
        }
        if (op.equals("*")) {
            // 文档未定义乘法白名单；保守仅放行同单位×常量已被 NONE 分支覆盖
            return Optional.empty();
        }
        return Optional.empty();
    }

    // ── 解析器（递归下降）────────────────────────────────
    private static final class Parser {
        private final Tokenizer tk;
        private final Map<String, UnitDimension> leafUnits;

        Parser(Tokenizer tk, Map<String, UnitDimension> leafUnits) { this.tk = tk; this.leafUnits = leafUnits; }

        Node parseExpr() {
            Node node = parseTerm();
            while (peekIsOp("+", "-")) {
                String op = tk.next().text;
                Node rhs = parseTerm();
                node = bin(node, op, rhs, node.unit, rhs.unit, op);
            }
            return node;
        }

        Node parseTerm() {
            Node node = parseFactor();
            while (peekIsOp("*", "/")) {
                String op = tk.next().text;
                Node rhs = parseFactor();
                node = bin(node, op, rhs, node.unit, rhs.unit, op);
            }
            return node;
        }

        Node parseFactor() {
            Tok t = tk.next();
            if (t.type == Tok.Type.LP) {
                Node inner = parseExpr();
                expect(Tok.Type.RP);
                return inner;
            }
            if (t.type == Tok.Type.IDENT) {
                Tok nxt = tk.peek();
                if (nxt.type == Tok.Type.LP) {
                    tk.next(); // consume (
                    Node arg = parseExpr();
                    expect(Tok.Type.RP);
                    // 聚合函数量单位 = 实参单位
                    return new Node(arg.unit, t.text, arg);
                }
                UnitDimension u = leafUnits != null ? leafUnits.get(t.text) : null;
                return new Node(u != null ? u : UnitDimension.NONE, t.text);
            }
            if (t.type == Tok.Type.NUMBER) {
                return new Node(UnitDimension.NONE, t.text);
            }
            throw new IllegalStateException("unexpected token " + t.type + " '" + t.text + "'");
        }

        static Node bin(Node l, String op, Node r, UnitDimension lu, UnitDimension ru, String op2) {
            Optional<UnitDimension> c = combine(lu, ru, op2);
            if (c.isPresent()) {
                return new Node(c.get(), l, op2, r);
            }
            // 冲突：单位仍记 NONE，交由上层 detect（此处记一个冲突标记节点，unit=null）
            return new Node(null, l, op2, r);
        }

        private boolean peekIsOp(String opA, String opB) {
            Tok t = tk.peek();
            return t.type == Tok.Type.OP && (t.text.equals(opA) || t.text.equals(opB));
        }

        private void expect(Tok.Type want) {
            Tok t = tk.next();
            if (t.type != want) throw new IllegalStateException("expected " + want + " got " + t.type);
        }

        private Tok peek() { return tk.peek(); }
    }

    // ── 分词器 ───────────────────────────────────────────
    private static final class Tokenizer {
        private final String src;
        private final List<Tok> tokens = new ArrayList<>();
        private int idx;

        Tokenizer(String src) {
            this.src = src == null ? "" : src;
            tokenize();
        }

        private void tokenize() {
            int i = 0, n = src.length();
            while (i < n) {
                char c = src.charAt(i);
                if (Character.isWhitespace(c)) { i++; continue; }
                if (c == '(' || c == ')') { tokens.add(new Tok(c == '(' ? Tok.Type.LP : Tok.Type.RP, String.valueOf(c))); i++; continue; }
                if (c == '+' || c == '-' || c == '*' || c == '/') {
                    tokens.add(new Tok(Tok.Type.OP, String.valueOf(c))); i++; continue;
                }
                if (Character.isDigit(c) || c == '.') {
                    int j = i;
                    while (j < n && (Character.isDigit(src.charAt(j)) || src.charAt(j) == '.')) j++;
                    tokens.add(new Tok(Tok.Type.NUMBER, src.substring(i, j))); i = j; continue;
                }
                if (Character.isLetter(c) || c == '_') {
                    int j = i;
                    while (j < n && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '_')) j++;
                    tokens.add(new Tok(Tok.Type.IDENT, src.substring(i, j))); i = j; continue;
                }
                // 未知字符：作为 OP 单 token 抛出（parser 侧会报）
                tokens.add(new Tok(Tok.Type.OP, String.valueOf(c))); i++;
            }
        }

        Tok next() {
            if (idx >= tokens.size()) return new Tok(Tok.Type.EOF, "");
            return tokens.get(idx++);
        }

        Tok peek() {
            if (idx >= tokens.size()) return new Tok(Tok.Type.EOF, "");
            return tokens.get(idx);
        }
    }

    private static final class Tok {
        enum Type { LP, RP, OP, IDENT, NUMBER, EOF }
        final Type type;
        final String text;

        Tok(Type type, String text) { this.type = type; this.text = text; }
    }
}
