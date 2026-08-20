package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.List;

public class Justification {
    private String conclusion;
    private ReasoningPath path;
    private List<String> evidence;

    public Justification() {
    }

    public Justification(String conclusion, ReasoningPath path, List<String> evidence) {
        this.conclusion = conclusion;
        this.path = path;
        this.evidence = evidence;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public ReasoningPath getPath() {
        return path;
    }

    public void setPath(ReasoningPath path) {
        this.path = path;
    }

    public List<String> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<String> evidence) {
        this.evidence = evidence;
    }
}
