package com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean;

import java.io.Serializable;

/**
 * MongoDataSourceParams - MongoDB数据源参数Bean
 */
public class MongoDataSourceParams implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String authentication_mechanism;
    private String isUseSSLSocket;
    private String username;
    private String password;
    private String database;
    
    // Getters and setters
    public String getAuthenticationMechanism() {
        return authentication_mechanism;
    }
    
    public void setAuthenticationMechanism(String authentication_mechanism) {
        this.authentication_mechanism = authentication_mechanism;
    }
    
    public String getIsUseSSLSocket() {
        return isUseSSLSocket;
    }
    
    public void setIsUseSSLSocket(String isUseSSLSocket) {
        this.isUseSSLSocket = isUseSSLSocket;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getDatabase() {
        return database;
    }
    
    public void setDatabase(String database) {
        this.database = database;
    }
}

