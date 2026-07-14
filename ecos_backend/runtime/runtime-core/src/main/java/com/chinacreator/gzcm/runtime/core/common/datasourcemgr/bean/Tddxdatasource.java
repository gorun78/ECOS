package com.chinacreator.gzcm.runtime.core.common.datasourcemgr.bean;

public class Tddxdatasource {
    private String ds_id;
    private String center_data_falg;
    private String center_ds_id;
    private String dsWSDLAddress;
    private String targetNameSpace;
    private String datasourceName;
    private String datasourceType;

    public String getDs_id() { return ds_id; }
    public void setDs_id(String ds_id) { this.ds_id = ds_id; }
    
    public String getCenter_data_falg() { return center_data_falg; }
    public void setCenter_data_falg(String center_data_falg) { this.center_data_falg = center_data_falg; }
    
    public String getCenter_ds_id() { return center_ds_id; }
    public void setCenter_ds_id(String center_ds_id) { this.center_ds_id = center_ds_id; }

    public String getDsWSDLAddress() { return dsWSDLAddress; }
    public void setDsWSDLAddress(String dsWSDLAddress) { this.dsWSDLAddress = dsWSDLAddress; }

    public String getTargetNameSpace() { return targetNameSpace; }
    public void setTargetNameSpace(String targetNameSpace) { this.targetNameSpace = targetNameSpace; }

    public String getDatasourceName() { return datasourceName; }
    public void setDatasourceName(String datasourceName) { this.datasourceName = datasourceName; }

    public String getDatasourceType() { return datasourceType; }
    public void setDatasourceType(String datasourceType) { this.datasourceType = datasourceType; }
    
    private String ds_name;
    
    public String getDs_name() {
        return ds_name;
    }
    
    public void setDs_name(String ds_name) {
        this.ds_name = ds_name;
    }
    
    private String db_type;
    
    public String getDb_type() {
        return db_type;
    }
    
    public void setDb_type(String db_type) {
        this.db_type = db_type;
    }
    
    private String ds_type;
    
    public String getDs_type() {
        return ds_type;
    }
    
    public void setDs_type(String ds_type) {
        this.ds_type = ds_type;
    }
    
    private String db_type_name;
    
    public String getDb_type_name() {
        return db_type_name;
    }
    
    public void setDb_type_name(String db_type_name) {
        this.db_type_name = db_type_name;
    }
    
    private String node_id;
    
    public String getNode_id() {
        return node_id;
    }
    
    public void setNode_id(String node_id) {
        this.node_id = node_id;
    }
    
    private String query_type;
    
    public String getQuery_type() {
        return query_type;
    }
    
    public void setQuery_type(String query_type) {
        this.query_type = query_type;
    }
    
    private String hostname;
    
    public String getHostname() {
        return hostname;
    }
    
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }
    
    private String db_port;
    
    public String getDb_port() {
        return db_port;
    }
    
    public void setDb_port(String db_port) {
        this.db_port = db_port;
    }
    
    private String database_name;
    
    public String getDatabase_name() {
        return database_name;
    }
    
    public void setDatabase_name(String database_name) {
        this.database_name = database_name;
    }
    
    private String username;
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    private String password;
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    private String def_schema;
    
    public String getDef_schema() {
        return def_schema;
    }
    
    public void setDef_schema(String def_schema) {
        this.def_schema = def_schema;
    }
    
    private String jdbc_url;
    
    public String getJdbc_url() {
        return jdbc_url;
    }
    
    public void setJdbc_url(String jdbc_url) {
        this.jdbc_url = jdbc_url;
    }
    
    private String authentication_mechanism;
    private String isUseSSLSocket;
    
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
    
    private String org_id;
    
    public String getOrg_id() {
        return org_id;
    }
    
    public void setOrg_id(String org_id) {
        this.org_id = org_id;
    }
}
