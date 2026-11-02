package com.chinacreator.gzcm.engine.kb.model;

/**
 * 改分类请求体（#147U 编辑腿）。
 *
 * <p>强类型 DTO（禁 {@code Map<String,Object>}）：{@code category} 必填、取值闭集见
 * {@link KnowledgeCategory}；{@code ruleRef} 仅 Rule 类可带（非 Rule 传空，服务端会显式写 NULL）。</p>
 *
 * @author ECOS KB #147U
 */
public class KnowledgeCategoryUpdateDTO {

    private String category;

    private String ruleRef;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRuleRef() {
        return ruleRef;
    }

    public void setRuleRef(String ruleRef) {
        this.ruleRef = ruleRef;
    }
}
