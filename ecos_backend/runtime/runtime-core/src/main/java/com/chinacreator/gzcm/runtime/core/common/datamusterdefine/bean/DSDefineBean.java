package com.chinacreator.gzcm.runtime.core.common.datamusterdefine.bean;

import java.io.Serializable;

/**
 * DSDefineBean - 数据集定义Bean
 */
public class DSDefineBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String dsdefine_id;
    private String dset_id;
    private String dsdefine_name;
    private String changed;
    private String remark;
    
    // Getters and setters
    public String getDsdefine_id() {
        return dsdefine_id;
    }
    
    public void setDsdefine_id(String dsdefine_id) {
        this.dsdefine_id = dsdefine_id;
    }
    
    public String getDset_id() {
        return dset_id;
    }
    
    public void setDset_id(String dset_id) {
        this.dset_id = dset_id;
    }
    
    public String getDsdefine_name() {
        return dsdefine_name;
    }
    
    public void setDsdefine_name(String dsdefine_name) {
        this.dsdefine_name = dsdefine_name;
    }
    
    public String getChanged() {
        return changed;
    }
    
    public void setChanged(String changed) {
        this.changed = changed;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    private String out_dsid;
    private String mapcollectrec_id;
    private String status;
    
    public String getOut_dsid() {
        return out_dsid;
    }
    
    public void setOut_dsid(String out_dsid) {
        this.out_dsid = out_dsid;
    }
    
    public String getMapcollectrec_id() {
        return mapcollectrec_id;
    }
    
    public void setMapcollectrec_id(String mapcollectrec_id) {
        this.mapcollectrec_id = mapcollectrec_id;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    private String match_susped;
    private String save_clusre;
    private String xml;
    private java.util.List<java.util.HashMap<String, Object>> valueMapping;
    private String rec_collect_schedule_id;
    
    public String getMatch_susped() {
        return match_susped;
    }
    
    public void setMatch_susped(String match_susped) {
        this.match_susped = match_susped;
    }
    
    public String getSave_clusre() {
        return save_clusre;
    }
    
    public void setSave_clusre(String save_clusre) {
        this.save_clusre = save_clusre;
    }
    
    public String getXml() {
        return xml;
    }
    
    public void setXml(String xml) {
        this.xml = xml;
    }
    
    public java.util.List<java.util.HashMap<String, Object>> getValueMapping() {
        return valueMapping;
    }
    
    public void setValueMapping(java.util.List<java.util.HashMap<String, Object>> valueMapping) {
        this.valueMapping = valueMapping;
    }
    
    public String getRec_collect_schedule_id() {
        return rec_collect_schedule_id;
    }
    
    public void setRec_collect_schedule_id(String rec_collect_schedule_id) {
        this.rec_collect_schedule_id = rec_collect_schedule_id;
    }
}

