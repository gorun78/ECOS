package com.chinacreator.gzcm.engine.cognitive2.model;

import java.sql.Timestamp;

/**
 * 决策先例 — 历史相似决策的引用记录，支持先例检索。
 * 对应表 ecos_decision_precedent。
 */
public class DecisionPrecedent {

    private String id;
    private String decisionId;
    private String similarDecisionId;
    private double similarity;
    private String note;
    private Timestamp createdAt;

    public DecisionPrecedent() {}

    public DecisionPrecedent(String id, String decisionId, String similarDecisionId,
                             double similarity, String note) {
        this.id = id;
        this.decisionId = decisionId;
        this.similarDecisionId = similarDecisionId;
        this.similarity = similarity;
        this.note = note;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDecisionId() { return decisionId; }
    public void setDecisionId(String decisionId) { this.decisionId = decisionId; }
    public String getSimilarDecisionId() { return similarDecisionId; }
    public void setSimilarDecisionId(String similarDecisionId) { this.similarDecisionId = similarDecisionId; }
    public double getSimilarity() { return similarity; }
    public void setSimilarity(double similarity) { this.similarity = similarity; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
