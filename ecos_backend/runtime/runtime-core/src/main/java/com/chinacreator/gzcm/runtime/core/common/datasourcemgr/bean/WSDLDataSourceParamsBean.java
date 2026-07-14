package com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean;

import java.io.Serializable;

/**
 * WSDLDataSourceParamsBean class
 * TODO: Add proper implementation based on actual requirements
 */
public class WSDLDataSourceParamsBean implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String webServiceType;
    
    public String getWebServiceType() {
        return webServiceType;
    }
    
    public void setWebServiceType(String webServiceType) {
        this.webServiceType = webServiceType;
    }
    
    private String dsWSDLAddress;
    
    public String getDsWSDLAddress() {
        return dsWSDLAddress;
    }
    
    public void setDsWSDLAddress(String dsWSDLAddress) {
        this.dsWSDLAddress = dsWSDLAddress;
    }
    
    private String targetNameSpace;
    
    public String getTargetNameSpace() {
        return targetNameSpace;
    }
    
    public void setTargetNameSpace(String targetNameSpace) {
        this.targetNameSpace = targetNameSpace;
    }
}

