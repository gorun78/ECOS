package com.chinacreator.gzcm.engine.kb.model;

/**
 * 知识分类校验（F-KB-18；#147H）。
 *
 * <p>三条判据：</p>
 * <ol>
 *   <li>分类值必须落在七类封闭枚举内（未知值拒绝，禁静默降级为自由文本）；</li>
 *   <li>Rule 类必须携带 {@code ruleRef}（指向 {@code ecos_ontology_rule} 的语义根，禁复制第二份）；</li>
 *   <li>非 Rule 类不得携带 {@code ruleRef}（避免"影子规则"绕过规则中心的版本/审批/发布门）。</li>
 * </ol>
 *
 * @author ECOS KB #147H
 */
public final class KnowledgeCategoryPolicy {

    /** 校验失败机读码。 */
    public static final String CODE_INVALID_CATEGORY = "KB-ONT-180";

    private KnowledgeCategoryPolicy() {
    }

    /**
     * 校验并返回规范化的分类码；空值允许（存量数据无分类），非空必须合法。
     *
     * @throws IllegalArgumentException 携带 {@link #CODE_INVALID_CATEGORY} 前缀
     */
    public static String validate(String category, String ruleRef) {
        if (category == null || category.isBlank()) {
            if (ruleRef != null && !ruleRef.isBlank()) {
                throw new IllegalArgumentException(CODE_INVALID_CATEGORY
                        + ": rule_ref 只能用于 Rule 类知识（当前未填分类）");
            }
            return null;
        }
        KnowledgeCategory parsed = KnowledgeCategory.fromCode(category);
        if (parsed == null) {
            throw new IllegalArgumentException(CODE_INVALID_CATEGORY
                    + ": 未知知识分类 '" + category + "'（七类闭集：Fact/Concept/Procedure/Rule/Case/Evidence/Pattern）");
        }
        boolean hasRuleRef = ruleRef != null && !ruleRef.isBlank();
        if (parsed.requiresRuleRef() && !hasRuleRef) {
            throw new IllegalArgumentException(CODE_INVALID_CATEGORY
                    + ": Rule 类知识必须携带 rule_ref（语义根 ecos_ontology_rule，禁复制第二份规则内容）");
        }
        if (!parsed.requiresRuleRef() && hasRuleRef) {
            throw new IllegalArgumentException(CODE_INVALID_CATEGORY
                    + ": 非 Rule 类（" + parsed.code() + "）不得携带 rule_ref，避免影子规则绕过发布门");
        }
        return parsed.code();
    }
}
