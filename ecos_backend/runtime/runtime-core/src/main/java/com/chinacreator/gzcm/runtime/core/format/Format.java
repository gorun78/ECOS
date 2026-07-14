package com.chinacreator.gzcm.runtime.core.format;

/**
 * 鏁版嵁鏍煎紡鏋氫妇
 * 
 * @author CDRC Runtime Team
 */
public enum Format {
    
    // ==================== 缁撴瀯鍖栨牸寮?====================
    
    /**
     * CSV格式
     */
    CSV("CSV", "逗号分隔值", FormatCategory.STRUCTURED, false, false, true),
    
    /**
     * JSON格式
     */
    JSON("JSON", "JavaScript对象表示法", FormatCategory.STRUCTURED, true, false, false),
    
    /**
     * XML鏍煎紡
     */
    XML("XML", "鍙墿灞曟爣璁拌瑷€", FormatCategory.STRUCTURED, true, false, false),
    
    /**
     * Avro格式
     */
    AVRO("Avro", "Apache Avro二进制格式", FormatCategory.STRUCTURED, true, true, true),
    
    /**
     * Parquet鏍煎紡
     */
    PARQUET("Parquet", "Apache Parquet鍒楀紡瀛樺偍鏍煎紡", FormatCategory.STRUCTURED, true, true, true),
    
    /**
     * ORC鏍煎紡
     */
    ORC("ORC", "Apache ORC鍒楀紡瀛樺偍鏍煎紡", FormatCategory.STRUCTURED, true, true, true),
    
    // ==================== 鍘嬬缉鏍煎紡 ====================
    
    /**
     * GZIP鍘嬬缉
     */
    GZIP("GZIP", "GZIP鍘嬬缉鏍煎紡", FormatCategory.COMPRESSION, false, false, false),
    
    /**
     * Snappy鍘嬬缉
     */
    SNAPPY("Snappy", "Snappy鍘嬬缉鏍煎紡", FormatCategory.COMPRESSION, false, false, false),
    
    /**
     * LZ4鍘嬬缉
     */
    LZ4("LZ4", "LZ4鍘嬬缉鏍煎紡", FormatCategory.COMPRESSION, false, false, false),
    
    /**
     * ZStandard鍘嬬缉
     */
    ZSTANDARD("ZStandard", "ZStandard鍘嬬缉鏍煎紡", FormatCategory.COMPRESSION, false, false, false),
    
    /**
     * BZIP2鍘嬬缉
     */
    BZIP2("BZIP2", "BZIP2鍘嬬缉鏍煎紡", FormatCategory.COMPRESSION, false, false, false);
    
    /**
     * 鏍煎紡绫诲埆
     */
    public enum FormatCategory {
        STRUCTURED("缁撴瀯鍖栨牸寮?"),
        COMPRESSION("鍘嬬缉鏍煎紡");
        
        private final String description;
        
        FormatCategory(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    private final String name;
    private final String description;
    private final FormatCategory category;
    private final boolean supportsStreaming;
    private final boolean supportsSchemaEvolution;
    private final boolean supportsCompression;
    
    Format(String name, String description, FormatCategory category, 
           boolean supportsStreaming, boolean supportsSchemaEvolution, boolean supportsCompression) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.supportsStreaming = supportsStreaming;
        this.supportsSchemaEvolution = supportsSchemaEvolution;
        this.supportsCompression = supportsCompression;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public FormatCategory getCategory() {
        return category;
    }
    
    public boolean supportsStreaming() {
        return supportsStreaming;
    }
    
    public boolean supportsSchemaEvolution() {
        return supportsSchemaEvolution;
    }
    
    public boolean supportsCompression() {
        return supportsCompression;
    }
    
    /**
     * 鍒ゆ柇鏄惁涓虹粨鏋勫寲鏍煎紡
     */
    public boolean isStructured() {
        return category == FormatCategory.STRUCTURED;
    }
    
    /**
     * 鍒ゆ柇鏄惁涓哄帇缂╂牸寮?
     */
    public boolean isCompression() {
        return category == FormatCategory.COMPRESSION;
    }
    
    /**
     * 鏍规嵁瀛楃涓茶幏鍙栨牸寮?
     */
    public static Format fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        
        try {
            return Format.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 灏濊瘯妯＄硦鍖归厤
            for (Format format : Format.values()) {
                if (format.name().equalsIgnoreCase(value) || 
                    format.getName().equalsIgnoreCase(value)) {
                    return format;
                }
            }
            return null;
        }
    }
}

