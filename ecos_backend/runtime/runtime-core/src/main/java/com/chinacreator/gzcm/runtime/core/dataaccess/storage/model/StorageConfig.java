package com.chinacreator.gzcm.runtime.core.dataaccess.storage.model;

import java.util.Map;

/**
 * 瀛樺偍閰嶇疆妯″瀷
 * 
 * @author CDRC Runtime Team
 */
public class StorageConfig {
    
    /**
     * 瀛樺偍绫诲瀷锛圡YSQL, POSTGRESQL, CLICKHOUSE绛夛級
     */
    private String storageType;
    
    /**
     * 杩炴帴瀛楃涓?
     */
    private String connectionString;
    
    /**
     * 涓绘満鍦板潃
     */
    private String host;
    
    /**
     * 绔彛
     */
    private Integer port;
    
    /**
     * 鏁版嵁搴撳悕绉?
     */
    private String database;
    
    /**
     * 鐢ㄦ埛鍚?
     */
    private String username;
    
    /**
     * 瀵嗙爜
     */
    private String password;
    
    /**
     * 璁よ瘉淇℃伅锛堢敤浜庡鏉傝璇佸満鏅級
     */
    private Map<String, Object> credentials;
    
    /**
     * 鎵╁睍灞炴€?
     */
    private Map<String, Object> properties;
    
    /**
     * 杩炴帴姹犻厤缃?
     */
    private ConnectionPoolConfig connectionPool;
    
    /**
     * 杩炴帴姹犻厤缃?
     */
    public static class ConnectionPoolConfig {
        private Integer maxPoolSize = 10;
        private Integer minPoolSize = 1;
        private Long connectionTimeout = 30000L;
        private Long idleTimeout = 600000L;
        private Long maxLifetime = 1800000L;
        
        // Getters and Setters
        public Integer getMaxPoolSize() {
            return maxPoolSize;
        }
        
        public void setMaxPoolSize(Integer maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }
        
        public Integer getMinPoolSize() {
            return minPoolSize;
        }
        
        public void setMinPoolSize(Integer minPoolSize) {
            this.minPoolSize = minPoolSize;
        }
        
        public Long getConnectionTimeout() {
            return connectionTimeout;
        }
        
        public void setConnectionTimeout(Long connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }
        
        public Long getIdleTimeout() {
            return idleTimeout;
        }
        
        public void setIdleTimeout(Long idleTimeout) {
            this.idleTimeout = idleTimeout;
        }
        
        public Long getMaxLifetime() {
            return maxLifetime;
        }
        
        public void setMaxLifetime(Long maxLifetime) {
            this.maxLifetime = maxLifetime;
        }
    }
    
    // Getters and Setters
    
    public String getStorageType() {
        return storageType;
    }
    
    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }
    
    public String getConnectionString() {
        return connectionString;
    }
    
    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public Integer getPort() {
        return port;
    }
    
    public void setPort(Integer port) {
        this.port = port;
    }
    
    public String getDatabase() {
        return database;
    }
    
    public void setDatabase(String database) {
        this.database = database;
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
    
    public Map<String, Object> getCredentials() {
        return credentials;
    }
    
    public void setCredentials(Map<String, Object> credentials) {
        this.credentials = credentials;
    }
    
    public Map<String, Object> getProperties() {
        return properties;
    }
    
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
    
    public ConnectionPoolConfig getConnectionPool() {
        return connectionPool;
    }
    
    public void setConnectionPool(ConnectionPoolConfig connectionPool) {
        this.connectionPool = connectionPool;
    }
}

