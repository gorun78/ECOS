package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.Map;

public class ReasoningStep {
    private String stepId;
    private String description;
    private String ruleApplied;
    private Map<String, Object> inputFacts;
    private Object outputFact;
    private double confidence;

    public ReasoningStep() {
    }

    public ReasoningStep(String stepId, String description, String ruleApplied,
                         Map<String, Object> inputFacts, Object outputFact, double confidence) {
        this.stepId = stepId;
        this.description = description;
        this.ruleApplied = ruleApplied;
        this.inputFacts = inputFacts;
        this.outputFact = outputFact;
        this.confidence = confidence;
    }

    public String getStepId() {
        return stepId;
    }

    public void setStepId(String stepId) {
        this.stepId = stepId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRuleApplied() {
        return ruleApplied;
    }

    public void setRuleApplied(String ruleApplied) {
        this.ruleApplied = ruleApplied;
    }

    public Map<String, Object> getInputFacts() {
        return inputFacts;
    }

    public void setInputFacts(Map<String, Object> inputFacts) {
        this.inputFacts = inputFacts;
    }

    public Object getOutputFact() {
        return outputFact;
    }

    public void setOutputFact(Object outputFact) {
        this.outputFact = outputFact;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
