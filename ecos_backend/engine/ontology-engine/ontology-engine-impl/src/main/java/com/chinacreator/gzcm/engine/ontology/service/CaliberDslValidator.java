package com.chinacreator.gzcm.engine.ontology.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.chinacreator.gzcm.engine.ontology.dto.CaliberFormulaValidateDTO;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberFormulaValidationVO;
import com.chinacreator.gzcm.engine.ontology.service.CaliberDslParser.BinaryNode;
import com.chinacreator.gzcm.engine.ontology.service.CaliberDslParser.FormulaAst;
import com.chinacreator.gzcm.engine.ontology.service.CaliberDslParser.FuncNode;
import com.chinacreator.gzcm.engine.ontology.service.CaliberDslParser.Node;
import com.chinacreator.gzcm.engine.ontology.service.CaliberDslParser.NumberNode;
import com.chinacreator.gzcm.engine.ontology.service.CaliberDslParser.QualifiedRefNode;
import com.chinacreator.gzcm.engine.ontology.service.CaliberDslParser.RefNode;
import com.chinacreator.gzcm.engine.ontology.service.CaliberDslParser.SyntaxError;
import com.chinacreator.gzcm.engine.ontology.service.CaliberDslParser.Token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * H-2（详设-09 v1.2 §十一 / PRD-09 v1.2 §1.2）— 口径公式 DSL <b>四关校验器</b>。
 *
 * <p>四关（违一即 400，错误信息定位到符号 {@code line/column/offset}）：</p>
 * <ol>
 *   <li><b>V1 语法</b>：委托 {@link CaliberDslParser}（受限递归下降，EBNF 见其类注释；
 *       函数白名单仅 SUM/ROUND/MIN/MAX/IF，<b>无任何脚本引擎/SpEL/Groovy</b>，
 *       求值不属本册职责 — H-2 边界，求值器由 cognitive-engine 按同源文法实现）；</li>
 *   <li><b>V2 引用闭合</b>：IDENT / IDENT[QUAL] 必须解析到
 *       ①指标注册表 {@code ecos_metric_definition}（R-4 唯一语义入口，经 {@link MetricSymbols}）
 *       ②业务事实字段（{@link CaliberFactFieldRegistry} + 请求声明）
 *       ③请求声明单位符号；不可解析 → {@code ECOS-ONTO-071} 并指出符号与位置；</li>
 *   <li><b>V3 量纲一致</b>：在 V1 AST 上做单位推导，<b>复用</b>
 *       {@link MetricUnitDerivationGuard}.UNIT_REGISTRY 单位元数据并扩展
 *       （金额×比率=金额；禁金额+比率直加 → {@code ECOS-ONTO-072}）；未知单位按既有
 *       守卫哲学放行 + WARN；</li>
 *   <li><b>V4 无循环依赖</b>：公式图（目标指标 + 指标注册表表达式 + 请求关联公式）DFS 判环
 *       （拓扑排序不可行 ⇔ 有环），成环 → {@code ECOS-ONTO-073} 打印环路径。</li>
 * </ol>
 *
 * <p><b>与既有门禁共存</b>：本校验器是 <b>caliber 公式面</b>（validate 端点）的新门禁，
 * 不替换发布门禁 {@link PublishGateService}（其 V1_CALIBER/V2_UNIT/V3_LINEAGE 为
 * 指标发布面语义，编号体系不同；此处 V1~V4 按 PRD-09 §1.2 命名 DSL_V1_SYNTAX…DSL_V4_CYCLE）。</p>
 */
public class CaliberDslValidator {

    private static final Logger log = LoggerFactory.getLogger(CaliberDslValidator.class);

    // ── 四关门禁标签（PRD-09 §1.2 V1~V4；与发布门禁 V1_CALIBER/V2_UNIT/V3_LINEAGE 区分）──
    public static final String GATE_V1_SYNTAX = "DSL_V1_SYNTAX";
    public static final String GATE_V2_REFERENCE = "DSL_V2_REFERENCE";
    public static final String GATE_V3_DIMENSION = "DSL_V3_DIMENSION";
    public static final String GATE_V4_CYCLE = "DSL_V4_CYCLE";

    // ── 错误码（ECOS-ONTO-07x 号段，避开 012/021/022/030/040~042/050/060/061）──
    public static final String CODE_SYNTAX = "ECOS-ONTO-070";
    public static final String CODE_UNRESOLVED_REFERENCE = "ECOS-ONTO-071";
    public static final String CODE_DIMENSION_CONFLICT = "ECOS-ONTO-072";
    public static final String CODE_CYCLIC_DEPENDENCY = "ECOS-ONTO-073";

    /** V3 单位推导递归深度上限（防御注册表脏数据导致的深链）。 */
    private static final int UNIT_DERIVE_MAX_DEPTH = 6;

    /**
     * 指标注册表符号供给（V2/V3/V4 的语义面）：code → 公式表达式（可空）。
     * 生产装配由 {@link CaliberFormulaDslService} 从 {@code ecos_metric_definition} 加载；
     * 单测直接构造内存 Map。
     */
    public static final class MetricSymbols {
        private final Map<String, String> codeToExpression;

        public MetricSymbols(Map<String, String> codeToExpression) {
            // 注意：注册表 expression 允许为 null（指标存在但未带公式），故不能用 Map.copyOf/Map.of
            this.codeToExpression = codeToExpression == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(codeToExpression));
        }

        public static MetricSymbols empty() {
            return new MetricSymbols(Map.of());
        }

        public boolean knownMetric(String code) {
            return code != null && codeToExpression.containsKey(code);
        }

        public String expressionOf(String code) {
            return codeToExpression.get(code);
        }

        public int size() {
            return codeToExpression.size();
        }
    }

    // ────────────────────────────────────────────────────────────
    // 主入口
    // ────────────────────────────────────────────────────────────

    public CaliberFormulaValidationVO validate(CaliberFormulaValidateDTO dto, MetricSymbols symbols) {
        CaliberFormulaValidationVO vo = new CaliberFormulaValidationVO();
        vo.setValid(false);
        if (dto == null || symbols == null) {
            vo.getViolations().add(new CaliberFormulaValidationVO.Violation(
                    GATE_V1_SYNTAX, CODE_SYNTAX, "DSL_V1 syntax: request or symbol registry missing (fail-closed)",
                    null, null, null, null));
            return vo;
        }
        vo.setRegistryMetricCount(symbols.size());

        // ── V1 语法 ──
        FormulaAst ast;
        try {
            ast = CaliberDslParser.parse(dto.getFormula());
        } catch (SyntaxError e) {
            vo.getViolations().add(new CaliberFormulaValidationVO.Violation(
                    GATE_V1_SYNTAX, CODE_SYNTAX, e.getMessage(),
                    e.getSymbol(), e.getLine(), e.getColumn(), e.getOffset()));
            return vo; // 语法不过 ⇒ V2~V4 无 AST 可走
        }
        String target = (dto.getCode() == null || dto.getCode().isBlank())
                ? ast.target.text : dto.getCode().trim();
        vo.setTarget(target);

        Ctx ctx = new Ctx(dto, symbols, target, ast);

        // ── V2 引用闭合 ──
        checkReferences(ast.expr, ctx, vo.getViolations());

        // ── V4 无循环依赖（先于 V3：单位推导会展开公式图，须保证无环）──
        checkCycles(ctx, vo.getViolations());

        // ── V3 量纲一致 ──
        String unit = deriveUnit(ast.expr, ctx, 0, vo.getViolations());
        if (unit == null) {
            log.warn("caliber-dsl V3: unit unresolved for formula '{}' → PASS (lenient, same philosophy as publish-gate V2)",
                    dto.getFormula());
        }

        vo.setValid(vo.getViolations().isEmpty());
        return vo;
    }

    /** 单次校验上下文。 */
    private static final class Ctx {
        final CaliberFormulaValidateDTO dto;
        final MetricSymbols symbols;
        final String target;
        final FormulaAst ast;
        /** 请求声明单位（符号→单位），优先级最高。 */
        final Map<String, String> declaredUnits;
        /** 请求声明的私有事实字段。 */
        final Set<String> declaredFields;
        /** V2 遍历中解析成功的指标引用（V4 建图的 BFS 种子）。 */
        final Set<String> referencedMetrics = new LinkedHashSet<>();
        /** V4 图：节点 → 引用的指标码集合。 */
        final Map<String, Set<String>> graph = new HashMap<>();
        final Map<String, String> formulaByText;

        Ctx(CaliberFormulaValidateDTO dto, MetricSymbols symbols, String target, FormulaAst ast) {
            this.dto = dto;
            this.symbols = symbols;
            this.target = target;
            this.ast = ast;
            this.declaredUnits = dto.getUnits() == null ? Map.of() : dto.getUnits();
            this.declaredFields = new LinkedHashSet<>();
            if (dto.getFactFields() != null) {
                for (String f : dto.getFactFields()) {
                    if (f != null && !f.isBlank()) declaredFields.add(f.trim());
                }
            }
            this.formulaByText = new HashMap<>();
            if (dto.getRelatedFormulas() != null) {
                this.formulaByText.putAll(dto.getRelatedFormulas());
            }
        }

        void metricRef(String name) {
            referencedMetrics.add(name);
        }
    }

    // ────────────────────────────────────────────────────────────

    private void checkReferences(Node node, Ctx ctx, List<CaliberFormulaValidationVO.Violation> out) {
        if (node instanceof NumberNode) {
            return;
        }
        if (node instanceof BinaryNode b) {
            checkReferences(b.left, ctx, out);
            checkReferences(b.right, ctx, out);
            return;
        }
        if (node instanceof FuncNode f) {
            for (Node a : f.args) checkReferences(a, ctx, out);
            return;
        }
        if (node instanceof RefNode r) {
            resolveRef(r.name.text, r.name, ctx, out);
            return;
        }
        if (node instanceof QualifiedRefNode q) {
            resolveRef(q.name.text, q.name, ctx, out);
            if (!CaliberFactFieldRegistry.isKnownQualifier(q.qualifier.text)
                    && !ctx.declaredUnits.containsKey(q.qualifier.text)) {
                out.add(new CaliberFormulaValidationVO.Violation(
                        GATE_V2_REFERENCE, CODE_UNRESOLVED_REFERENCE,
                        "DSL_V2 reference: qualifier " + q.qualifier.location()
                                + " is not a known qualifier (allowed: ACCEPTANCE/COLLECTION/SUBCONTRACT/PLAN/ACTUAL)",
                        q.qualifier.text, q.qualifier.line, q.qualifier.column, q.qualifier.offset));
            }
        }
    }

    /** 单个符号引用解析：指标码 / 事实字段 / 声明单位符号 / 左端目标自身。 */
    private void resolveRef(String name, Token token, Ctx ctx,
                            List<CaliberFormulaValidationVO.Violation> out) {
        if (name.equals(ctx.target)) {
            ctx.graph.computeIfAbsent(ctx.target, k -> new LinkedHashSet<>()).add(ctx.target);
            return; // 自引用：语法合法符号，交由 V4 判环
        }
        if (ctx.symbols.knownMetric(name)) {
            ctx.metricRef(name);
            return;
        }
        if (CaliberFactFieldRegistry.isKnownField(name)
                || ctx.declaredFields.contains(name)
                || ctx.declaredUnits.containsKey(name)) {
            return; // 业务事实字段 / 向导声明字段 / 声明单位符号
        }
        if (ctx.formulaByText.containsKey(name)) {
            ctx.metricRef(name);
            return; // 关联公式图中定义的中间量（如 stage_amount），V4 继续追
        }
        out.add(new CaliberFormulaValidationVO.Violation(
                GATE_V2_REFERENCE, CODE_UNRESOLVED_REFERENCE,
                "DSL_V2 reference: symbol " + token.location()
                        + " does not resolve to a metric code in ecos_metric_definition"
                        + " (R-4 unique semantic entry) or a business fact field",
                name, token.line, token.column, token.offset));
    }

    // ────────────────────────────────────────────────────────────
    // V4 循环依赖（公式图 DFS；拓扑排序不可行 ⇔ 有环）
    // ────────────────────────────────────────────────────────────

    private void checkCycles(Ctx ctx, List<CaliberFormulaValidationVO.Violation> out) {
        buildGraph(ctx);
        Set<String> visiting = new LinkedHashSet<>();
        List<String> path = new ArrayList<>();
        detectCycle(ctx.target, visiting, path, out, ctx);
    }

    private void buildGraph(Ctx ctx) {
        // 提交公式自身的边（target → 引用指标；V2 遍历的 ctx.graph 兜底重走保证完整）
        collectMetricRefs(ctx.ast.expr, ctx.target, ctx);
        // BFS 展开注册表 / 关联公式的下游边（宽松解析：他方公式语法问题不阻断本口径建图，
        // 每节点只展开一次 ⇒ 即使有环该循环也终止）
        java.util.Deque<String> queue = new java.util.ArrayDeque<>(ctx.referencedMetrics);
        queue.addFirst(ctx.target);
        while (!queue.isEmpty()) {
            String node = queue.poll();
            if (ctx.graph.containsKey(node)) {
                continue;
            }
            String exprText = ctx.formulaByText.get(node);
            if (exprText == null) {
                exprText = ctx.symbols.expressionOf(node);
            }
            Set<String> edges = new LinkedHashSet<>();
            if (exprText != null && !exprText.isBlank()) {
                try {
                    FormulaAst sub = CaliberDslParser.parse(exprText);
                    for (String ref : bareMetricRefs(sub.expr, ctx)) {
                        if (ctx.symbols.knownMetric(ref) || ctx.formulaByText.containsKey(ref)) {
                            edges.add(ref);
                        }
                    }
                } catch (SyntaxError ignored) {
                    // 关联公式不可解析 → 该节点视为叶（其自身缺陷由各自的 validate 判定）
                }
            }
            ctx.graph.put(node, edges);
            queue.addAll(edges);
        }
    }

    private void collectMetricRefs(Node node, String from, Ctx ctx) {
        if (node instanceof BinaryNode b) {
            collectMetricRefs(b.left, from, ctx);
            collectMetricRefs(b.right, from, ctx);
        } else if (node instanceof FuncNode f) {
            for (Node a : f.args) collectMetricRefs(a, from, ctx);
        } else if (node instanceof RefNode r) {
            if (ctx.symbols.knownMetric(r.name.text) || ctx.formulaByText.containsKey(r.name.text)
                    || r.name.text.equals(ctx.target)) {
                ctx.graph.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(r.name.text);
            }
        } else if (node instanceof QualifiedRefNode q) {
            if (ctx.symbols.knownMetric(q.name.text) || ctx.formulaByText.containsKey(q.name.text)
                    || q.name.text.equals(ctx.target)) {
                ctx.graph.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(q.name.text);
            }
        }
    }

    private Set<String> bareMetricRefs(Node node, Ctx ctx) {
        Set<String> out = new LinkedHashSet<>();
        collectInto(node, out, ctx);
        return out;
    }

    private void collectInto(Node node, Set<String> out, Ctx ctx) {
        if (node instanceof BinaryNode b) {
            collectInto(b.left, out, ctx);
            collectInto(b.right, out, ctx);
        } else if (node instanceof FuncNode f) {
            for (Node a : f.args) collectInto(a, out, ctx);
        } else if (node instanceof RefNode r) {
            out.add(r.name.text);
        } else if (node instanceof QualifiedRefNode q) {
            out.add(q.name.text);
        }
    }

    private void detectCycle(String node, Set<String> visiting, List<String> path,
                             List<CaliberFormulaValidationVO.Violation> out, Ctx ctx) {
        if (visiting.contains(node)) {
            List<String> cycle = new ArrayList<>(path.subList(path.indexOf(node), path.size()));
            cycle.add(node);
            String cpath = String.join(" -> ", cycle);
            out.add(new CaliberFormulaValidationVO.Violation(
                    GATE_V4_CYCLE, CODE_CYCLIC_DEPENDENCY,
                    "DSL_V4 dependency graph is cyclic (topological order impossible): " + cpath,
                    node, null, null, null));
            return;
        }
        if (path.size() > 32) {
            return; // 防御：超深链不再展开
        }
        visiting.add(node);
        path.add(node);
        for (String next : ctx.graph.getOrDefault(node, Set.of())) {
            detectCycle(next, visiting, path, out, ctx);
        }
        path.remove(path.size() - 1);
        visiting.remove(node);
    }

    // ────────────────────────────────────────────────────────────
    // V3 量纲一致（AST 单位推导；复用 MetricUnitDerivationGuard 单位元数据）
    // ────────────────────────────────────────────────────────────

    private String deriveUnit(Node node, Ctx ctx, int depth,
                              List<CaliberFormulaValidationVO.Violation> out) {
        if (node instanceof NumberNode) {
            return "SCALAR"; // 无量纲常数
        }
        if (node instanceof RefNode r) {
            return leafUnit(r.name.text, ctx, depth);
        }
        if (node instanceof QualifiedRefNode q) {
            return leafUnit(q.name.text, ctx, depth); // 限定符不改变单位
        }
        if (node instanceof BinaryNode b) {
            String lu = deriveUnit(b.left, ctx, depth + 1, out);
            String ru = deriveUnit(b.right, ctx, depth + 1, out);
            return combineBinary(b.op.text, lu, ru, b.op, out);
        }
        if (node instanceof FuncNode f) {
            List<String> units = new ArrayList<>();
            for (Node a : f.args) {
                units.add(deriveUnit(a, ctx, depth + 1, out));
            }
            return combineFunc(f.name.text, units, f.name, out);
        }
        return null;
    }

    /** 叶子单位：声明单位 > 发布门禁单位登记（复用）> 事实字段登记 > 由公式推导（深链受限）。 */
    private String leafUnit(String name, Ctx ctx, int depth) {
        String u = ctx.declaredUnits.get(name);
        if (u != null) {
            return u.toUpperCase();
        }
        u = MetricUnitDerivationGuard.UNIT_REGISTRY.get(name);
        if (u != null) {
            return u;
        }
        u = CaliberFactFieldRegistry.unitOf(name);
        if (u != null) {
            return u;
        }
        if (depth >= UNIT_DERIVE_MAX_DEPTH) {
            return null;
        }
        String exprText = ctx.formulaByText.get(name);
        if (exprText == null && ctx.symbols.knownMetric(name)) {
            exprText = ctx.symbols.expressionOf(name);
        }
        if (exprText == null || exprText.isBlank()) {
            return null;
        }
        try {
            FormulaAst sub = CaliberDslParser.parse(exprText);
            // 子公式推导失败不外溢（其自身由该指标自己的校验负责）
            return deriveUnit(sub.expr, ctx, depth + 1, new ArrayList<>());
        } catch (SyntaxError e) {
            return null;
        }
    }

    /** 二元算符单位代数；冲突产出违反项（定位到算符符号）。 */
    private String combineBinary(String op, String lu, String ru, Token opToken,
                                 List<CaliberFormulaValidationVO.Violation> out) {
        if (lu == null || ru == null) {
            return null; // 未知单位 → 宽松（与 MetricUnitDerivationGuard 未知叶子放行同哲学）
        }
        switch (op) {
            case "+":
            case "-":
                if (lu.equals(ru) || "SCALAR".equals(lu) || "SCALAR".equals(ru)) {
                    return "SCALAR".equals(lu) ? ru : lu;
                }
                out.add(dimensionConflict(opToken, op, lu, ru));
                return lu; // 冲突已登记，返回值仅避免下游级联报错
            case "*":
                if ("SCALAR".equals(lu)) return ru;
                if ("SCALAR".equals(ru)) return lu;
                if (lu.equals(ru)) return lu;
                if (isAmount(lu) && isRate(ru)) return "AMOUNT";   // PRD：金额×比率=金额
                if (isRate(lu) && isAmount(ru)) return "AMOUNT";
                return null; // 其它乘积未登记 → 宽松
            case "/":
                if ("SCALAR".equals(ru)) return lu;
                if (lu.equals(ru)) return "SCALAR".equals(lu) ? "SCALAR" : "RATE"; // 金额/金额=比率
                if (isAmount(lu) && "PERSON_COUNT".equals(ru)) return "AMOUNT";    // 人均金额仍是金额
                return null;
            default:
                return null;
        }
    }

    private String combineFunc(String name, List<String> units, Token token,
                               List<CaliberFormulaValidationVO.Violation> out) {
        switch (name) {
            case "SUM":
            case "ROUND":
                return units.isEmpty() ? null : units.get(0);
            case "MIN":
            case "MAX": {
                if (units.size() < 2) return null;
                String a = units.get(0), b = units.get(1);
                if (a != null && b != null && !a.equals(b) && !"SCALAR".equals(a) && !"SCALAR".equals(b)) {
                    out.add(dimensionConflict(token, name, a, b));
                    return a;
                }
                return a == null ? b : a;
            }
            case "IF": {
                if (units.size() < 3) return null;
                String t = units.get(1), e = units.get(2);
                if (t != null && e != null && !t.equals(e) && !"SCALAR".equals(t) && !"SCALAR".equals(e)) {
                    out.add(dimensionConflict(token, name, t, e));
                    return t;
                }
                return t == null ? e : t;
            }
            default:
                return null;
        }
    }

    private CaliberFormulaValidationVO.Violation dimensionConflict(
            Token token, String opOrFunc, String lu, String ru) {
        return new CaliberFormulaValidationVO.Violation(
                GATE_V3_DIMENSION, CODE_DIMENSION_CONFLICT,
                "DSL_V3 dimension: " + lu + " " + opOrFunc + " " + ru
                        + " is not derivable (amount+rate direct addition forbidden; amount*rate=amount)"
                        + " — operator " + token.location(),
                token.text, token.line, token.column, token.offset);
    }

    private static boolean isAmount(String u) {
        return "AMOUNT".equals(u);
    }

    private static boolean isRate(String u) {
        return "RATE".equals(u);
    }
}
