package com.chinacreator.gzcm.runtime.core.dataaccess.storage.enums;

/**
 * 瀛樺偍绫诲瀷鏋氫妇
 * 
 * @author CDRC Runtime Team
 */
public enum StorageType {
    
    /**
     * MySQL鏁版嵁搴?
     */
    MYSQL("MySQL", "鍏崇郴鍨嬫暟鎹簱"),
    
    /**
     * PostgreSQL鏁版嵁搴?
     */
    POSTGRESQL("PostgreSQL", "鍏崇郴鍨嬫暟鎹簱"),
    
    /**
     * Oracle鏁版嵁搴?
     */
    ORACLE("Oracle", "鍏崇郴鍨嬫暟鎹簱"),
    
    /**
     * SQL Server鏁版嵁搴?
     */
    SQLSERVER("SQL Server", "鍏崇郴鍨嬫暟鎹簱"),
    
    /**
     * ClickHouse鏁版嵁搴?
     */
    CLICKHOUSE("ClickHouse", "OLAP鏁版嵁搴?"),
    
    /**
     * Apache Doris鏁版嵁搴?
     */
    DORIS("Apache Doris", "OLAP鏁版嵁搴?"),
    
    /**
     * StarRocks鏁版嵁搴?
     */
    STARROCKS("StarRocks", "OLAP鏁版嵁搴?"),
    
    /**
     * Elasticsearch
     */
    ELASTICSEARCH("Elasticsearch", "鎼滅储寮曟搸"),
    
    /**
     * MongoDB
     */
    MONGODB("MongoDB", "鏂囨。鏁版嵁搴?"),
    
    /**
     * Amazon S3
     */
    S3("Amazon S3", "瀵硅薄瀛樺偍"),
    
    /**
     * 闃块噷浜慜SS
     */
    OSS("Aliyun OSS", "瀵硅薄瀛樺偍"),
    
    /**
     * MinIO
     */
    MINIO("MinIO", "瀵硅薄瀛樺偍"),
    
    /**
     * HDFS
     */
    HDFS("HDFS", "鍒嗗竷寮忔枃浠剁郴缁?"),
    
    /**
     * Kafka
     */
    KAFKA("Kafka", "娑堟伅闃熷垪"),
    
    /**
     * Pulsar
     */
    PULSAR("Pulsar", "娑堟伅闃熷垪"),
    
    /**
     * Redis
     */
    REDIS("Redis", "缂撳瓨鏁版嵁搴?");
    
    /**
     * 瀛樺偍绫诲瀷鍚嶇О
     */
    private final String name;
    
    /**
     * 瀛樺偍绫诲瀷鎻忚堪
     */
    private final String description;
    
    /**
     * 鏋勯€犲嚱鏁?
     */
    StorageType(String name, String description) {
        this.name = name;
        this.description = description;
    }
    
    /**
     * 鑾峰彇瀛樺偍绫诲瀷鍚嶇О
     */
    public String getName() {
        return name;
    }
    
    /**
     * 鑾峰彇瀛樺偍绫诲瀷鎻忚堪
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 鏍规嵁瀛楃涓茶幏鍙栧瓨鍌ㄧ被鍨?
     */
    public static StorageType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        try {
            return StorageType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 灏濊瘯妯＄硦鍖归厤
            for (StorageType type : StorageType.values()) {
                if (type.name().equalsIgnoreCase(value) || 
                    type.getName().equalsIgnoreCase(value)) {
                    return type;
                }
            }
            return null;
        }
    }
    
    /**
     * 鍒ゆ柇鏄惁涓哄叧绯诲瀷鏁版嵁搴?
     */
    public boolean isRelationalDatabase() {
        return this == MYSQL || this == POSTGRESQL || 
               this == ORACLE || this == SQLSERVER;
    }
    
    /**
     * 鍒ゆ柇鏄惁涓篛LAP鏁版嵁搴?
     */
    public boolean isOlapDatabase() {
        return this == CLICKHOUSE || this == DORIS || this == STARROCKS;
    }
    
    /**
     * 鍒ゆ柇鏄惁涓哄璞″瓨鍌?
     */
    public boolean isObjectStorage() {
        return this == S3 || this == OSS || this == MINIO;
    }
    
    /**
     * 鍒ゆ柇鏄惁涓烘秷鎭槦鍒?
     */
    public boolean isMessageQueue() {
        return this == KAFKA || this == PULSAR;
    }
}

