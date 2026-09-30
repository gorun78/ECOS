package com.chinacreator.gzcm.services.ontology.model;

public class ActionDefinition {
    private String id;
    private String code;
    private String name;
    private ActionType type;

    public ActionDefinition() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ActionType getType() { return type; }
    public void setType(ActionType type) { this.type = type; }
}
