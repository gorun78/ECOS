package com.chinacreator.gzcm.services.ontology.model;

public class PropertyDefinition {
    private String id;
    private String code;
    private String name;
    private DataType type;
    private boolean required;
    private boolean indexed;
    private String validation;

    public PropertyDefinition() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public DataType getType() { return type; }
    public void setType(DataType type) { this.type = type; }
    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }
    public boolean isIndexed() { return indexed; }
    public void setIndexed(boolean indexed) { this.indexed = indexed; }
    public String getValidation() { return validation; }
    public void setValidation(String validation) { this.validation = validation; }
}
