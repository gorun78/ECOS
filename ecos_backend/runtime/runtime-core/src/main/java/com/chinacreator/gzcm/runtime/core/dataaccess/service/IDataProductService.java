package com.chinacreator.gzcm.runtime.core.dataaccess.service;

/**
 * 鏁版嵁浜у搧鏈嶅姟鎺ュ彛锛堝崰浣嶆帴鍙ｏ級
 * 寰呬换鍔?.1.2瀹屾垚鍚庡畬鍠?
 * 
 * @author CDRC Runtime Team
 */
public interface IDataProductService {
    
    /**
     * 鏍规嵁鏁版嵁浜у搧ID鑾峰彇鏁版嵁浜у搧淇℃伅
     * 
     * @param dataProductId 鏁版嵁浜у搧ID
     * @return 鏁版嵁浜у搧淇℃伅锛堢畝鍖栫増鏈紝寰呭畬鍠勶級
     * @throws Exception
     */
    DataProductInfo getDataProduct(String dataProductId) throws Exception;
    
    /**
     * 鏁版嵁浜у搧淇℃伅锛堢畝鍖栫増鏈級
     */
    class DataProductInfo {
        private String productId;
        private String productName;
        private String storageType; // MYSQL, POSTGRESQL, ELASTICSEARCH绛?
        private String storageConfig; // 瀛樺偍閰嶇疆锛圝SON鏍煎紡锛?
        private String schema; // Schema瀹氫箟锛圝SON鏍煎紡锛?
        
        // Getters and Setters
        public String getProductId() {
            return productId;
        }
        
        public void setProductId(String productId) {
            this.productId = productId;
        }
        
        public String getProductName() {
            return productName;
        }
        
        public void setProductName(String productName) {
            this.productName = productName;
        }
        
        public String getStorageType() {
            return storageType;
        }
        
        public void setStorageType(String storageType) {
            this.storageType = storageType;
        }
        
        public String getStorageConfig() {
            return storageConfig;
        }
        
        public void setStorageConfig(String storageConfig) {
            this.storageConfig = storageConfig;
        }
        
        public String getSchema() {
            return schema;
        }
        
        public void setSchema(String schema) {
            this.schema = schema;
        }
    }
}

