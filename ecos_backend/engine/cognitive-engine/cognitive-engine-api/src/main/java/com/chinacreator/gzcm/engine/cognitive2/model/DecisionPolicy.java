package com.chinacreator.gzcm.engine.cognitive2.model;

import java.sql.Timestamp;

/**
 * 决策策略 — 规则+约束的版本化定义。
 * 对应表 ecos_decision_policy。
 */
public class DecisionPolicy {

    private String id;
    private String name;
    private String category;
    /** JSONB 字段，以 String 存储 */
    private String rules;
    private int version;
    private String status;
    private Timestamp createdAt;

    public DecisionPolicy() {}

    public DecisionPolicy(String id, String name, String category, String rules,
                          int version, String status) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.rules = rules;
        this.version = version;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getRules() { return rules; }
    public void setRules(String rules) { this.rules = rules; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
