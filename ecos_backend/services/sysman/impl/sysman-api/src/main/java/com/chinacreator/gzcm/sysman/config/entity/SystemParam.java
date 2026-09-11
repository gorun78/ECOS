package com.chinacreator.gzcm.sysman.config.entity;

import java.util.Date;

/**
 * 系统参数实体
 * 对应老系统SystemInfo表
 */
public class SystemParam {
    private String paramId; // info_id
    private String paramName; // info_name
    private String paramContent; // info_content
    private String paramDesc; // info_desc
    private String paramType; // info_type
    private Date createdTime;
    private String createdBy;
    private Date updatedTime;
    private String updatedBy;

    public String getParamId() {
        return paramId;
    }

    public void setParamId(String paramId) {
        this.paramId = paramId;
    }

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public String getParamContent() {
        return paramContent;
    }

    public void setParamContent(String paramContent) {
        this.paramContent = paramContent;
    }

    public String getParamDesc() {
        return paramDesc;
    }

    public void setParamDesc(String paramDesc) {
        this.paramDesc = paramDesc;
    }

    public String getParamType() {
        return paramType;
    }

    public void setParamType(String paramType) {
        this.paramType = paramType;
    }

    public Date getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Date createdTime) {
        this.createdTime = createdTime;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Date getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(Date updatedTime) {
        this.updatedTime = updatedTime;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    // 便捷方法：getParamValue/setParamValue 使用 paramContent
    public String getParamValue() {
        return paramContent;
    }

    public void setParamValue(String paramValue) {
        this.paramContent = paramValue;
    }
}

