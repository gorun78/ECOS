package com.chinacreator.gzcm.engine.cognitive2.fc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 分册09 F09-02 · Caliber DSL 受限解析与四关静态校验。
 *
 * <p>白名单函数（禁脚本引擎，铁律 :14 + 分册09 §七 W216/C198）：
 * SUM / ROUND / MIN / MAX / IF + 变量引用（小写字母开头 + 下划线/数字）。
 * 四关：
 * <ol>
 *   <li>V1 语法 — 括号匹配 / 白名单函数 / 变量形态</li>
 *   <li>V2 引用闭合 — 每个变量必须在 {@code knownSymbols} 集合中</li>
 *   <li>V3 量纲 — 每个引用的变量既定 unit ≠ 目标 unit 时拒（简单等值校验，非 AST 推导）</li>
 *   <li>V4 无环 — 表达式不允许出现专门标记的自引用（DSL 约定 "SELF(" 视为自引用）</li>
 * </ol>
 *
 * <p>校验结果以 {@link ValidationResult} 集合返回，一个表达式可多个违规并报符号定位。
 * 求值入口 {@link #evaluate(String, Map)} 一律出参 {@link BigDecimal} HALF_UP 2 位，
 * 未知函数/变量直接抛 {@link IllegalArgumentException}，由上游 400。</p>
 */
public final class CaliberDsl {

    private static final Set<String> FUNCTIONS = Set.of("SUM", "ROUND", "MIN", "MAX", "IF");
    private static final Pattern VAR_PATTERN = Pattern.compile("^[a-z_][a-z0-9_]*$");

    /** 校验结果；违规项列表包含符号定位摘要，供口径编辑页逐条渲染。 */
    public record ValidationResult(boolean passed, List<String> violations) {
        public static ValidationResult ok() { return new ValidationResult(true, List.of()); }
        public static ValidationResult bad(List<String> vs) {
            return new ValidationResult(vs.isEmpty(), new ArrayList<>(vs));
        }
    }

    /**
     * 四关总入口。任一关用于独立初筛（顺序短路：V1 失败不再进入 V2/V3/V4，避免噪音）。
     *
     * @param expr           表达式文本（白名单语法）
     * @param knownSymbols   V2 引用闭合的符号集合；null 视为"语法层不校验 V2"（保持向后兼容）
     * @param symbolUnits    符号 → 量纲单位字符串；null 视为"语法层不校验 V3"
     * @param expectedUnit   目标量纲单位；null 或空串 视为"语法层不校验 V3"
     */
    public static ValidationResult validate(String expr, Set<String> knownSymbols,
                                            Map<String, String> symbolUnits, String expectedUnit) {
        List<String> vs = new ArrayList<>();
        if (expr == null || expr.isBlank()) {
            vs.add("V1 empty expression");
            return ValidationResult.bad(vs);
        }
        // V1 语法（含变量/函数形态）
        List<Ref> refs = checkSyntax(expr, vs);
        if (!vs.isEmpty()) return ValidationResult.bad(vs);
        // V2 引用闭合
        if (knownSymbols != null) {
            Set<String> seen = new LinkedHashSet<>();
            for (Ref r : refs) {
                if (seen.add(r.name()) && !knownSymbols.contains(r.name())) {
                    vs.add("V2 unknown symbol: " + r.name());
                }
            }
        }
        if (!vs.isEmpty()) return ValidationResult.bad(vs);
        // V3 量纲
        if (symbolUnits != null && expectedUnit != null && !expectedUnit.isBlank()) {
            Set<String> seen = new LinkedHashSet<>();
            for (Ref r : refs) {
                if (!seen.add(r.name())) continue;
                String u = symbolUnits.get(r.name());
                if (u != null && !u.equals(expectedUnit)) {
                    vs.add("V3 dimension mismatch[" + r.name() + "] " + u + " vs " + expectedUnit);
                }
            }
        }
        if (!vs.isEmpty()) return ValidationResult.bad(vs);
        // V4 无环
        // 约定：DSL 不支持变量自引用；若表达式中出现显式自引用占位符 "SELF(" 判定为 V4 违规
        if (expr.contains("SELF(")) {
            vs.add("V4 self-reference detected (SELF() marker present)");
        }
        return ValidationResult.bad(vs);
    }

    /** 语法关：括号匹配 + 白名单函数 + 变量形态。返回遇到的变量引用列表（含函数名？— 否，仅变量）。
     *  SELF 是 V4 自引用占位符约定，不在白名单函数面，也不属于变量形态；应在 V1 阶段直接识别
     *  （避免被当作"unknown function"误拦），并让计数下留给 V4 判定。 */
    private static List<Ref> checkSyntax(String expr, List<String> vs) {
        List<Ref> refs = new ArrayList<>();
        int depth = 0;
        StringBuilder id = new StringBuilder();
        for (int i = 0; i <= expr.length(); i++) {
            char ch = i < expr.length() ? expr.charAt(i) : ' ';
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                id.append(ch);
                continue;
            }
            String token = id.toString();
            id.setLength(0);
            if (!token.isEmpty()) {
                boolean atParen = (ch == '(');
                String up = token.toUpperCase(Locale.ROOT);
                if (atParen) {
                    boolean isWhitelistFn = FUNCTIONS.contains(up);
                    boolean isSelfMarker = "SELF".equals(up);
                    if (!isWhitelistFn && !isSelfMarker) {
                        vs.add("V1 unknown function: " + token + " (whitelist " + FUNCTIONS + ")");
                    }
                } else {
                    if (token.equals("SELF")) {
                        refs.add(new Ref(token));
                    } else if (token.matches("-?\\d+(\\.\\d+)?")) {
                        // DSL 子集：数字字面量按字符串记为合成符号 __lit，让求值路径恰当处理
                    } else if (!VAR_PATTERN.matcher(token).matches()) {
                        vs.add("V1 illegal symbol token: '" + token + "' at pos " + (i - token.length()));
                    } else {
                        refs.add(new Ref(token));
                    }
                }
            }
            // 括号计数无条件执行：无论当前 token 是变量、函数名还是 SELF 标记，只要 ch 是 ( / ) 就计
            if (ch == '(') depth++;
            else if (ch == ')') {
                depth--;
                if (depth < 0) {
                    vs.add("V1 unbalanced ')' at pos " + i);
                    return refs;
                }
            }
        }
        if (depth != 0) vs.add("V1 unbalanced parentheses (depth=" + depth + ")");
        return refs;
    }

    /**
     * 求值：语法树 → BigDecimal(scale=2, HALF_UP)。
     * <p><b>DSL 子集说明</b>：单独一个变量引用、单独一个白名单函数调用、或变量间嵌套调用。
     * 顶层四则（- / *）不进入 DSL 白名单——口径字段只承载聚合语义，加减乘除由生产服务在
     * 自下而上聚合阶段完成（分册09 §3.1 逐格取值 + 严格自下而上聚合），DSL 本身不承担算术展开，
     * 与 F09-07「workspace 不写金额计算代码 + cognitive 做确定性聚合」的边界一致。</p>
     */
    public static BigDecimal evaluate(String expr, Map<String, BigDecimal> vars) {
        Parser p = new Parser(expr);
        Object root = p.parseUnary();
        p.skipWs();
        if (p.peek() != '\u0000') {
            throw new IllegalArgumentException("trailing tokens: '" + p.rest() + "'");
        }
        BigDecimal r = toBd(root, vars);
        return r == null ? null : r.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal toBd(Object node, Map<String, BigDecimal> vars) {
        if (node instanceof BigDecimal bd) return bd;
        if (node instanceof String name) {
            if (vars == null || !vars.containsKey(name)) {
                throw new IllegalArgumentException("unresolved symbol: " + name);
            }
            return vars.get(name);
        }
        if (node instanceof Call c) {
            List<Object> args = c.args();
            switch (c.fn()) {
                case "SUM" -> {
                    BigDecimal s = BigDecimal.ZERO;
                    for (Object a : args) s = s.add(toBd(a, vars));
                    return s;
                }
                case "ROUND" -> {
                    BigDecimal v = toBd(args.get(0), vars);
                    int scale = args.size() > 1
                        ? toBd(args.get(1), vars).intValue()
                        : 2;
                    return v.setScale(scale, RoundingMode.HALF_UP);
                }
                case "MIN" -> {
                    BigDecimal m = toBd(args.get(0), vars);
                    for (int i = 1; i < args.size(); i++) m = m.min(toBd(args.get(i), vars));
                    return m;
                }
                case "MAX" -> {
                    BigDecimal m = toBd(args.get(0), vars);
                    for (int i = 1; i < args.size(); i++) m = m.max(toBd(args.get(i), vars));
                    return m;
                }
                case "IF" -> {
                    boolean cond = toBd(args.get(0), vars).signum() != 0;
                    return toBd(cond ? args.get(1) : args.get(2), vars);
                }
                default -> throw new IllegalArgumentException("unknown fn " + c.fn());
            }
        }
        throw new IllegalArgumentException("unsupported node: " + (node == null ? "null" : node.getClass().getName()));
    }

    /** 节点：函数调用 */
    public record Call(String fn, List<Object> args) {}

    /** 节点：变量引用 */
    public record Ref(String name) {}

    /** 极简递归下降求值器（DSL 白名单：函数/变量/数字/括号）。 */
    public static final class Parser {
        private final String s;
        private int i;
        public Parser(String s) { this.s = s; this.i = 0; }

        public Object parseUnary() {
            skipWs();
            if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
            char c = s.charAt(i);
            if (c == '(') {
                i++;
                Object e = parseUnary();
                skipWs();
                if (i >= s.length() || s.charAt(i) != ')') throw new IllegalArgumentException("missing ')'");
                i++;
                return e;
            }
            StringBuilder tok = new StringBuilder();
            while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.' ||
                    (s.charAt(i) == '-' && tok.length() == 0))) {
                tok.append(s.charAt(i));
                i++;
            }
            if (tok.length() == 0) throw new IllegalArgumentException("bad token around pos " + i);
            String t = tok.toString();
            skipWs();
            if (i < s.length() && s.charAt(i) == '(') {
                String upper = t.toUpperCase(Locale.ROOT);
                if (!FUNCTIONS.contains(upper)) throw new IllegalArgumentException("unknown function " + t);
                i++;
                List<Object> args = new ArrayList<>();
                skipWs();
                if (i < s.length() && s.charAt(i) == ')') {
                    i++;
                    return new Call(upper, List.of());
                }
                while (true) {
                    args.add(parseUnary());
                    skipWs();
                    if (i < s.length() && s.charAt(i) == ',') { i++; continue; }
                    if (i < s.length() && s.charAt(i) == ')') { i++; break; }
                    throw new IllegalArgumentException("malformed fn args: " + rest());
                }
                return new Call(upper, args);
            }
            if (t.matches("-?\\d+(\\.\\d+)?")) {
                return new BigDecimal(t);
            }
            return t;
        }

        public char peek() {
            skipWs();
            return i < s.length() ? s.charAt(i) : '\u0000';
        }
        public int pos() { return i; }
        public String rest() { return i < s.length() ? s.substring(i) : ""; }
        private void skipWs() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    }

    private CaliberDsl() {}
}
