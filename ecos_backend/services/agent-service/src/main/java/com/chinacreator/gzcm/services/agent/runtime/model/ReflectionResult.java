package com.chinacreator.gzcm.services.agent.runtime.model;

import java.util.ArrayList;
import java.util.List;

public class ReflectionResult {
    private String resultId;
    private List<String> gaps = new ArrayList<>();
    private List<String> improvements = new ArrayList<>();
    private ExecutionPlan revisedPlan;

    public ReflectionResult() {}

    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }
    public List<String> getGaps() { return gaps; }
    public void setGaps(List<String> gaps) { this.gaps = gaps; }
    public List<String> getImprovements() { return improvements; }
    public void setImprovements(List<String> improvements) { this.improvements = improvements; }
    public ExecutionPlan getRevisedPlan() { return revisedPlan; }
    public void setRevisedPlan(ExecutionPlan revisedPlan) { this.revisedPlan = revisedPlan; }
}
