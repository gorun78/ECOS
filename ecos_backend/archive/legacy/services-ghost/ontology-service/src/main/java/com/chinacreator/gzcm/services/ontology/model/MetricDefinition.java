package com.chinacreator.gzcm.services.ontology.model;

public class MetricDefinition {
    private String id;
    private String code;
    private String name;
    private String expression;
    private AggregationType aggregation;

    public MetricDefinition() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
    public AggregationType getAggregation() { return aggregation; }
    public void setAggregation(AggregationType aggregation) { this.aggregation = aggregation; }
}
