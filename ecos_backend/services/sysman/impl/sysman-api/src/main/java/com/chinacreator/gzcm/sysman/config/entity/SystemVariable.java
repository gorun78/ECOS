package com.chinacreator.gzcm.sysman.config.entity;

/**
 * 系统变量实体
 * 对应老系统SystemVariable表
 */
public class SystemVariable {
    private String varId; // var_id
    private String scopeId; // var_scopeid
    private String varCode; // var_code
    private String scopeName; // var_scopename
    private String varDesc; // var_desc
    private String valType; // var_valtype
    private String varFormat; // var_format
    private String varValue; // var_val
    private String varStatus; // var_status (0-禁用, 1-启用)

    public String getVarId() {
        return varId;
    }

    public void setVarId(String varId) {
        this.varId = varId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getVarCode() {
        return varCode;
    }

    public void setVarCode(String varCode) {
        this.varCode = varCode;
    }

    public String getScopeName() {
        return scopeName;
    }

    public void setScopeName(String scopeName) {
        this.scopeName = scopeName;
    }

    public String getVarDesc() {
        return varDesc;
    }

    public void setVarDesc(String varDesc) {
        this.varDesc = varDesc;
    }

    public String getValType() {
        return valType;
    }

    public void setValType(String valType) {
        this.valType = valType;
    }

    public String getVarFormat() {
        return varFormat;
    }

    public void setVarFormat(String varFormat) {
        this.varFormat = varFormat;
    }

    public String getVarValue() {
        return varValue;
    }

    public void setVarValue(String varValue) {
        this.varValue = varValue;
    }

    public String getVarStatus() {
        return varStatus;
    }

    public void setVarStatus(String varStatus) {
        this.varStatus = varStatus;
    }
}

