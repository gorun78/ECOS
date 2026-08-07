package com.chinacreator.gzcm.engine.cognitive2.model;

import java.util.List;
import java.util.Map;

public class SimulationResult {
    private String id;
    private String scenarioId;
    private SimulationStatus status;
    private Map<String, Object> outputState;
    private Map<String, Object> predictions;
    private Map<String, Object> baseline;
    private Map<String, Object> predicted;
    private Map<String, Object> deltas;
    private Map<String, Object> trends;
    private List<String> assumptions;
    private double confidence;
    private String summary;
    public SimulationResult() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getScenarioId() { return scenarioId; }
    public void setScenarioId(String scenarioId) { this.scenarioId = scenarioId; }
    public SimulationStatus getStatus() { return status; }
    public void setStatus(SimulationStatus status) { this.status = status; }
    public Map<String, Object> getOutputState() { return outputState; }
    public void setOutputState(Map<String, Object> outputState) { this.outputState = outputState; }
    public Map<String, Object> getPredictions() { return predictions; }
    public void setPredictions(Map<String, Object> predictions) { this.predictions = predictions; }
    public Map<String, Object> getBaseline() { return baseline; }
    public void setBaseline(Map<String, Object> baseline) { this.baseline = baseline; }
    public Map<String, Object> getPredicted() { return predicted; }
    public void setPredicted(Map<String, Object> predicted) { this.predicted = predicted; }
    public Map<String, Object> getDeltas() { return deltas; }
    public void setDeltas(Map<String, Object> deltas) { this.deltas = deltas; }
    public Map<String, Object> getTrends() { return trends; }
    public void setTrends(Map<String, Object> trends) { this.trends = trends; }
    public List<String> getAssumptions() { return assumptions; }
    public void setAssumptions(List<String> assumptions) { this.assumptions = assumptions; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
