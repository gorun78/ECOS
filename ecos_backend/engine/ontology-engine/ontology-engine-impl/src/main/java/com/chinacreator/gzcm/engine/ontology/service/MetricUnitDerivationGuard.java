package com.chinacreator.gzcm.engine.ontology.service;

import com.chinacreator.gzcm.engine.ontology.dto.GateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * F03-02 / F03-03 V2 — 指标单位推导 + 可加性守卫（发布门禁 V2 实现体）。
 *
 * <p><b>单位代数</b>（叶子带单位，四则算符 ± ÷ ×；简化 tokenizer 支持单一顶层算符）：</p>
 * <ul>
 *   <li>{@code AMOUNT + AMOUNT = AMOUNT}、{@code MONTH + MONTH = MONTH}（同单位加减 = 同单位）；</li>
 *   <li>{@code AMOUNT / AMOUNT = RATE}；</li>
 *   <li>{@code AMOUNT / PERSON_COUNT = AMOUNT}（人均金额仍是金额）；</li>
 *   <li>其它组合（如 {@code RATE - AMOUNT}）→ 400 {@code ECOS-ONTO-021}，
 *       violations.refs 携带推导链（左单位 / 右单位 / 算符）。</li>
 * </ul>
 *
 * <p><b>可加性守卫</b>（{@code ECOS-ONTO-022}）：结果为 {@code RATE} 或 {@code MONTH}
 * 的指标不得声明 {@code additive=1}（比率/月非可加聚合）。</p>
 *
 * <p><b>简化 tokenizer</b>（设计允许，测试以文档 10 项为口径断言）：
 * 正则扫描顶层（括号平衡剥离后）{@code + - * /}；任何 {@code ^\w+$} token 视为叶子变量，
 * 单位取自下方 10 项规范登记（F03-02 PRD-03 §1.3）。表达式仅含已知叶子 + 单一算符时才按代数判定；
 * 无法解析（未知叶子 / 多顶层算符 / 纯叶子）视为 PASS 并 WARN（不阻断发布）——
 * 10 项文档口径集是断言基线，未登记的不入冲突。</p>
 */
final class MetricUnitDerivationGuard {

    private static final Logger log = LoggerFactory.getLogger(MetricUnitDerivationGuard.class);

    /** 10 项规范指标 → 叶子单位（F03-02 PRD-03 §1.3）。 */
    static final Map<String, String> UNIT_REGISTRY = new LinkedHashMap<>();

    static {
        UNIT_REGISTRY.put("M_ACCEPTANCE_AMT", "AMOUNT");
        UNIT_REGISTRY.put("M_COLLECTION_AMT", "AMOUNT");
        UNIT_REGISTRY.put("M_SUBCONTRACT_AMT", "AMOUNT");
        UNIT_REGISTRY.put("M_FC_REVENUE", "AMOUNT");
        UNIT_REGISTRY.put("M_COST_SALARY", "AMOUNT");
        UNIT_REGISTRY.put("M_COST_MGMT", "AMOUNT");
        UNIT_REGISTRY.put("M_COST_MKT", "AMOUNT");
        UNIT_REGISTRY.put("M_FC_PROFIT", "AMOUNT");
        UNIT_REGISTRY.put("M_REALIZATION_RATE", "RATE");
        UNIT_REGISTRY.put("M_COLLECTION_CYCLE", "MONTH");
    }

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*|\\+|-|\\*|/");

    private MetricUnitDerivationGuard() {
    }

    /**
     * V2 判定主入口。
     *
     * @param metricCode        指标编码（用于推导链命名）
     * @param expression        四则表达式（空白 → PASS）
     * @param declaredAdditive  声明 additive（TRUE=可加；FALSE/null=不可加）
     *                          — 结果单位 ∈ {RATE,MONTH} 且声明可加 → {@code ECOS-ONTO-022}
     * @return 违反项列表（空 = PASS）
     */
    static List<GateResult.GateViolation> check(String metricCode, String expression, Boolean declaredAdditive) {
        List<GateResult.GateViolation> out = new ArrayList<>();
        if (expression == null || expression.isBlank()) {
            return out;
        }
        Resolved r = derive(expression);

        if (r == null) {
            // 无法解析（未知叶子 / 多顶层算符 / 纯叶子）→ PASS + WARN，不阻断发布
            log.warn("publish-gate V2: unresolved expression for metric={} expr='{}' → PASS (unresolved)",
                    metricCode, expression);
            return out;
        }
        if (!r.legal) {
            GateResult.GateViolation v = new GateResult.GateViolation(
                    GateResult.GATE_V2_UNIT, GateResult.CODE_UNIT_CONFLICT,
                    "metric '" + metricCode + "' unit derivation conflict"
                            + " (\"" + r.left + "\" as " + r.leftUnit
                            + " " + r.op
                            + " \"" + r.right + "\" as " + r.rightUnit
                            + ")");
            v.addRef(metricCode).addRef(r.left + ":" + r.leftUnit).addRef(r.op).addRef(r.right + ":" + r.rightUnit);
            out.add(v);
            return out;
        }
        // 单位推导合法 → 可加性守卫
        if (Boolean.TRUE.equals(declaredAdditive)
                && (r.resultUnit.equals("RATE") || r.resultUnit.equals("MONTH"))) {
            out.add(new GateResult.GateViolation(
                    GateResult.GATE_V2_UNIT, GateResult.CODE_ADDITIVITY_CONFLICT,
                    "metric '" + metricCode + "' result unit '" + r.resultUnit
                            + "' is non-additive but declared additive=1")
                    .addRef(metricCode).addRef("additive=1").addRef("resultUnit=" + r.resultUnit));
        }
        return out;
    }

    // ────────────────────────────────────────────────────────────
    // 推导
    // ────────────────────────────────────────────────────────────

    private static final class Resolved {
        boolean legal;       // true = 代数可判定；false = 冲突
        String resultUnit;   // 合法时 = 结果单位
        String left, right;  // 叶子
        String leftUnit, rightUnit;
        String op;
    }

    /** 推导表达式结果单位；未知叶子/多顶层算符/纯叶子 → null（PASS 上游）。 */
    private static Resolved derive(String expression) {
        String expr = stripParens(expression);
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(expr);
        while (m.find()) {
            tokens.add(m.group());
        }
        String operand1 = null, op = null, operand2 = null;
        int operandIdx = 0;
        for (String tok : tokens) {
            if (tok.equals("+") || tok.equals("-") || tok.equals("*") || tok.equals("/")) {
                if (op != null) return null; // 多顶层算符 → 复合表达式 → 无法解析
                op = tok;
            } else if (operandIdx == 0) {
                operand1 = tok;
                operandIdx = 1;
            } else if (operandIdx == 1) {
                operand2 = tok;
                operandIdx = 2;
            } else {
                return null; // 3+ 操作数 → 无法解析
            }
        }
        if (operand2 == null) {
            // 单叶子（无算符）→ 纯叶子指标（如 M_FC_PROFIT），单位 = 登记单位
            if (operand1 != null && UNIT_REGISTRY.containsKey(operand1)) {
                Resolved r = new Resolved();
                r.legal = true;
                r.resultUnit = UNIT_REGISTRY.get(operand1);
                r.left = operand1;
                r.leftUnit = r.resultUnit;
                r.op = "";
                return r;
            }
            return null; // 纯未知叶子 → 无法解析
        }

        Resolved r = new Resolved();
        r.left = operand1;
        r.right = operand2;
        r.op = op;
        r.leftUnit = UNIT_REGISTRY.get(operand1);
        r.rightUnit = UNIT_REGISTRY.get(operand2);
        if (r.leftUnit == null || r.rightUnit == null) {
            return null; // 有未知叶子 → 无法解析（不冲突，PASS）
        }
        String result = applyOp(op, r.leftUnit, r.rightUnit);
        if (result == null) {
            r.legal = false; // 单位冲突 → ECOS-ONTO-021
            return r;
        }
        r.legal = true;
        r.resultUnit = result;
        return r;
    }

    private static String stripParens(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c != '(' && c != ')' && !Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 单位代数；null = 冲突（上游产出 ECOS-ONTO-021）。 */
    private static String applyOp(String op, String lu, String ru) {
        switch (op) {
            case "+":
            case "-":
                return lu.equals(ru) ? lu : null; // 同单位加减 → 同单位；不同 → 冲突
            case "*":
                return null; // 设计简化代数不含 ×（未知 → 冲突）
            case "/":
                if (lu.equals("AMOUNT") && ru.equals("AMOUNT")) return "RATE";
                if (lu.equals("AMOUNT") && ru.equals("PERSON_COUNT")) return "AMOUNT";
                return null; // 其它除法 → 冲突
            default:
                return null;
        }
    }
}
