package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.List;

public class ReasoningPath {
    private List<ReasoningStep> steps;
    private String conclusion;
    private String justification;

    public ReasoningPath() {
    }

    public ReasoningPath(List<ReasoningStep> steps, String conclusion, String justification) {
        this.steps = steps;
        this.conclusion = conclusion;
        this.justification = justification;
    }

    public List<ReasoningStep> getSteps() {
        return steps;
    }

    public void setSteps(List<ReasoningStep> steps) {
        this.steps = steps;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }
}
