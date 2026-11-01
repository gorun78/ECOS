package com.chinacreator.gzcm.engine.kb.model;

/**
 * 知识对象七分类封闭枚举（R11 / F-KB-18；§7.1.2；#147H）。
 *
 * <p><b>封闭而非自由文本</b>：知识资产树根分类维度收敛为七类，新增族须改 DDL 与枚举
 * （受控新增），禁止在业务侧写任意字符串。Rule 类知识与规则中心同源：
 * 以 {@code rule_carrier=KNOWLEDGE_ASSET} 的 {@code ecos_ontology_rule} 行为语义根，
 * 知识侧只持有 {@code rule_ref} 引用，<b>不复制第二份规则内容</b>（§6.2.6③ / §7.1.2）。</p>
 *
 * @author ECOS KB #147H
 */
public enum KnowledgeCategory {

    /** 事实：可核验的陈述。 */
    FACT("Fact"),
    /** 概念：定义与术语。 */
    CONCEPT("Concept"),
    /** 过程：步骤与操作指南。 */
    PROCEDURE("Procedure"),
    /** 规则：以 ontology_rule 为语义根（须带 rule_ref）。 */
    RULE("Rule"),
    /** 案例：经验与复盘。 */
    CASE("Case"),
    /** 证据：审计/合规留痕。 */
    EVIDENCE("Evidence"),
    /** 模式：可复用结构/范式。 */
    PATTERN("Pattern");

    private final String code;

    KnowledgeCategory(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** 大小写不敏感解析；空值返回 {@code null}（是否必填由调用方决定）；未知值返回 {@code null}。 */
    public static KnowledgeCategory fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String c = code.trim();
        for (KnowledgeCategory v : values()) {
            if (v.code.equalsIgnoreCase(c) || v.name().equalsIgnoreCase(c)) {
                return v;
            }
        }
        return null;
    }

    /** 是否要求 {@code rule_ref}（Rule 类知识必须指向 ontology_rule 语义根）。 */
    public boolean requiresRuleRef() {
        return this == RULE;
    }
}
