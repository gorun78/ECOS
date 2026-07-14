package com.chinacreator.gzcm.sysman.dict.entity;

import java.time.LocalDateTime;

/**
 * 系统数据字典实体
 * 对应 sys_dict 表
 */
public class SysDict {

    private String id;
    private String dictType;
    private String dictCode;
    private String dictLabel;
    private String dictLabelEn;
    private int sortOrder;
    private String status;
    private String parentCode;
    private String extValue;
    private String subsystem;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public SysDict() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDictType() {
        return dictType;
    }

    public void setDictType(String dictType) {
        this.dictType = dictType;
    }

    public String getDictCode() {
        return dictCode;
    }

    public void setDictCode(String dictCode) {
        this.dictCode = dictCode;
    }

    public String getDictLabel() {
        return dictLabel;
    }

    public void setDictLabel(String dictLabel) {
        this.dictLabel = dictLabel;
    }

    public String getDictLabelEn() {
        return dictLabelEn;
    }

    public void setDictLabelEn(String dictLabelEn) {
        this.dictLabelEn = dictLabelEn;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public String getExtValue() {
        return extValue;
    }

    public void setExtValue(String extValue) {
        this.extValue = extValue;
    }

    public String getSubsystem() {
        return subsystem;
    }

    public void setSubsystem(String subsystem) {
        this.subsystem = subsystem;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
