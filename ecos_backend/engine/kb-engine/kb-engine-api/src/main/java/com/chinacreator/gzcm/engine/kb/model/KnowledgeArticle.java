package com.chinacreator.gzcm.engine.kb.model;

public class KnowledgeArticle {

    private String id;
    private String title;
    private String content;
    private String domain;
    private String category;
    /** 七分类封闭枚举（Fact/Concept/Procedure/Rule/Case/Evidence/Pattern；#147H）。 */
    private String knowledgeCategory;
    /** Rule 类知识的语义根引用（ecos_ontology_rule.id；非 Rule 类必须为空）。 */
    private String ruleRef;
    private String sourceType;
    private String status;
    private long createdAt;
    private long updatedAt;

    public KnowledgeArticle() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getKnowledgeCategory() { return knowledgeCategory; }
    public void setKnowledgeCategory(String knowledgeCategory) { this.knowledgeCategory = knowledgeCategory; }
    public String getRuleRef() { return ruleRef; }
    public void setRuleRef(String ruleRef) { this.ruleRef = ruleRef; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}