package com.chinacreator.gzcm.services.knowledge.rag;

import java.util.ArrayList;
import java.util.List;

public class RagResponse {
    private String query;
    private String answer;
    private List<DocumentChunk> sources = new ArrayList<>();
    private double confidence;

    public RagResponse() {}

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<DocumentChunk> getSources() { return sources; }
    public void setSources(List<DocumentChunk> sources) { this.sources = sources; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
}
