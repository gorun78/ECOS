package com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean;

import java.io.Serializable;

/**
 * HBDataSourceParams - HBase数据源参数Bean
 */
public class HBDataSourceParams implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String zookeeper_quorum;
    private String zookeeper_client_port;
    private String hbase_master;
    private String hbase_zookeeper_property_clientPort;
    
    // Getters and setters
    public String getZookeeper_quorum() {
        return zookeeper_quorum;
    }
    
    public void setZookeeper_quorum(String zookeeper_quorum) {
        this.zookeeper_quorum = zookeeper_quorum;
    }
    
    public String getZookeeper_client_port() {
        return zookeeper_client_port;
    }
    
    public void setZookeeper_client_port(String zookeeper_client_port) {
        this.zookeeper_client_port = zookeeper_client_port;
    }
    
    public String getHbase_master() {
        return hbase_master;
    }
    
    public void setHbase_master(String hbase_master) {
        this.hbase_master = hbase_master;
    }
    
    public String getHbase_zookeeper_property_clientPort() {
        return hbase_zookeeper_property_clientPort;
    }
    
    public void setHbase_zookeeper_property_clientPort(String hbase_zookeeper_property_clientPort) {
        this.hbase_zookeeper_property_clientPort = hbase_zookeeper_property_clientPort;
    }
    
    private String zkIPs;
    
    public String getZkIPs() {
        return zkIPs;
    }
    
    public void setZkIPs(String zkIPs) {
        this.zkIPs = zkIPs;
    }
    
    private String zkPort;
    
    public String getZkPort() {
        return zkPort;
    }
    
    public void setZkPort(String zkPort) {
        this.zkPort = zkPort;
    }
    
    private String zkDir;
    
    public String getZkDir() {
        return zkDir;
    }
    
    public void setZkDir(String zkDir) {
        this.zkDir = zkDir;
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
}

