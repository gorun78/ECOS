package com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean;

/**
 * WSDL DataSource Bean
 * 
 * 用于表示基于 WSDL 的数据源配置
 */
public class WSDLDataSourceBean {
    private String wsdlUrl;
    private String targetNamespace;
    private String serviceName;
    private String portName;
    private String operationName;
    
    public String getWsdlUrl() {
        return wsdlUrl;
    }
    
    public void setWsdlUrl(String wsdlUrl) {
        this.wsdlUrl = wsdlUrl;
    }
    
    public String getTargetNamespace() {
        return targetNamespace;
    }
    
    public void setTargetNamespace(String targetNamespace) {
        this.targetNamespace = targetNamespace;
    }
    
    public String getServiceName() {
        return serviceName;
    }
    
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    
    public String getPortName() {
        return portName;
    }
    
    public void setPortName(String portName) {
        this.portName = portName;
    }
    
    public String getOperationName() {
        return operationName;
    }
    
    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }
    
    // Additional fields for compatibility
    private String dsWSDLAddress;
    private String node_id;
    private String ds_id;
    private String ds_name;
    
    public String getDsWSDLAddress() {
        return dsWSDLAddress != null ? dsWSDLAddress : wsdlUrl;
    }
    
    public void setDsWSDLAddress(String dsWSDLAddress) {
        this.dsWSDLAddress = dsWSDLAddress;
        this.wsdlUrl = dsWSDLAddress;
    }
    
    public String getTargetNameSpace() {
        return targetNamespace;
    }
    
    public void setTargetNameSpace(String targetNameSpace) {
        this.targetNamespace = targetNameSpace;
    }
    
    public String getNode_id() {
        return node_id;
    }
    
    public void setNode_id(String node_id) {
        this.node_id = node_id;
    }
    
    public String getDs_id() {
        return ds_id;
    }
    
    public void setDs_id(String ds_id) {
        this.ds_id = ds_id;
    }
    
    public String getDs_name() {
        return ds_name;
    }
    
    public void setDs_name(String ds_name) {
        this.ds_name = ds_name;
    }
}
