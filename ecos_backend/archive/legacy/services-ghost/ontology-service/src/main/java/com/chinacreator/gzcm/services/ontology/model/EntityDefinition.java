package com.chinacreator.gzcm.services.ontology.model;

import java.util.ArrayList;
import java.util.List;

public class EntityDefinition {
    private String id;
    private String code;
    private String name;
    private String description;
    private EntityCategory category;
    private List<PropertyDefinition> properties = new ArrayList<>();
    private LifecycleDefinition lifecycle;

    public EntityDefinition() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public EntityCategory getCategory() { return category; }
    public void setCategory(EntityCategory category) { this.category = category; }
    public List<PropertyDefinition> getProperties() { return properties; }
    public void setProperties(List<PropertyDefinition> properties) { this.properties = properties; }
    public LifecycleDefinition getLifecycle() { return lifecycle; }
    public void setLifecycle(LifecycleDefinition lifecycle) { this.lifecycle = lifecycle; }
}
