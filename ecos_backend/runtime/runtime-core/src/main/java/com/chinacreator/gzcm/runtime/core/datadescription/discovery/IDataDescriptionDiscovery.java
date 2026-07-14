package com.chinacreator.gzcm.runtime.core.datadescription.discovery;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.datadescription.model.DataDescription;

/**
 * 鏁版嵁鎻忚堪鍙戠幇鏈嶅姟鎺ュ彛
 * 
 * @author CDRC Runtime Team
 */
public interface IDataDescriptionDiscovery {
    
    /**
     * 鍙戠幇鏁版嵁搴撹〃缁撴瀯
     * 
     * @param connectionInfo 鏁版嵁搴撹繛鎺ヤ俊鎭?
     * @param tableName 琛ㄥ悕锛堝彲閫夛紝濡傛灉涓虹┖鍒欏彂鐜版墍鏈夎〃锛?
     * @return 鏁版嵁鎻忚堪鍒楄〃
     * @throws Exception
     */
    List<DataDescription> discoverDatabaseTable(DatabaseConnectionInfo connectionInfo, String tableName) throws Exception;
    
    /**
     * 鍙戠幇鏂囦欢鍏冩暟鎹?
     * 
     * @param filePath 鏂囦欢璺緞
     * @return 鏁版嵁鎻忚堪瀵硅薄
     * @throws Exception
     */
    DataDescription discoverFile(String filePath) throws Exception;
    
    /**
     * 鍙戠幇API鎺ュ彛瀹氫箟
     * 
     * @param apiUrl API鏂囨。URL锛圤penAPI/Swagger锛?
     * @return 鏁版嵁鎻忚堪鍒楄〃
     * @throws Exception
     */
    List<DataDescription> discoverApi(String apiUrl) throws Exception;
    
    /**
     * 鏁版嵁搴撹繛鎺ヤ俊鎭?
     */
    class DatabaseConnectionInfo {
        private String host;
        private Integer port;
        private String database;
        private String username;
        private String password;
        private String driverClass;
        
        // Getters and Setters
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
        
        public String getDriverClass() {
            return driverClass;
        }
        
        public void setDriverClass(String driverClass) {
            this.driverClass = driverClass;
        }
        
        /**
         * 鏋勫缓JDBC URL
         */
        public String buildJdbcUrl() {
            if (driverClass == null || driverClass.isEmpty()) {
                throw new IllegalStateException("鏁版嵁搴撻┍鍔ㄧ被涓嶈兘涓虹┖");
            }
            
            // 鏍规嵁椹卞姩绫诲垽鏂暟鎹簱绫诲瀷
            if (driverClass.contains("mysql")) {
                return String.format("jdbc:mysql://%s:%d/%s", host, port, database);
            } else if (driverClass.contains("postgresql")) {
                return String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
            } else if (driverClass.contains("oracle")) {
                return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, database);
            } else {
                throw new IllegalArgumentException("涓嶆敮鎸佺殑鏁版嵁搴撶被鍨? " + driverClass);
            }
        }
    }
}

