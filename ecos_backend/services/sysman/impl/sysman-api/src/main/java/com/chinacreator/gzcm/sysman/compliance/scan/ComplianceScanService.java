package com.chinacreator.gzcm.sysman.compliance.scan;

import java.util.List;
import java.util.Map;

/**
 * 合规扫描服务接口
 * 提供合规配置扫描功能，检查数据分级分类、数据驻留策略、访问控制策略、审计日志配置等
 */
public interface ComplianceScanService {
    
    /**
     * 执行合规扫描
     * 
     * @param scanConfig 扫描配置
     * @return 扫描结果
     * @throws ComplianceScanException 扫描失败
     */
    ScanResult scan(ScanConfig scanConfig) throws ComplianceScanException;
    
    /**
     * 查询扫描结果
     * 
     * @param scanId 扫描ID
     * @return 扫描结果
     * @throws ComplianceScanException 查询失败
     */
    ScanResult getScanResult(String scanId) throws ComplianceScanException;
    
    /**
     * 查询扫描历史
     * 
     * @param filter 过滤条件
     * @return 扫描结果列表
     * @throws ComplianceScanException 查询失败
     */
    List<ScanResult> listScanHistory(Map<String, Object> filter) throws ComplianceScanException;
    
    /**
     * 合规扫描异常
     */
    class ComplianceScanException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public ComplianceScanException(String message) {
            super(message);
        }
        
        public ComplianceScanException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * 扫描配置
     */
    class ScanConfig {
        private List<String> scanTypes; // CLASSIFICATION, RESIDENCY, ACCESS_CONTROL, AUDIT_LOG
        private List<String> resourceTypes; // 资源类型过滤
        private Map<String, Object> options; // 其他选项
        
        public List<String> getScanTypes() { return scanTypes; }
        public void setScanTypes(List<String> scanTypes) { this.scanTypes = scanTypes; }
        public List<String> getResourceTypes() { return resourceTypes; }
        public void setResourceTypes(List<String> resourceTypes) { this.resourceTypes = resourceTypes; }
        public Map<String, Object> getOptions() { return options; }
        public void setOptions(Map<String, Object> options) { this.options = options; }
    }
    
    /**
     * 扫描结果
     */
    class ScanResult {
        private String scanId;
        private long scanTime;
        private String status; // RUNNING, COMPLETED, FAILED
        private List<ScanIssue> issues; // 发现的问题
        private ScanSummary summary; // 扫描摘要
        
        public String getScanId() { return scanId; }
        public void setScanId(String scanId) { this.scanId = scanId; }
        public long getScanTime() { return scanTime; }
        public void setScanTime(long scanTime) { this.scanTime = scanTime; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public List<ScanIssue> getIssues() { return issues; }
        public void setIssues(List<ScanIssue> issues) { this.issues = issues; }
        public ScanSummary getSummary() { return summary; }
        public void setSummary(ScanSummary summary) { this.summary = summary; }
    }
    
    /**
     * 扫描问题
     */
    class ScanIssue {
        private String issueId;
        private String issueType; // MISSING_CLASSIFICATION, RESIDENCY_VIOLATION, ACCESS_CONTROL_GAP等
        private String severity; // CRITICAL, HIGH, MEDIUM, LOW
        private String resourceId;
        private String resourceType;
        private String description;
        private String recommendation; // 改进建议
        
        public String getIssueId() { return issueId; }
        public void setIssueId(String issueId) { this.issueId = issueId; }
        public String getIssueType() { return issueType; }
        public void setIssueType(String issueType) { this.issueType = issueType; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getRecommendation() { return recommendation; }
        public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    }
    
    /**
     * 扫描摘要
     */
    class ScanSummary {
        private int totalResources;
        private int scannedResources;
        private int issuesFound;
        private Map<String, Integer> issueCountByType; // 按类型统计问题数量
        private Map<String, Integer> issueCountBySeverity; // 按严重程度统计问题数量
        
        public int getTotalResources() { return totalResources; }
        public void setTotalResources(int totalResources) { this.totalResources = totalResources; }
        public int getScannedResources() { return scannedResources; }
        public void setScannedResources(int scannedResources) { this.scannedResources = scannedResources; }
        public int getIssuesFound() { return issuesFound; }
        public void setIssuesFound(int issuesFound) { this.issuesFound = issuesFound; }
        public Map<String, Integer> getIssueCountByType() { return issueCountByType; }
        public void setIssueCountByType(Map<String, Integer> issueCountByType) { this.issueCountByType = issueCountByType; }
        public Map<String, Integer> getIssueCountBySeverity() { return issueCountBySeverity; }
        public void setIssueCountBySeverity(Map<String, Integer> issueCountBySeverity) { 
            this.issueCountBySeverity = issueCountBySeverity; 
        }
    }
}
