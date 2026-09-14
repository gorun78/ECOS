package com.chinacreator.gzcm.engine.data.quality.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * DQ 报告 VO（PMO-48-D T15）。
 *
 * <p>对应 {@code ecos_dq.dq_report} 表的驼峰映射：
 * <ul>
 *   <li>{@code reportType} — DAILY / WEEKLY / MONTHLY</li>
 *   <li>{@code scope} — ALL / NATIVE / OFI / DOMAIN:xxx</li>
 *   <li>{@code payloadHtml} — 内嵌 HTML（GET /{id}/html 直接返回该字段）</li>
 *   <li>{@code pdfObjectKey} — MinIO object key（大文件走对象存储；本 Task stub 返 null）</li>
 *   <li>{@code llmSummary} — LLM 摘要（Phase 5 接 cognitive 后非空；本 Task stub 文本）</li>
 *   <li>{@code avgScore} — 报告范围平均分 0.0-1.0</li>
 * </ul>
 *
 * @author PMO-48-D T15
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DqReportVO {

    /** 报告 ID（VARCHAR 36 主键） */
    private String id;

    /** 报告类型: DAILY / WEEKLY / MONTHLY */
    private String reportType;

    /** 报告范围: ALL / NATIVE / OFI / DOMAIN:xxx */
    private String scope;

    /** 报告起始日期（含） */
    private LocalDate startDate;

    /** 报告截止日期（含） */
    private LocalDate endDate;

    /** 内嵌 HTML 渲染结果（GET /{id}/html 直接返回该字段） */
    private String payloadHtml;

    /** MinIO object key（PDF > 200KB 走对象存储）；本 Task PDF 未启用时返 null */
    private String pdfObjectKey;

    /** LLM 摘要（Phase 5 接 cognitive-engine 后非空） */
    private String llmSummary;

    /** 报告覆盖的数据行数 */
    private Integer rowCount;

    /** 报告覆盖的告警数量 */
    private Integer alertCount;

    /** 报告覆盖范围内平均分（0.0-1.0） */
    private Double avgScore;

    /** 创建人（人工触发时记录用户名） */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
