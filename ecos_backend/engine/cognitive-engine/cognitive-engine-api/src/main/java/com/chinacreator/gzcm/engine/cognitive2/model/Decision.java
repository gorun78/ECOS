package com.chinacreator.gzcm.engine.cognitive2.model;

import java.sql.Timestamp;

/**
 * 决策实体 — 决策智能层的一等公民节点。
 * 对应表 ecos_decision。
 */
public class Decision {

    private String id;
    private String category;
    private String scenario;
    private String reasoning;
    private String outcome;
    private double confidence;
    private String decisionMaker;
    private Timestamp validFrom;
    private Timestamp validUntil;
    /** JSONB 字段，以 String 存储 */
    private String metadata;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Decision() {}

    public Decision(String id, String category, String scenario, String reasoning,
                    String outcome, double confidence, String decisionMaker,
                    Timestamp validFrom, Timestamp validUntil, String metadata) {
        this.id = id;
        this.category = category;
        this.scenario = scenario;
        this.reasoning = reasoning;
        this.outcome = outcome;
        this.confidence = confidence;
        this.decisionMaker = decisionMaker;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.metadata = metadata;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getDecisionMaker() { return decisionMaker; }
    public void setDecisionMaker(String decisionMaker) { this.decisionMaker = decisionMaker; }
    public Timestamp getValidFrom() { return validFrom; }
    public void setValidFrom(Timestamp validFrom) { this.validFrom = validFrom; }
    public Timestamp getValidUntil() { return validUntil; }
    public void setValidUntil(Timestamp validUntil) { this.validUntil = validUntil; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
