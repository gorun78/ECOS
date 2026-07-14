package com.chinacreator.gzcm.runtime.core.compliance.report;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chinacreator.gzcm.sysman.compliance.scan.ComplianceScanService.ScanIssue;
import com.chinacreator.gzcm.sysman.compliance.scan.ComplianceScanService.ScanResult;
import com.chinacreator.gzcm.sysman.compliance.report.ComplianceReportService;

/**
 * 合规报告服务实现
 * 提供合规扫描报告生成、查询和导出功能
 */
public class ComplianceReportServiceImpl implements ComplianceReportService {
    
    private static final Logger logger = LoggerFactory.getLogger(ComplianceReportServiceImpl.class);
    
    // 内存存储（实际应使用数据库）
    private final Map<String, ComplianceReportService.ComplianceReport> reportStore = new ConcurrentHashMap<>();
    
    @Override
    public ComplianceReportService.ComplianceReport generateReport(ScanResult scanResult, 
                                                                    ComplianceReportService.ReportConfig reportConfig) 
            throws ComplianceReportException {
        if (scanResult == null) {
            throw new ComplianceReportException("扫描结果不能为空");
        }
        
        try {
            String reportId = UUID.randomUUID().toString();
            ComplianceReportService.ComplianceReport report = new ComplianceReportService.ComplianceReport();
            report.setReportId(reportId);
            report.setScanId(scanResult.getScanId());
            report.setTitle(reportConfig != null && reportConfig.getTitle() != null ? 
                reportConfig.getTitle() : "合规扫描报告");
            report.setGenerateTime(System.currentTimeMillis());
            
            // 生成摘要
            ComplianceReportService.ReportSummary summary = generateSummary(scanResult);
            report.setSummary(summary);
            
            // 生成章节
            List<ComplianceReportService.ReportSection> sections = new ArrayList<>();
            
            // 摘要章节
            ComplianceReportService.ReportSection summarySection = new ComplianceReportService.ReportSection();
            summarySection.setSectionTitle("执行摘要");
            summarySection.setSectionType("SUMMARY");
            Map<String, Object> summaryContent = new HashMap<>();
            summaryContent.put("totalIssues", summary.getTotalIssues());
            summaryContent.put("complianceScore", summary.getComplianceScore());
            summarySection.setContent(summaryContent);
            sections.add(summarySection);
            
            // 问题章节
            if (reportConfig == null || reportConfig.isIncludeDetails()) {
                ComplianceReportService.ReportSection issuesSection = new ComplianceReportService.ReportSection();
                issuesSection.setSectionTitle("发现的问题");
                issuesSection.setSectionType("ISSUES");
                Map<String, Object> issuesContent = new HashMap<>();
                issuesContent.put("issues", scanResult.getIssues());
                issuesSection.setContent(issuesContent);
                sections.add(issuesSection);
            }
            
            // 统计章节
            if (scanResult.getSummary() != null) {
                ComplianceReportService.ReportSection statisticsSection = new ComplianceReportService.ReportSection();
                statisticsSection.setSectionTitle("统计信息");
                statisticsSection.setSectionType("STATISTICS");
                Map<String, Object> statisticsContent = new HashMap<>();
                statisticsContent.put("summary", scanResult.getSummary());
                statisticsSection.setContent(statisticsContent);
                sections.add(statisticsSection);
            }
            
            // 改进建议章节
            if (reportConfig == null || reportConfig.isIncludeRecommendations()) {
                List<String> recommendations = extractRecommendations(scanResult.getIssues());
                report.setRecommendations(recommendations);
                
                ComplianceReportService.ReportSection recommendationsSection = new ComplianceReportService.ReportSection();
                recommendationsSection.setSectionTitle("改进建议");
                recommendationsSection.setSectionType("RECOMMENDATIONS");
                Map<String, Object> recommendationsContent = new HashMap<>();
                recommendationsContent.put("recommendations", recommendations);
                recommendationsSection.setContent(recommendationsContent);
                sections.add(recommendationsSection);
            }
            
            report.setSections(sections);
            reportStore.put(reportId, report);
            
            logger.info("生成合规报告: reportId={}, scanId={}, issues={}", 
                reportId, scanResult.getScanId(), summary.getTotalIssues());
            return report;
        } catch (Exception e) {
            throw new ComplianceReportException("生成合规报告失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public ComplianceReportService.ComplianceReport getReport(String reportId) throws ComplianceReportException {
        if (reportId == null || reportId.trim().isEmpty()) {
            throw new ComplianceReportException("报告ID不能为空");
        }
        
        ComplianceReportService.ComplianceReport report = reportStore.get(reportId);
        if (report == null) {
            throw new ComplianceReportException("合规报告不存在: " + reportId);
        }
        
        return report;
    }
    
    @Override
    public List<ComplianceReportService.ComplianceReport> listReports(Map<String, Object> filter) throws ComplianceReportException {
        try {
            List<ComplianceReportService.ComplianceReport> reports = new ArrayList<>(reportStore.values());
            
            // 应用过滤条件
            if (filter != null) {
                String scanId = (String) filter.get("scanId");
                Long startTime = filter.containsKey("startTime") ? 
                    Long.parseLong(filter.get("startTime").toString()) : null;
                Long endTime = filter.containsKey("endTime") ? 
                    Long.parseLong(filter.get("endTime").toString()) : null;
                
                if (scanId != null) {
                    reports.removeIf(r -> !scanId.equals(r.getScanId()));
                }
                if (startTime != null) {
                    reports.removeIf(r -> r.getGenerateTime() < startTime);
                }
                if (endTime != null) {
                    reports.removeIf(r -> r.getGenerateTime() > endTime);
                }
            }
            
            // 按时间倒序排序
            reports.sort((a, b) -> Long.compare(b.getGenerateTime(), a.getGenerateTime()));
            
            return reports;
        } catch (Exception e) {
            throw new ComplianceReportException("查询合规报告列表失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public byte[] exportReport(String reportId, String format) throws ComplianceReportException {
        ComplianceReportService.ComplianceReport report = getReport(reportId);
        
        try {
            // TODO: 实际实现需要：
            // 1. 根据格式（PDF, EXCEL, HTML）生成文件
            // 2. 使用相应的库（如iText、Apache POI、Thymeleaf等）
            
            // 模拟导出（返回JSON格式的字节数组）
            String json = "{\"reportId\":\"" + report.getReportId() + "\",\"title\":\"" + report.getTitle() + "\"}";
            return json.getBytes("UTF-8");
        } catch (Exception e) {
            throw new ComplianceReportException("导出合规报告失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 生成报告摘要
     */
    private ComplianceReportService.ReportSummary generateSummary(ScanResult scanResult) {
        ComplianceReportService.ReportSummary summary = new ComplianceReportService.ReportSummary();
        
        List<ScanIssue> issues = scanResult.getIssues();
        summary.setTotalIssues(issues.size());
        
        int critical = 0, high = 0, medium = 0, low = 0;
        for (ScanIssue issue : issues) {
            switch (issue.getSeverity()) {
                case "CRITICAL":
                    critical++;
                    break;
                case "HIGH":
                    high++;
                    break;
                case "MEDIUM":
                    medium++;
                    break;
                case "LOW":
                    low++;
                    break;
            }
        }
        
        summary.setCriticalIssues(critical);
        summary.setHighIssues(high);
        summary.setMediumIssues(medium);
        summary.setLowIssues(low);
        
        // 计算合规分数（问题越少分数越高）
        int totalWeight = critical * 10 + high * 5 + medium * 2 + low * 1;
        double maxScore = 100.0;
        summary.setComplianceScore(Math.max(0.0, maxScore - totalWeight) / maxScore);
        
        return summary;
    }
    
    /**
     * 提取改进建议
     */
    private List<String> extractRecommendations(List<ScanIssue> issues) {
        List<String> recommendations = new ArrayList<>();
        
        for (ScanIssue issue : issues) {
            if (issue.getRecommendation() != null && !issue.getRecommendation().trim().isEmpty()) {
                String recommendation = issue.getRecommendation();
                if (!recommendations.contains(recommendation)) {
                    recommendations.add(recommendation);
                }
            }
        }
        
        return recommendations;
    }
}
