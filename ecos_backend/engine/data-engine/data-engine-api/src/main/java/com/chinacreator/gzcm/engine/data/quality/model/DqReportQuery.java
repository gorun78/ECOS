package com.chinacreator.gzcm.engine.data.quality.model;

import lombok.Data;

/**
 * DQ 报告列表查询 DTO（PMO-48-D T15）。
 *
 * <p>过滤维度：{@code reportType}（DAILY/WEEKLY/MONTHLY）、
 * {@code scope}（ALL/NATIVE/OFI/DOMAIN:xxx），分页走 {@code pageNum}/{@code pageSize}。
 *
 * @author PMO-48-D T15
 */
@Data
public class DqReportQuery {

    /** 报告类型过滤（DAILY / WEEKLY / MONTHLY，空 = 全部） */
    private String reportType;

    /** 范围过滤（ALL / NATIVE / OFI / DOMAIN:xxx，空 = 全部） */
    private String scope;

    /** 分页页码（从 1 开始，缺省 1） */
    private Integer pageNum;

    /** 每页大小（缺省 20，上限 200） */
    private Integer pageSize;
}
