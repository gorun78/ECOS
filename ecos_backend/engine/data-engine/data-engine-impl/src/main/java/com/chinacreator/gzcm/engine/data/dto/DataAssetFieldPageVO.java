package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

import java.util.List;

/**
 * 资产-字段级敏感度分页 VO（{@code GET /assets/{id}/fields?confirmed=f}）。
 *
 * @author DataBridge Datanet Team
 */
@Data
public class DataAssetFieldPageVO {

    /** 当前页码（从 1 开始） */
    private Integer page;

    /** 每页条数 */
    private Integer pageSize;

    /** 总条数 */
    private Long total;

    /** 已确认敏感字段数（confirmed=true 计数） */
    private Integer confirmedCount;

    /** 本页字段 */
    private List<DataAssetFieldVO> items;
}
