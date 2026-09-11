package com.chinacreator.gzcm.sysman.compliance.classification;

import java.util.List;
import java.util.Map;

/**
 * 数据分级分类服务接口
 * 提供数据分级分类功能，包括自动分级分类、查询和统计
 */
public interface DataClassificationService {
    
    /**
     * 对资源进行分级分类
     * 
     * @param resourceId 资源ID
     * @param resourceType 资源类型（TABLE, COLUMN, FILE等）
     * @param resourceMetadata 资源元数据（表名、字段名、内容等）
     * @return 分级分类结果
     * @throws ClassificationException 分类失败
     */
    ClassificationResult classify(String resourceId, String resourceType, 
                                 Map<String, Object> resourceMetadata) throws ClassificationException;
    
    /**
     * 批量分级分类
     * 
     * @param resources 资源列表（每个资源包含resourceId, resourceType, metadata）
     * @return 分级分类结果列表
     * @throws ClassificationException 分类失败
     */
    List<ClassificationResult> batchClassify(List<Map<String, Object>> resources) throws ClassificationException;
    
    /**
     * 查询资源的分级分类信息
     * 
     * @param resourceId 资源ID
     * @param resourceType 资源类型
     * @return 分级分类结果
     * @throws ClassificationException 查询失败
     */
    ClassificationResult getClassification(String resourceId, String resourceType) throws ClassificationException;
    
    /**
     * 查询分级分类统计
     * 
     * @param filter 过滤条件（level, dataType, resourceType等）
     * @return 统计结果
     * @throws ClassificationException 查询失败
     */
    ClassificationStatistics getStatistics(Map<String, Object> filter) throws ClassificationException;
    
    /**
     * 更新分级分类
     * 
     * @param resourceId 资源ID
     * @param resourceType 资源类型
     * @param level 分级
     * @param dataType 分类
     * @param operator 操作者
     * @throws ClassificationException 更新失败
     */
    void updateClassification(String resourceId, String resourceType, String level, 
                              String dataType, String operator) throws ClassificationException;
    
    /**
     * 分级分类异常
     */
    class ClassificationException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public ClassificationException(String message) {
            super(message);
        }
        
        public ClassificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * 分级分类结果
     */
    class ClassificationResult {
        private String resourceId;
        private String resourceType;
        private String level; // PUBLIC, INTERNAL, SECRET, CONFIDENTIAL
        private String dataType; // PII, FINANCIAL, HEALTH, BUSINESS_SECRET等
        private Double confidence; // 置信度（自动分类时）
        private String method; // MANUAL, RULE, ML_MODEL
        private String reason; // 分类原因
        private long classifiedTime;
        
        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
        public String getLevel() { return level; }
        public void setLevel(String level) { this.level = level; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public long getClassifiedTime() { return classifiedTime; }
        public void setClassifiedTime(long classifiedTime) { this.classifiedTime = classifiedTime; }
    }
    
    /**
     * 分级分类统计
     */
    class ClassificationStatistics {
        private Map<String, Long> levelCount; // 各分级数量
        private Map<String, Long> dataTypeCount; // 各分类数量
        private Map<String, Map<String, Long>> levelDataTypeCount; // 分级×分类数量
        private long totalCount;
        
        public Map<String, Long> getLevelCount() { return levelCount; }
        public void setLevelCount(Map<String, Long> levelCount) { this.levelCount = levelCount; }
        public Map<String, Long> getDataTypeCount() { return dataTypeCount; }
        public void setDataTypeCount(Map<String, Long> dataTypeCount) { this.dataTypeCount = dataTypeCount; }
        public Map<String, Map<String, Long>> getLevelDataTypeCount() { return levelDataTypeCount; }
        public void setLevelDataTypeCount(Map<String, Map<String, Long>> levelDataTypeCount) { 
            this.levelDataTypeCount = levelDataTypeCount; 
        }
        public long getTotalCount() { return totalCount; }
        public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
    }
}
