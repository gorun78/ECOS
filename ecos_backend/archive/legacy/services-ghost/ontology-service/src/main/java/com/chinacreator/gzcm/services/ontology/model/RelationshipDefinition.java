package com.chinacreator.gzcm.services.ontology.model;

public class RelationshipDefinition {
    private String id;
    private String sourceEntity;
    private String targetEntity;
    private RelationshipType type;
    private Cardinality cardinality;

    public RelationshipDefinition() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceEntity() { return sourceEntity; }
    public void setSourceEntity(String sourceEntity) { this.sourceEntity = sourceEntity; }
    public String getTargetEntity() { return targetEntity; }
    public void setTargetEntity(String targetEntity) { this.targetEntity = targetEntity; }
    public RelationshipType getType() { return type; }
    public void setType(RelationshipType type) { this.type = type; }
    public Cardinality getCardinality() { return cardinality; }
    public void setCardinality(Cardinality cardinality) { this.cardinality = cardinality; }
}
