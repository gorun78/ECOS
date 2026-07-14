package com.chinacreator.gzcm.runtime.core.common.sysvar.bean;

public class SystemVariable {
    private String varName;
    private String varValue;
    private String var_valtype;
    private String var_format;

    public String getVarName() { return varName; }
    public void setVarName(String varName) { this.varName = varName; }

    public String getVarValue() { return varValue; }
    public void setVarValue(String varValue) { this.varValue = varValue; }
    
    // Additional getters for compatibility
    public String getVar_valtype() {
        return var_valtype;
    }
    
    public void setVar_valtype(String var_valtype) {
        this.var_valtype = var_valtype;
    }
    
    public String getVar_val() {
        return varValue;
    }
    
    public void setVar_val(String var_val) {
        this.varValue = var_val;
    }
    
    public String getVar_format() {
        return var_format;
    }
    
    public void setVar_format(String var_format) {
        this.var_format = var_format;
    }
}
