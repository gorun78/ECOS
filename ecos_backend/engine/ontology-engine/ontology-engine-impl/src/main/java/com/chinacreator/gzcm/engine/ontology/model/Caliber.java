package com.chinacreator.gzcm.engine.ontology.model;

import java.time.LocalDateTime;

/**
 * 口径主表模型（表 {@code ecos_ontology.ecos_caliber}，V167.1）。
 * 列对齐 DDL：id/code/name/status/unit/currency/owner_role/dimension_json + DR06~DR08。
 */
public class Caliber {
    private String id;
    private String code;
    private String name;
    private String status;
    private String unit;
    private String currency;
    private String ownerRole;
    private String dimensionJson;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String createBy;
    private String updateBy;
    private Integer isDeleted;
    private String versionNo;
    private String domain;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
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
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }
    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
}
