package com.chinacreator.gzcm.engine.ontology.rule.dispatch;

/**
 * 五层规则的执行目标（§6.2.6②；#147G）。
 *
 * <p><b>单源表</b>：{@code rule_layer} → 执行目标的一一映射只在本枚举里写一次。
 * 任何域不得再写「我的层→我的执行器」第二份表（由
 * {@code RuleLayerDispatchGateTest} 仓扫钉住）。</p>
 *
 * <p><b>两条不可逾越的边界</b>（§4.2.6 / DQ-5）：</p>
 * <ul>
 *   <li>{@code POLICY} 是 <b>唯一</b> 进 OPA 的层——OPA = PDP，仅承接 allow/deny 类策略；</li>
 *   <li>{@code BUSINESS} 绝不进 OPA（业务规则由 Action + workflow 执行），
 *       也绝不转 Rego。把折扣/审批这类业务逻辑塞进 Rego 会让策略层变成第二套业务引擎。</li>
 * </ul>
 *
 * @author ECOS Ontology #147G
 */
public enum RuleLayerTarget {

    /** 结构规则 → 本体元模型（Class/Attribute/Association/Cardinality/Inheritance/Constraint）。 */
    STRUCTURAL("ONTOLOGY_METAMODEL", "本体元模型自动校验", false),

    /** 校验规则（谓词 true/false）→ FunctionValidator 受限沙箱。 */
    VALIDATION("FUNCTION_VALIDATOR", "FunctionValidator 受限沙箱", false),

    /** 业务规则（Approve/Reject/Discount）→ Action + workflow（Action 经 ruleJson/strategy 引用规则）。 */
    BUSINESS("ACTION_WORKFLOW", "Action + workflow 执行", false),

    /** 策略规则（allow/deny）→ OPA（仅此层）。 */
    POLICY("OPA_POLICY_ENGINE", "OPA 策略执行层（仅 Policy 层）", true),

    /** 认知规则（证据→假设→信念）→ 认知引擎。 */
    COGNITIVE("COGNITIVE_ENGINE", "认知引擎", false);

    private final String targetCode;
    private final String targetLabel;
    private final boolean opaBound;

    RuleLayerTarget(String targetCode, String targetLabel, boolean opaBound) {
        this.targetCode = targetCode;
        this.targetLabel = targetLabel;
        this.opaBound = opaBound;
    }

    public String targetCode() {
        return targetCode;
    }

    public String targetLabel() {
        return targetLabel;
    }

    /** 是否由 OPA 执行（全枚举仅 POLICY 为真）。 */
    public boolean opaBound() {
        return opaBound;
    }

    /** 由词表名解析；未知层返回 {@code null}（禁静默默认值）。 */
    public static RuleLayerTarget fromLayer(String layer) {
        if (layer == null || layer.isBlank()) {
            return null;
        }
        try {
            return valueOf(layer.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
