package com.chinacreator.gzcm.sysman.compliance.report;

import com.chinacreator.gzcm.sysman.compliance.scan.ComplianceScanService.ScanResult;

import java.util.List;
import java.util.Map;

/**
 * 合规报告服务接口
 * 提供合规扫描报告生成、查询和导出功能
 */
public interface ComplianceReportService {
    
    /**
     * 生成合规报告
     * 
     * @param scanResult 扫描结果
     * @param reportConfig 报告配置
     * @return 合规报告
     * @throws ComplianceReportException 生成失败
     */
    ComplianceReport generateReport(ScanResult scanResult, ReportConfig reportConfig) throws ComplianceReportException;
    
    /**
     * 查询合规报告
     * 
     * @param reportId 报告ID
     * @return 合规报告
     * @throws ComplianceReportException 查询失败
     */
    ComplianceReport getReport(String reportId) throws ComplianceReportException;
    
    /**
     * 查询合规报告列表
     * 
     * @param filter 过滤条件
     * @return 合规报告列表
     * @throws ComplianceReportException 查询失败
     */
    List<ComplianceReport> listReports(Map<String, Object> filter) throws ComplianceReportException;
    
    /**
     * 导出合规报告
     * 
     * @param reportId 报告ID
     * @param format 导出格式（PDF, EXCEL, HTML）
     * @return 报告文件内容（字节数组）
     * @throws ComplianceReportException 导出失败
     */
    byte[] exportReport(String reportId, String format) throws ComplianceReportException;
    
    /**
     * 合规报告异常
     */
    class ComplianceReportException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public ComplianceReportException(String message) {
            super(message);
        }
        
        public ComplianceReportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * 报告配置
     */
    class ReportConfig {
        private String title;
        private boolean includeDetails; // 是否包含详细信息
        private boolean includeRecommendations; // 是否包含改进建议
        private Map<String, Object> options; // 其他选项
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public boolean isIncludeDetails() { return includeDetails; }
        public void setIncludeDetails(boolean includeDetails) { this.includeDetails = includeDetails; }
        public boolean isIncludeRecommendations() { return includeRecommendations; }
        public void setIncludeRecommendations(boolean includeRecommendations) { 
            this.includeRecommendations = includeRecommendations; 
        }
        public Map<String, Object> getOptions() { return options; }
        public void setOptions(Map<String, Object> options) { this.options = options; }
    }
    
    /**
     * 合规报告
     */
    class ComplianceReport {
        private String reportId;
        private String scanId;
        private String title;
        private long generateTime;
        private ReportSummary summary; // 报告摘要
        private List<ReportSection> sections; // 报告章节
        private List<String> recommendations; // 改进建议
        
        public String getReportId() { return reportId; }
        public void setReportId(String reportId) { this.reportId = reportId; }
        public String getScanId() { return scanId; }
        public void setScanId(String scanId) { this.scanId = scanId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public long getGenerateTime() { return generateTime; }
        public void setGenerateTime(long generateTime) { this.generateTime = generateTime; }
        public ReportSummary getSummary() { return summary; }
        public void setSummary(ReportSummary summary) { this.summary = summary; }
        public List<ReportSection> getSections() { return sections; }
        public void setSections(List<ReportSection> sections) { this.sections = sections; }
        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }
    }
    
    /**
     * 报告摘要
     */
    class ReportSummary {
        private int totalIssues;
        private int criticalIssues;
        private int highIssues;
        private int mediumIssues;
        private int lowIssues;
        private double complianceScore; // 合规分数（0.0-1.0）
        
        public int getTotalIssues() { return totalIssues; }
        public void setTotalIssues(int totalIssues) { this.totalIssues = totalIssues; }
        public int getCriticalIssues() { return criticalIssues; }
        public void setCriticalIssues(int criticalIssues) { this.criticalIssues = criticalIssues; }
        public int getHighIssues() { return highIssues; }
        public void setHighIssues(int highIssues) { this.highIssues = highIssues; }
        public int getMediumIssues() { return mediumIssues; }
        public void setMediumIssues(int mediumIssues) { this.mediumIssues = mediumIssues; }
        public int getLowIssues() { return lowIssues; }
        public void setLowIssues(int lowIssues) { this.lowIssues = lowIssues; }
        public double getComplianceScore() { return complianceScore; }
        public void setComplianceScore(double complianceScore) { this.complianceScore = complianceScore; }
    }
    
    /**
     * 报告章节
     */
    class ReportSection {
        private String sectionTitle;
        private String sectionType; // SUMMARY, ISSUES, STATISTICS, RECOMMENDATIONS
        private Map<String, Object> content; // 章节内容
        
        public String getSectionTitle() { return sectionTitle; }
        public void setSectionTitle(String sectionTitle) { this.sectionTitle = sectionTitle; }
        public String getSectionType() { return sectionType; }
        public void setSectionType(String sectionType) { this.sectionType = sectionType; }
        public Map<String, Object> getContent() { return content; }
        public void setContent(Map<String, Object> content) { this.content = content; }
    }
}
