package com.chinacreator.gzcm.runtime.core.common.dbdata.bean;

/**
 * DatasourceBean - 数据源Bean占位类
 * 用于兼容旧代码
 */
public class DatasourceBean {
    private String ds_name;
    private String username;
    private String password;
    private String driver;
    private String jdbc_url;
    private String jndi_name;
    private int maximum_size;
    private int minimum_size;
    
    public String getDs_name() {
        return ds_name;
    }
    
    public void setDs_name(String ds_name) {
        this.ds_name = ds_name;
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
    
    public String getDriver() {
        return driver;
    }
    
    public void setDriver(String driver) {
        this.driver = driver;
    }
    
    public String getJdbc_url() {
        return jdbc_url;
    }
    
    public void setJdbc_url(String jdbc_url) {
        this.jdbc_url = jdbc_url;
    }
    
    public String getJndi_name() {
        return jndi_name;
    }
    
    public void setJndi_name(String jndi_name) {
        this.jndi_name = jndi_name;
    }
    
    public int getMaximum_size() {
        return maximum_size;
    }
    
    public void setMaximum_size(int maximum_size) {
        this.maximum_size = maximum_size;
    }
    
    public int getMinimum_size() {
        return minimum_size;
    }
    
    public void setMinimum_size(int minimum_size) {
        this.minimum_size = minimum_size;
    }
    
    // Additional field for validation query
    private String validation_query;
    
    public String getValidation_query() {
        return validation_query;
    }
    
    public void setValidation_query(String validation_query) {
        this.validation_query = validation_query;
    }
    
    // Additional fields for compatibility
    private int initial_connections;
    private String connection_type;
    private String ext_jndi_name;
    
    public int getInitial_connections() {
        return initial_connections;
    }
    
    public void setInitial_connections(int initial_connections) {
        this.initial_connections = initial_connections;
    }
    
    public String getConnection_type() {
        return connection_type;
    }
    
    public void setConnection_type(String connection_type) {
        this.connection_type = connection_type;
    }
    
    public String getExt_jndi_name() {
        return ext_jndi_name;
    }
    
    public void setExt_jndi_name(String ext_jndi_name) {
        this.ext_jndi_name = ext_jndi_name;
    }
}
