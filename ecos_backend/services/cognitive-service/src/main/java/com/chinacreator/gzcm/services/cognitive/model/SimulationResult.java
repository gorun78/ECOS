package com.chinacreator.gzcm.services.cognitive.model;

import java.util.Map;

public class SimulationResult {
    private String id;
    private String scenarioId;
    private SimulationStatus status;
    private Map<String, Object> outputState;
    private Map<String, Object> predictions;
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
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
