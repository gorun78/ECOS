package com.chinacreator.gzcm.runtime.core.format.model;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * 鏍煎紡涓婁笅鏂?
 * 鍖呭惈鏍煎紡杞崲鎵€闇€鐨勯厤缃俊鎭?
 * 
 * @author CDRC Runtime Team
 */
public class FormatContext {
    
    /**
     * 瀛楃缂栫爜锛堝UTF-8銆丟BK绛夛級
     */
    private Charset encoding;
    
    /**
     * 鍒嗛殧绗︼紙CSV鏍煎紡浣跨敤锛?
     */
    private String delimiter;
    
    /**
     * 鏄惁鍖呭惈琛ㄥご锛圕SV鏍煎紡浣跨敤锛?
     */
    private Boolean hasHeader;
    
    /**
     * 寮曞彿瀛楃锛圕SV鏍煎紡浣跨敤锛?
     */
    private Character quoteChar;
    
    /**
     * 杞箟瀛楃锛圕SV鏍煎紡浣跨敤锛?
     */
    private Character escapeChar;
    
    /**
     * 鍘嬬缉绠楁硶锛堝鏋滀娇鐢ㄥ帇缂╋級
     */
    private CompressionType compression;
    
    /**
     * Schema瀹氫箟锛圓vro/Parquet鏍煎紡浣跨敤锛?
     */
    private String schema;
    
    /**
     * 鎵╁睍灞炴€?
     */
    private Map<String, Object> properties;
    
    /**
     * 鍘嬬缉绫诲瀷鏋氫妇
     */
    public enum CompressionType {
        NONE("鏃犲帇缂?"),
        GZIP("GZIP鍘嬬缉"),
        SNAPPY("Snappy鍘嬬缉"),
        LZ4("LZ4鍘嬬缉"),
        ZSTANDARD("ZStandard鍘嬬缉"),
        BZIP2("BZIP2鍘嬬缉");
        
        private final String description;
        
        CompressionType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // Getters and Setters
    
    public Charset getEncoding() {
        return encoding != null ? encoding : Charset.forName("UTF-8");
    }
    
    public void setEncoding(Charset encoding) {
        this.encoding = encoding;
    }
    
    public void setEncoding(String encoding) {
        this.encoding = Charset.forName(encoding);
    }
    
    public String getDelimiter() {
        return delimiter != null ? delimiter : ",";
    }
    
    public void setDelimiter(String delimiter) {
        this.delimiter = delimiter;
    }
    
    public Boolean getHasHeader() {
        return hasHeader != null ? hasHeader : true;
    }
    
    public void setHasHeader(Boolean hasHeader) {
        this.hasHeader = hasHeader;
    }
    
    public Character getQuoteChar() {
        return quoteChar != null ? quoteChar : '"';
    }
    
    public void setQuoteChar(Character quoteChar) {
        this.quoteChar = quoteChar;
    }
    
    public Character getEscapeChar() {
        return escapeChar != null ? escapeChar : '\\';
    }
    
    public void setEscapeChar(Character escapeChar) {
        this.escapeChar = escapeChar;
    }
    
    public CompressionType getCompression() {
        return compression != null ? compression : CompressionType.NONE;
    }
    
    public void setCompression(CompressionType compression) {
        this.compression = compression;
    }
    
    public String getSchema() {
        return schema;
    }
    
    public void setSchema(String schema) {
        this.schema = schema;
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

