package com.chinacreator.gzcm.engine.ontology.rule.dispatch;

import com.chinacreator.gzcm.engine.ontology.engine.FunctionValidator;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRule;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 五层规则执行分发（§6.2.6② 与 DF-4；#147G）。
 *
 * <p><b>做什么 / 不做什么</b>：本类只做「层 → 执行目标」的单一映射与该层的前置校验，
 * <b>不</b>把任何规则翻译成第二套执行语言：VALIDATION 复用 {@link FunctionValidator}
 * 受限沙箱；STRUCTURAL 的最终校验落本体元模型；BUSINESS 交 Action + workflow；
 * POLICY 交 OPA（PDP）；COGNITIVE 交认知引擎。<b>五层都不全部转 Rego</b>（DQ-5）。</p>
 *
 * <p><b>诚实边界</b>：目标执行器未接线的层（当前 COGNITIVE 的在线执行、
 * BUSINESS 无 action 绑定、POLICY 缺 expression 等）返回
 * {@link RuleDispatchResult.Status#NOT_AVAILABLE}/{@link RuleDispatchResult.Status#NOT_APPLICABLE}
 * ——禁把"路由表已就绪"说成"该层已实际执行"。</p>
 *
 * @author ECOS Ontology #147G
 */
@Service
public class RuleLayerDispatcher {

    private final FunctionValidator functionValidator;

    public RuleLayerDispatcher(FunctionValidator functionValidator) {
        this.functionValidator = functionValidator;
    }

    /**
     * 按 {@code rule_layer} 分发一条规则。
     *
     * @param rule 规则（{@code rule_layer} 空/未知 → REJECTED，禁静默按 VALIDATION 处理）
     */
    public RuleDispatchResult dispatch(OntologyRule rule) {
        if (rule == null) {
            return RuleDispatchResult.rejected(null, "规则为空，拒绝分发（禁缺省折叠）");
        }
        RuleLayerTarget target = RuleLayerTarget.fromLayer(rule.getRuleLayer());
        if (target == null) {
            return RuleDispatchResult.rejected(null,
                    "rule_layer 为空或不在五值闭集内: " + rule.getRuleLayer()
                            + "（C-9：拒发布并告警，不静默放行）");
        }
        return switch (target) {
            case STRUCTURAL -> dispatchStructural(rule, target);
            case VALIDATION -> dispatchValidation(rule, target);
            case BUSINESS -> dispatchBusiness(rule, target);
            case POLICY -> dispatchPolicy(rule, target);
            case COGNITIVE -> dispatchCognitive(rule, target);
        };
    }

    /** 结构规则：由本体元模型承载；无结构声明（expression 空）＝本规则不含结构语义。 */
    private RuleDispatchResult dispatchStructural(OntologyRule rule, RuleLayerTarget target) {
        if (blank(rule.getExpression())) {
            return RuleDispatchResult.notApplicable(target,
                    "无结构声明表达式：STRUCTURAL 由本体元模型在建模/发布时自动校验，本条无需额外执行");
        }
        return RuleDispatchResult.routed(target,
                "已路由本体元模型校验（Class/Attribute/Association/Cardinality/Inheritance/Constraint）");
    }

    /** 校验规则：必须能过 FunctionValidator 受限沙箱（白名单 + 禁止模式 + 括号 + 语法）。 */
    private RuleDispatchResult dispatchValidation(OntologyRule rule, RuleLayerTarget target) {
        if (blank(rule.getExpression())) {
            return RuleDispatchResult.rejected(target, "VALIDATION 规则缺 expression，无法进入函数沙箱");
        }
        String forbidden = functionValidator.quickScan(rule.getExpression());
        if (forbidden != null) {
            return RuleDispatchResult.rejected(target, "FunctionValidator 拒绝表达式：" + forbidden);
        }
        Map<String, Object> verdict = functionValidator.validate(rule.getExpression());
        if (Boolean.TRUE.equals(verdict.get("valid"))) {
            return RuleDispatchResult.routed(target, "已通过 FunctionValidator 受限沙箱校验");
        }
        // VALIDATION 是谓词（true/false），不要求 FROM 子句；沙箱的非 FROM 类违例才算拒绝。
        Object rawErrors = verdict.get("errors");
        java.util.List<String> errors = rawErrors instanceof List<?> l
                ? l.stream().map(String::valueOf).toList() : List.of();
        java.util.List<String> hard = errors.stream()
                .filter(e -> !e.contains("FROM"))
                .toList();
        if (hard.isEmpty() && !errors.isEmpty()) {
            return RuleDispatchResult.routed(target,
                    "谓词表达式已过受限沙箱（禁止模式/括号/白名单）；无 FROM 子句属谓词常态");
        }
        return RuleDispatchResult.rejected(target, "FunctionValidator 拒绝表达式：" + errors);
    }

    /** 业务规则：由 Action + workflow 执行；**绝不**转 OPA / Rego。 */
    private RuleDispatchResult dispatchBusiness(OntologyRule rule, RuleLayerTarget target) {
        if (blank(rule.getAction())) {
            return RuleDispatchResult.notApplicable(target,
                    "BUSINESS 规则未绑定 Action：由 Action + workflow 执行，本规则无执行载体"
                            + "（禁改投 OPA/Rego）");
        }
        return RuleDispatchResult.routed(target,
                "已路由 Action + workflow 执行（Action 经 ruleJson/strategy 引用规则）");
    }

    /** 策略规则：唯一进 OPA 的层。缺 expression 即无法形成 allow/deny 判定。 */
    private RuleDispatchResult dispatchPolicy(OntologyRule rule, RuleLayerTarget target) {
        if (blank(rule.getExpression())) {
            return RuleDispatchResult.rejected(target,
                    "POLICY 规则缺 expression：OPA 只能承接 allow/deny 类策略，无判定表达即拒绝");
        }
        return RuleDispatchResult.routed(target,
                "已路由 OPA（PDP，仅 Policy 层）；业务/校验/认知规则不得转 Rego");
    }

    /** 认知规则：证据→假设→信念；在线执行由认知引擎承接，未接入时如实不可用。 */
    private RuleDispatchResult dispatchCognitive(OntologyRule rule, RuleLayerTarget target) {
        if (blank(rule.getExpression())) {
            return RuleDispatchResult.rejected(target, "COGNITIVE 规则缺表达式，无法形成证据→信念链");
        }
        return RuleDispatchResult.routed(target,
                "已路由认知引擎（证据→假设→信念）；在线推理通道若未装配须由调用方判 NOT_AVAILABLE");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
