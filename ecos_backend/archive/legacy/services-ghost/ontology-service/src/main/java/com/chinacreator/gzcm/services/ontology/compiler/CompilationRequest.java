package com.chinacreator.gzcm.services.ontology.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CompilationRequest {
    private String name;
    private List<Map<String, Object>> entities = new ArrayList<>();
    private List<Map<String, Object>> relationships = new ArrayList<>();
    private List<Map<String, Object>> metrics = new ArrayList<>();

    public CompilationRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Map<String, Object>> getEntities() { return entities; }
    public void setEntities(List<Map<String, Object>> entities) { this.entities = entities; }
    public List<Map<String, Object>> getRelationships() { return relationships; }
    public void setRelationships(List<Map<String, Object>> relationships) { this.relationships = relationships; }
    public List<Map<String, Object>> getMetrics() { return metrics; }
    public void setMetrics(List<Map<String, Object>> metrics) { this.metrics = metrics; }
}
