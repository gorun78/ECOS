package com.chinacreator.gzcm.engine.ontology.dto;

import com.chinacreator.gzcm.engine.ontology.model.Caliber;

/** 口径视图（对外只读契约，D.1 listCalibers/getCurrentCaliber）。 */
public class CaliberVO {
    private String id;
    private String code;
    private String name;
    private String status;
    private String unit;
    private String currency;
    private String ownerRole;
    private String dimensionJson;
    private String currentVersionNo;
    private String createBy;
    private String createTime;

    public static CaliberVO from(Caliber c, String currentVersionNo) {
        CaliberVO v = new CaliberVO();
        v.id = c.getId(); v.code = c.getCode(); v.name = c.getName(); v.status = c.getStatus();
        v.unit = c.getUnit(); v.currency = c.getCurrency(); v.ownerRole = c.getOwnerRole();
        v.dimensionJson = c.getDimensionJson(); v.currentVersionNo = currentVersionNo;
        v.createBy = c.getCreateBy();
        v.createTime = c.getCreateTime() != null ? c.getCreateTime().toString() : null;
        return v;
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public String getUnit() { return unit; }
    public String getCurrency() { return currency; }
    public String getOwnerRole() { return ownerRole; }
    public String getDimensionJson() { return dimensionJson; }
    public String getCurrentVersionNo() { return currentVersionNo; }
    public String getCreateBy() { return createBy; }
    public String getCreateTime() { return createTime; }
}
