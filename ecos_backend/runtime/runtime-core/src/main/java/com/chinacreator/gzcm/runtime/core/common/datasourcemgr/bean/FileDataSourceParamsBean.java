package com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean;

import java.io.Serializable;

/**
 * FileDataSourceParamsBean class
 * TODO: Add proper implementation based on actual requirements
 */
public class FileDataSourceParamsBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String dsFolder;
    
    public String getDsFolder() {
        return dsFolder;
    }
    
    public void setDsFolder(String dsFolder) {
        this.dsFolder = dsFolder;
    }
    
    private String loginMethod;
    
    public String getLoginMethod() {
        return loginMethod;
    }
    
    public void setLoginMethod(String loginMethod) {
        this.loginMethod = loginMethod;
    }
    
    private String krbFilePath;
    
    public String getKrbFilePath() {
        return krbFilePath;
    }
    
    public void setKrbFilePath(String krbFilePath) {
        this.krbFilePath = krbFilePath;
    }
    
    private String keytabFilePath;
    
    public String getKeytabFilePath() {
        return keytabFilePath;
    }
    
    public void setKeytabFilePath(String keytabFilePath) {
        this.keytabFilePath = keytabFilePath;
    }
    
    private String dsClearFileWay;
    
    public String getDsClearFileWay() {
        return dsClearFileWay;
    }
    
    public void setDsClearFileWay(String dsClearFileWay) {
        this.dsClearFileWay = dsClearFileWay;
    }
    
    private String dsRemoveFolder;
    
    public String getDsRemoveFolder() {
        return dsRemoveFolder;
    }
    
    public void setDsRemoveFolder(String dsRemoveFolder) {
        this.dsRemoveFolder = dsRemoveFolder;
    }
}

