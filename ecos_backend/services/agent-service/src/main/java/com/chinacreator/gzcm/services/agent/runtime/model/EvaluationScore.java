package com.chinacreator.gzcm.services.agent.runtime.model;

public class EvaluationScore {
    private String resultId;
    private double correctness;
    private double completeness;
    private double safety;
    private double efficiency;
    private double overall;
    private String feedback;

    public EvaluationScore() {}

    public String getResultId() { return resultId; }
    public void setResultId(String resultId) { this.resultId = resultId; }
    public double getCorrectness() { return correctness; }
    public void setCorrectness(double correctness) { this.correctness = correctness; }
    public double getCompleteness() { return completeness; }
    public void setCompleteness(double completeness) { this.completeness = completeness; }
    public double getSafety() { return safety; }
    public void setSafety(double safety) { this.safety = safety; }
    public double getEfficiency() { return efficiency; }
    public void setEfficiency(double efficiency) { this.efficiency = efficiency; }
    public double getOverall() { return overall; }
    public void setOverall(double overall) { this.overall = overall; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
}
