package com.chinacreator.gzcm.engine.data.quality.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.data.quality.DqReportService;
import com.chinacreator.gzcm.engine.data.quality.model.DqReportQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqReportVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * DQ 报告 REST API（PMO-48-D T15）。
 *
 * <pre>
 * POST /api/v1/dq/reports/generate?scope=ALL&amp;type=daily  — 生成报告（DAILY / WEEKLY / MONTHLY）
 * GET  /api/v1/dq/reports                             — 分页列表（reportType / scope 过滤 + pageNum / pageSize）
 * GET  /api/v1/dq/reports/{id}/html                   — 返回 HTML（Content-Type: text/html; charset=UTF-8）
 * GET  /api/v1/dq/reports/{id}/pdf                    — PDF 导出（Phase 5 补，当前 501 Not Implemented）
 * </pre>
 *
 * <p>三滤波器（已就绪）：本 Controller 路径 {@code /api/v1/dq/reports/**}
 * 由既有 {@code /api/v1/dq/**} 通配覆盖（Phase 1 T2 已交付 VersionPrefixRewriteFilter KEEP +
 * SecurityConfig permitAll + ClearanceInterceptor 豁免双路径）。本 Task 无需追加。
 *
 * <p>安全卡（铁律 2.4 #5）：生成 / 下载由 {@code DqReportServiceImpl} 调
 * {@code DqSecurityService.auditWrite/auditRead} 异步留痕（
 * {@code DQ_REPORT_GENERATE} / {@code DQ_REPORT_READ} / {@code DQ_REPORT_DOWNLOAD}）。
 *
 * @author PMO-48-D T15
 */
@RestController
@RequestMapping("/api/v1/dq/reports")
public class DqReportController {

    /** 支持的报告类型枚举（防御性小写归一化） */
    private static final String TYPE_DAILY = "DAILY";
    private static final String TYPE_WEEKLY = "WEEKLY";
    private static final String TYPE_MONTHLY = "MONTHLY";

    private final DqReportService reportService;

    public DqReportController(DqReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 生成报告入口。type 参数支持 {@code daily / weekly / monthly}（大小写不敏感），
     * 非法 type 抛 {@link ValidationException}。
     */
    @PostMapping("/generate")
    public ApiResponse<DqReportVO> generate(
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "type", required = false) String type) {
        String t = (type == null || type.isBlank()) ? TYPE_DAILY : type.trim().toUpperCase();
        DqReportVO vo;
        switch (t) {
            case TYPE_DAILY:
                vo = reportService.generateDaily(scope);
                break;
            case TYPE_WEEKLY:
                vo = reportService.generateWeekly(scope);
                break;
            case TYPE_MONTHLY:
                vo = reportService.generateMonthly(scope);
                break;
            default:
                throw new ValidationException("不支持的报告类型 " + t + "，仅 DAILY / WEEKLY / MONTHLY");
        }
        return ApiResponse.success(vo);
    }

    /** 分页列表（reportType / scope 过滤 + pageNum / pageSize） */
    @GetMapping
    public ApiResponse<PageResult<DqReportVO>> list(
            @RequestParam(value = "reportType", required = false) String reportType,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        DqReportQuery q = new DqReportQuery();
        q.setReportType(reportType);
        q.setScope(scope);
        q.setPageNum(pageNum);
        q.setPageSize(pageSize);
        return ApiResponse.success(reportService.listReports(q));
    }

    /** 详情 HTML（浏览器直接查看，Content-Type: text/html; charset=UTF-8） */
    @GetMapping("/{id}/html")
    public ResponseEntity<String> getHtml(@PathVariable String id) {
        DqReportVO vo = reportService.getReportHtml(id);
        String html = vo.getPayloadHtml();
        if (html == null || html.isBlank()) {
            throw new NotFoundException("报告 " + id + " 内容为空");
        }
        MediaType htmlUtf8 = new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(htmlUtf8)
                .body(html);
    }

    /** PDF 下载（Phase 5 补 PDF 插件）；本 Task 返 501 Not Implemented */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<Void> getPdf(@PathVariable String id) {
        return ResponseEntity.status(501).build();
    }
}
