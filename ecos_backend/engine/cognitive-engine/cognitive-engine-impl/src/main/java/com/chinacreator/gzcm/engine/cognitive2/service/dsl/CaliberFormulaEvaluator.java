package com.chinacreator.gzcm.engine.cognitive2.service.dsl;

import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.BinaryNode;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.FuncNode;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.Node;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.NumberNode;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.QualifiedRefNode;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.RefNode;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaAst.Token;
import com.chinacreator.gzcm.engine.cognitive2.service.dsl.CaliberFormulaEvaluationException.Reason;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 口径公式 DSL 求值器（H-2b，详设-09 v1.2 §十一 H-2 认知侧半区）。
 *
 * <p><b>四条硬约束</b>（PRD-09 v1.2 §1.2 + 卷 09 §十一 H-2「禁脚本引擎」）：</p>
 * <ol>
 *   <li><b>无脚本引擎</b>：只对 {@link CaliberFormulaParser} 产出的自有 AST 做递归下降求值，
 *       不使用 SpEL / Groovy / Nashorn / 反射方法调用（认知 pom 亦无规则引擎依赖，
 *       由 {@code CognitiveDependencyAuditTest} 守门）；</li>
 *   <li><b>全 BigDecimal</b>：字面量直接 {@code new BigDecimal(String)}，加减乘精确、
 *       除法显式 {@code scale + RoundingMode.HALF_UP}（{@link Policy#divisionScale()} 默认 10），
 *       无任何 {@code double} 参与金额/比例中间计算（CM07 数值纪律）；</li>
 *   <li><b>fail-loud</b>：未知标识符 / 维度缺失 / 除零 / 量纲冲突 →
 *       {@link CaliberFormulaEvaluationException}（{@code ECOS-COG-422F} → 真实 422），
 *       <b>禁返回伪造数值</b>（X-44：禁 200 + 假数据）；</li>
 *   <li><b>文法同源</b>：函数白名单与元数（{@link CaliberFormulaAst#FUNC_ARITY}）、
 *       单位代数（{@code combineUnit}）与 ontology 侧 H-2 交付逐条同源；未知单位按对侧同哲学
 *       宽松放行，不做二次发明。</li>
 * </ol>
 *
 * <p><b>求值前置</b>：本类<b>不</b>替代 V2 引用闭合 / V3 量纲 / V4 无环四关校验（属主
 * {@code CaliberDslValidator}，ontology-engine）。{@code UnitSupply} 只是运行期的二次防御：
 * 静态面漏过的金额+比率直加一类，在取数前仍会被拒。</p>
 */
public final class CaliberFormulaEvaluator {

    /** 嵌套深度上限（防御注册表脏数据导致的深链，越界按数据缺陷报错而非 StackOverflowError）。 */
    static final int MAX_DEPTH = 64;

    private CaliberFormulaEvaluator() {
    }

    // ────────────────────────────────────────────────────────────
    // 供给面（取值 / 取单位）
    // ────────────────────────────────────────────────────────────

    /**
     * 事实与指标取值供给（由调用方从预测运行快照 / 口径登记装配；本类不访问 DB）。
     * 区分两种"取不到"：符号本身不存在（{@code UNKNOWN_SYMBOL}）与
     * 符号存在但请求维度无数据（{@code MISSING_DIMENSION}）—— 前者是公式缺陷、后者是数据缺陷。
     */
    public interface ValueSupply {

        /** 符号是否为已登记的指标码 / 事实字段。 */
        boolean knownSymbol(String symbol);

        /**
         * 取值。
         *
         * @param symbol    指标码 / 事实字段名
         * @param qualifier {@code IDENT[QUAL]} 的维度限定；裸引用传 {@code null}
         * @return 空 = 该符号（在该维度下）无数据，由求值器按缺失语义拒绝，禁补 0
         */
        Optional<BigDecimal> value(String symbol, String qualifier);
    }

    /** 单位供给（V3 单位体系的运行期视图）。未登记返回空 = 宽松放行，与 ontology 侧同哲学。 */
    public interface UnitSupply {

        /** 无任何单位元数据（只求值、不做量纲二次防御）。 */
        UnitSupply NONE = symbol -> Optional.empty();

        /** 内置事实字段单位表（与 ontology {@code CaliberFactFieldRegistry} 同源复制）。 */
        UnitSupply FACT_FIELDS = CaliberFactUnits::unitOf;

        Optional<String> unitOf(String symbol);
    }

    /** 求值策略（除法精度 / ROUND 位数上限）。 */
    public record Policy(int divisionScale, int maxRoundScale) {

        public static final Policy DEFAULT = new Policy(10, 12);

        public Policy {
            if (divisionScale < 0 || divisionScale > 34) {
                throw new IllegalArgumentException("divisionScale 须落在 0..34（BigDecimal 可行域）");
            }
            if (maxRoundScale < 0 || maxRoundScale > 34) {
                throw new IllegalArgumentException("maxRoundScale 须落在 0..34");
            }
        }
    }

    /** 内存版取值供给（单测与快照适配器共用）：键 {@code symbol} 或 {@code symbol[QUAL]}。 */
    public static final class MapValueSupply implements ValueSupply {

        private final Map<String, BigDecimal> bare = new LinkedHashMap<>();
        private final Map<String, BigDecimal> qualified = new LinkedHashMap<>();

        public MapValueSupply put(String symbol, BigDecimal value) {
            bare.put(symbol, value);
            return this;
        }

        /** 挂维度限定的取值（对应 {@code IDENT[QUAL]} 形态）；同时把符号登记为已知。 */
        public MapValueSupply put(String symbol, String qualifier, BigDecimal value) {
            bare.putIfAbsent(symbol, null);
            qualified.put(key(symbol, qualifier), value);
            return this;
        }

        private static String key(String symbol, String qualifier) {
            return symbol + "[" + qualifier.toUpperCase() + "]";
        }

        @Override
        public boolean knownSymbol(String symbol) {
            return bare.containsKey(symbol);
        }

        @Override
        public Optional<BigDecimal> value(String symbol, String qualifier) {
            if (qualifier == null) {
                return Optional.ofNullable(bare.get(symbol));
            }
            return Optional.ofNullable(qualified.get(key(symbol, qualifier)));
        }
    }

    /**
     * 事实字段单位登记表（H-2b 运行期二次防御用）。
     *
     * <p>逐条复制自 ontology 侧 {@code CaliberFactFieldRegistry}（H-2 V3 交付，包内可见类、
     * 跨引擎不可 import），单位体系同源：AMOUNT / RATE / MONTH / PERSON_COUNT / HOURS / SCALAR。</p>
     */
    public static final class CaliberFactUnits {

        private static final Map<String, String> FIELD_UNITS = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        static {
            FIELD_UNITS.put("contract_base", "AMOUNT");
            FIELD_UNITS.put("amount", "AMOUNT");
            FIELD_UNITS.put("cost_amount", "AMOUNT");
            FIELD_UNITS.put("attribution_ratio", "RATE");
            FIELD_UNITS.put("realization_rate", "RATE");
            FIELD_UNITS.put("collection_rate", "RATE");
            FIELD_UNITS.put("hourly_rate", "AMOUNT");
            FIELD_UNITS.put("fte", "PERSON_COUNT");
            FIELD_UNITS.put("head_count", "PERSON_COUNT");
            FIELD_UNITS.put("work_hours", "HOURS");
            FIELD_UNITS.put("months", "MONTH");
        }

        private CaliberFactUnits() {
        }

        public static Optional<String> unitOf(String symbol) {
            return Optional.ofNullable(symbol == null ? null : FIELD_UNITS.get(symbol));
        }
    }

    // ────────────────────────────────────────────────────────────
    // 入口
    // ────────────────────────────────────────────────────────────

    /** 解析 + 求值一步到位（默认策略、内置事实单位表）。 */
    public static BigDecimal evaluate(String formulaText, ValueSupply values) {
        return evaluate(CaliberFormulaParser.parse(formulaText), values, UnitSupply.FACT_FIELDS, Policy.DEFAULT);
    }

    /** 对已解析 AST 求值（默认策略、内置事实单位表）。 */
    public static BigDecimal evaluate(CaliberFormulaAst.FormulaAst ast, ValueSupply values) {
        return evaluate(ast, values, UnitSupply.FACT_FIELDS, Policy.DEFAULT);
    }

    /** 对已解析 AST 求值（全量策略）。 */
    public static BigDecimal evaluate(CaliberFormulaAst.FormulaAst ast, ValueSupply values,
                                      UnitSupply units, Policy policy) {
        if (ast == null) {
            throw new IllegalArgumentException("formulaAst 不可为 null（H-2b 求值入口）");
        }
        return eval(ast.expr, values, units, policy, 0);
    }

    /** 求单个表达式节点（{@code formula := IDENT '=' expr} 的右半区，公开供快照适配器复用）。 */
    public static BigDecimal evaluateExpression(Node expr, ValueSupply values, UnitSupply units, Policy policy) {
        return eval(expr, values, units, policy, 0);
    }

    // ────────────────────────────────────────────────────────────
    // 递归求值
    // ────────────────────────────────────────────────────────────

    private static BigDecimal eval(Node node, ValueSupply values, UnitSupply units, Policy policy, int depth) {
        if (depth > MAX_DEPTH) {
            throw fail(Reason.NESTING_TOO_DEEP,
                    "DSL_EVAL depth: formula nesting exceeds " + MAX_DEPTH + " — " + node.token.location(),
                    node.token);
        }
        if (node instanceof NumberNode n) {
            return new BigDecimal(n.value);
        }
        if (node instanceof RefNode r) {
            return requireValue(r.symbol(), null, r.name, values);
        }
        if (node instanceof QualifiedRefNode q) {
            return requireValue(q.symbol(), q.dimension(), q.qualifier, values);
        }
        if (node instanceof BinaryNode b) {
            return evalBinary(b, values, units, policy, depth);
        }
        if (node instanceof FuncNode f) {
            return evalFunc(f, values, units, policy, depth);
        }
        throw fail(Reason.UNKNOWN_SYMBOL,
                "DSL_EVAL node: unsupported AST node " + node.token.location(), node.token);
    }

    private static BigDecimal evalBinary(BinaryNode b, ValueSupply values, UnitSupply units,
                                         Policy policy, int depth) {
        String op = b.operator();
        if ("+".equals(op) || "-".equals(op)) {
            BigDecimal l = eval(b.left, values, units, policy, depth + 1);
            BigDecimal r = eval(b.right, values, units, policy, depth + 1);
            requireAdditive(b, units, op);
            return "+".equals(op) ? l.add(r) : l.subtract(r);
        }
        if ("*".equals(op)) {
            return eval(b.left, values, units, policy, depth + 1)
                    .multiply(eval(b.right, values, units, policy, depth + 1));
        }
        if ("/".equals(op)) {
            BigDecimal l = eval(b.left, values, units, policy, depth + 1);
            BigDecimal r = eval(b.right, values, units, policy, depth + 1);
            if (r.compareTo(BigDecimal.ZERO) == 0) {
                throw fail(Reason.DIVISION_BY_ZERO,
                        "DSL_EVAL divide: divisor evaluates to 0 at " + b.op.location()
                                + " (禁以 0 或均值兜底)", b.op);
            }
            return l.divide(r, policy.divisionScale(), RoundingMode.HALF_UP);
        }
        throw fail(Reason.NON_WHITELIST_FUNCTION,
                "DSL_EVAL operator: '" + op + "' is not an arithmetic operator — " + b.op.location(), b.op);
    }

    private static BigDecimal evalFunc(FuncNode f, ValueSupply values, UnitSupply units,
                                       Policy policy, int depth) {
        String name = f.function();
        if (!CaliberFormulaAst.FUNC_ARITY.containsKey(name)) {
            throw fail(Reason.NON_WHITELIST_FUNCTION,
                    "DSL_EVAL function: '" + name + "' is outside the whitelist "
                            + CaliberFormulaAst.FUNC_ARITY.keySet() + " — " + f.name.location(), f.name);
        }
        switch (name) {
            // SUM 元数在 V1 文法锁死为 1（聚合发生在事实侧），求值即恒等取该聚合量
            case "SUM" -> {
                return eval(f.args.get(0), values, units, policy, depth + 1);
            }
            case "ROUND" -> {
                BigDecimal v = eval(f.args.get(0), values, units, policy, depth + 1);
                int scale;
                if (f.args.size() == 2) {
                    BigDecimal raw = eval(f.args.get(1), values, units, policy, depth + 1);
                    try {
                        scale = raw.stripTrailingZeros().intValueExact();
                    } catch (ArithmeticException | NumberFormatException e) {
                        throw fail(Reason.ROUND_SCALE_INVALID,
                                "DSL_EVAL round: scale must be an integer, got '" + raw.toPlainString()
                                        + "' — " + f.name.location(), f.name);
                    }
                } else {
                    scale = 0;
                }
                if (scale < 0 || scale > policy.maxRoundScale()) {
                    throw fail(Reason.ROUND_SCALE_INVALID,
                            "DSL_EVAL round: scale " + scale + " outside 0.." + policy.maxRoundScale()
                                    + " — " + f.name.location(), f.name);
                }
                return v.setScale(scale, RoundingMode.HALF_UP);
            }
            case "MIN" -> {
                BigDecimal a = eval(f.args.get(0), values, units, policy, depth + 1);
                BigDecimal b = eval(f.args.get(1), values, units, policy, depth + 1);
                requireSameUnit(f, units, f.args.get(0), f.args.get(1));
                return a.min(b);
            }
            case "MAX" -> {
                BigDecimal a = eval(f.args.get(0), values, units, policy, depth + 1);
                BigDecimal b = eval(f.args.get(1), values, units, policy, depth + 1);
                requireSameUnit(f, units, f.args.get(0), f.args.get(1));
                return a.max(b);
            }
            case "IF" -> {
                // 三目：条件非零即为真；只求精选中的分支（未选分支的取数缺陷不该污染本次结果）
                BigDecimal condition = eval(f.args.get(0), values, units, policy, depth + 1);
                Node branch = condition.compareTo(BigDecimal.ZERO) != 0 ? f.args.get(1) : f.args.get(2);
                BigDecimal taken = eval(branch, values, units, policy, depth + 1);
                requireSameUnit(f, units, f.args.get(1), f.args.get(2));
                return taken;
            }
            default -> throw fail(Reason.NON_WHITELIST_FUNCTION,
                    "DSL_EVAL function: '" + name + "' unreachable — " + f.name.location(), f.name);
        }
    }

    // ────────────────────────────────────────────────────────────
    // 取数与量纲
    // ────────────────────────────────────────────────────────────

    private static BigDecimal requireValue(String symbol, String qualifier,
                                           Token anchor, ValueSupply values) {
        if (!values.knownSymbol(symbol)) {
            throw fail(Reason.UNKNOWN_SYMBOL,
                    "DSL_EVAL reference: symbol '" + symbol + "' does not resolve to any metric / fact field"
                            + " — " + anchor.location()
                            + " (禁以 0 替真值，X-44)", anchor);
        }
        Optional<BigDecimal> value = values.value(symbol, qualifier);
        if (value.isEmpty()) {
            throw fail(Reason.MISSING_DIMENSION,
                    "DSL_EVAL dimension: no value for '" + symbol + "'"
                            + (qualifier == null ? "" : " under dimension '" + qualifier + "'")
                            + " — " + anchor.location() + " (缺数即失败，禁静默取 0 或均值)", anchor);
        }
        return value.get();
    }

    /** 加减法可加性（与 ontology V3 {@code combineBinary} 同判据：单位不等且非 SCALAR 即拒）。 */
    private static void requireAdditive(BinaryNode b, UnitSupply units, String op) {
        String lu = unitOfTree(b.left, units);
        String ru = unitOfTree(b.right, units);
        if (lu == null || ru == null || lu.equals(ru) || "SCALAR".equals(lu) || "SCALAR".equals(ru)) {
            return;
        }
        throw fail(Reason.UNIT_CONFLICT,
                "DSL_EVAL dimension: " + lu + " " + op + " " + ru + " is not derivable"
                        + " (amount+rate direct addition forbidden) — operator " + b.op.location(), b.op);
    }

    /** MIN/MAX/IF 分支同量纲（与 ontology V3 {@code combineFunc} 同判据）。 */
    private static void requireSameUnit(FuncNode f, UnitSupply units, Node left, Node right) {
        String lu = unitOfTree(left, units);
        String ru = unitOfTree(right, units);
        if (lu == null || ru == null || lu.equals(ru) || "SCALAR".equals(lu) || "SCALAR".equals(ru)) {
            return;
        }
        throw fail(Reason.UNIT_CONFLICT,
                "DSL_EVAL dimension: " + lu + " " + f.function() + " " + ru
                        + " is not derivable — function " + f.name.location(), f.name);
    }

    /**
     * 表达式单位推导（叶子查表 + 与 ontology V3 同源的单位代数；未登记 ⇒ null ⇒ 宽松）。
     * 纯数字按 SCALAR（无量纲常数）。
     */
    private static String unitOfTree(Node node, UnitSupply units) {
        if (node instanceof NumberNode) {
            return "SCALAR";
        }
        if (node instanceof RefNode r) {
            return units.unitOf(r.symbol()).orElse(null);
        }
        if (node instanceof QualifiedRefNode q) {
            return units.unitOf(q.symbol()).orElse(null); // 限定符不改变单位（同 ontology V3）
        }
        if (node instanceof BinaryNode b) {
            String lu = unitOfTree(b.left, units);
            String ru = unitOfTree(b.right, units);
            return combineUnit(b.operator(), lu, ru);
        }
        if (node instanceof FuncNode f) {
            return switch (f.function()) {
                case "SUM", "ROUND" -> f.args.isEmpty() ? null : unitOfTree(f.args.get(0), units);
                case "MIN", "MAX" -> f.args.size() < 2 ? null
                        : sameOrNull(unitOfTree(f.args.get(0), units), unitOfTree(f.args.get(1), units));
                case "IF" -> f.args.size() < 3 ? null
                        : sameOrNull(unitOfTree(f.args.get(1), units), unitOfTree(f.args.get(2), units));
                default -> null;
            };
        }
        return null;
    }

    private static String combineUnit(String op, String lu, String ru) {
        if (lu == null || ru == null) {
            return null;
        }
        return switch (op) {
            case "+", "-" -> "SCALAR".equals(lu) ? ru : lu;
            case "*" -> {
                if ("SCALAR".equals(lu)) yield ru;
                if ("SCALAR".equals(ru)) yield lu;
                // PRD-09 §1.2 V3：金额 × 比率 = 金额
                if (isAmount(lu) && isRate(ru)) yield "AMOUNT";
                if (isRate(lu) && isAmount(ru)) yield "AMOUNT";
                yield lu.equals(ru) ? lu : null;
            }
            case "/" -> {
                if ("SCALAR".equals(ru)) yield lu;
                if (lu.equals(ru)) yield "SCALAR".equals(lu) ? "SCALAR" : "RATE";
                if (isAmount(lu) && "PERSON_COUNT".equals(ru)) yield "AMOUNT";
                yield null;
            }
            default -> null;
        };
    }

    private static String sameOrNull(String a, String b) {
        return a == null || b == null || a.equals(b) ? (a == null ? b : a) : null;
    }

    private static boolean isAmount(String u) {
        return "AMOUNT".equals(u);
    }

    private static boolean isRate(String u) {
        return "RATE".equals(u);
    }

    private static CaliberFormulaEvaluationException fail(Reason reason, String message, Token token) {
        return new CaliberFormulaEvaluationException(reason, message,
                token == null ? null : token.text,
                token == null ? null : token.line,
                token == null ? null : token.column,
                token == null ? null : token.offset);
    }
}
