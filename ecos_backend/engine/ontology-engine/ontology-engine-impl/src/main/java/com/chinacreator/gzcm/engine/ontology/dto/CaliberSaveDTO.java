package com.chinacreator.gzcm.engine.ontology.dto;

/** 口径创建/更新入参（POST /api/v1/ontology/calibers）。 */
public class CaliberSaveDTO {
    private String code;
    private String name;
    private String status;
    private String unit;
    private String currency;
    private String ownerRole;
    private String dimensionJson;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getOwnerRole() { return ownerRole; }
    public void setOwnerRole(String ownerRole) { this.ownerRole = ownerRole; }
    public String getDimensionJson() { return dimensionJson; }
    public void setDimensionJson(String dimensionJson) { this.dimensionJson = dimensionJson; }
}
