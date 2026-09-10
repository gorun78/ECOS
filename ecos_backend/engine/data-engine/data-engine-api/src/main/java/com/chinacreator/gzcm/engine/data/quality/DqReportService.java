package com.chinacreator.gzcm.engine.data.quality;

import java.util.List;

import com.chinacreator.gzcm.engine.data.quality.model.DqReportQuery;
import com.chinacreator.gzcm.engine.data.quality.model.DqReportVO;
import com.chinacreator.gzcm.engine.data.quality.model.PageResult;

/**
 * DQ 报告服务接口（PMO-48-D T15）。
 *
 * <p>提供 3 个报告周期 × 6 个能力：
 * <pre>
 *   generateDaily(scope)    // 读最近 1 天 dq_rule_check + dq_alert_record + dq_score_asset → HTML 渲染 → MinIO stub → dq_report 写行
 *   generateWeekly(scope)   // 最近 7 天
 *   generateMonthly(scope)  // 最近 31 天
 *   listReports(query)      // 分页过滤
 *   getReportHtml(id)       // 返 HTML 字符串（GET /{id}/html 用）
 *   getReportPdf(id)        // Phase 5 补 PDF 插件；本 Task 501
 * </pre>
 *
 * <p>每次生成 / 下载都异步 audit {@code DQ_REPORT_GENERATE} / {@code DQ_REPORT_DOWNLOAD}（铁律 2.4 #5）。
 *
 * <p>LLM 摘要：本 Task stub（{@code "该时段 DQ 健康度：overall=X.XX（待 LLM 接入）"}），
 * Phase 5 由 DqLlmBlock 接 cognitive-engine 的真实摘要。
 *
 * <p>Bean {@code ecosDqReportService}（铁律 1.3 防多 Bean 冲突）。
 *
 * @author PMO-48-D T15
 */
public interface DqReportService {

    /**
     * 生成日报（本期 = [now()-1d, now()]）。
     *
     * @param scope ALL / NATIVE / OFI / DOMAIN:xxx
     * @return 新建报告 VO
     */
    DqReportVO generateDaily(String scope);

    /**
     * 生成周报（本期 = [now()-7d, now()]）。
     *
     * @param scope ALL / NATIVE / OFI / DOMAIN:xxx
     * @return 新建报告 VO
     */
    DqReportVO generateWeekly(String scope);

    /**
     * 生成月报（本期 = [now()-31d, now()]）。
     *
     * @param scope ALL / NATIVE / OFI / DOMAIN:xxx
     * @return 新建报告 VO
     */
    DqReportVO generateMonthly(String scope);

    /**
     * 分页列报告（按 end_date DESC）。
     *
     * @param query 过滤 + 分页 DTO
     * @return 分页结果
     */
    PageResult<DqReportVO> listReports(DqReportQuery query);

    /**
     * 取报告 HTML（payload_html 列，大文件时从 MinIO 读）。
     *
     * @param id 报告 ID
     * @return null 表示报告不存在
     */
    DqReportVO getReportHtml(String id);

    /**
     * 取报告 PDF 字节（Phase 5 补；本 Task 由 Controller 直接 501，Service 实现抛
     * {@code BusinessException("PDF 导出在 Phase 5 接入")} 防误调）。
     *
     * @param id 报告 ID
     * @return PDF 字节；本 Task 不实现
     */
    byte[] getReportPdf(String id);
}
