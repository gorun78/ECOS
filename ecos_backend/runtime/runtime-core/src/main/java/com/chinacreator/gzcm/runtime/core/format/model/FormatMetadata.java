package com.chinacreator.gzcm.runtime.core.format.model;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * 鏍煎紡鍏冩暟鎹?
 * 鎻忚堪鏁版嵁鏍煎紡鐨勫厓淇℃伅
 * 
 * @author CDRC Runtime Team
 */
public class FormatMetadata {
    
    /**
     * 鏍煎紡绫诲瀷
     */
    private com.chinacreator.gzcm.runtime.core.format.Format format;
    
    /**
     * 瀛楃缂栫爜
     */
    private String encoding;
    
    /**
     * 鍒嗛殧绗︼紙CSV鏍煎紡锛?
     */
    private String delimiter;
    
    /**
     * 鍘嬬缉绠楁硶
     */
    private FormatContext.CompressionType compression;
    
    /**
     * Schema瀹氫箟锛圓vro/Parquet鏍煎紡锛?
     */
    private String schema;
    
    /**
     * Schema鐗堟湰锛堟敮鎸丼chema婕旇繘锛?
     */
    private String schemaVersion;
    
    /**
     * 琛屾暟锛堝鏋滃凡鐭ワ級
     */
    private Long rowCount;
    
    /**
     * 鍒楁暟锛堝鏋滃凡鐭ワ級
     */
    private Integer columnCount;
    
    /**
     * 鏂囦欢澶у皬锛堝瓧鑺傦級
     */
    private Long fileSize;
    
    /**
     * 鍒涘缓鏃堕棿
     */
    private java.util.Date createdAt;
    
    /**
     * 鎵╁睍灞炴€?
     */
    private Map<String, Object> properties;
    
    // Getters and Setters
    
    public com.chinacreator.gzcm.runtime.core.format.Format getFormat() {
        return format;
    }
    
    public void setFormat(com.chinacreator.gzcm.runtime.core.format.Format format) {
        this.format = format;
    }
    
    public String getEncoding() {
        return encoding != null ? encoding : "UTF-8";
    }
    
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }
    
    public String getDelimiter() {
        return delimiter;
    }
    
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }
    
    public FormatContext.CompressionType getCompression() {
        return compression != null ? compression : FormatContext.CompressionType.NONE;
    }
    
    public void setCompression(FormatContext.CompressionType compression) {
        this.compression = compression;
    }
    
    public String getSchema() {
        return schema;
    }
    
    public void setSchema(String schema) {
        this.schema = schema;
    }
    
    public String getSchemaVersion() {
        return schemaVersion;
    }
    
    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
    
    public Long getRowCount() {
        return rowCount;
    }
    
    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }
    
    public Integer getColumnCount() {
        return columnCount;
    }
    
    public void setColumnCount(Integer columnCount) {
        this.columnCount = columnCount;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public java.util.Date getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(java.util.Date createdAt) {
        this.createdAt = createdAt;
    }
    
    public Map<String, Object> getProperties() {
        return properties;
    }
    
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }
    
    /**
     * 鑾峰彇鎵╁睍灞炴€?
     */
    public Object getProperty(String key) {
        return properties != null ? properties.get(key) : null;
    }
    
    /**
     * 璁剧疆鎵╁睍灞炴€?
     */
    public void setProperty(String key, Object value) {
        if (properties == null) {
            properties = new java.util.HashMap<>();
        }
        properties.put(key, value);
    }
}

