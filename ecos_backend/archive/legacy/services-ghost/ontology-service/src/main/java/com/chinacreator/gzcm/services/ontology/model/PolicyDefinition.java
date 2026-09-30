package com.chinacreator.gzcm.services.ontology.model;

public class PolicyDefinition {
    private String id;
    private String code;
    private String type;
    private String expression;

    public PolicyDefinition() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }
}
