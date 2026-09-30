package com.chinacreator.gzcm.services.ontology.compiler;

public class CompilationResult {
    private String name;
    private boolean success;
    private int entityCount;
    private int relationshipCount;
    private int metricCount;
    private boolean dbGenerated;
    private String message;

    public CompilationResult() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public int getEntityCount() { return entityCount; }
    public void setEntityCount(int entityCount) { this.entityCount = entityCount; }
    public int getRelationshipCount() { return relationshipCount; }
    public void setRelationshipCount(int relationshipCount) { this.relationshipCount = relationshipCount; }
    public int getMetricCount() { return metricCount; }
    public void setMetricCount(int metricCount) { this.metricCount = metricCount; }
    public boolean isDbGenerated() { return dbGenerated; }
    public void setDbGenerated(boolean dbGenerated) { this.dbGenerated = dbGenerated; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
