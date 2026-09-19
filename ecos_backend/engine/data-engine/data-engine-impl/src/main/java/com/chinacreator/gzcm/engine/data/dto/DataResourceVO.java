package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

/**
 * 数据资源登记结果 VO（强类型出参，禁 Map 作响应体）。
 */
@Data
public class DataResourceVO {

    /** 资源唯一标识（td_data_resource.resource_id） */
    private String resourceId;

    /** 资源显示名 */
    private String resourceName;

    /** 资源类型：TABLE / VIEW / API / FILE / LAKE_OBJECT */
    private String resourceType;

    /** 上游数据源 ID */
    private String datasourceId;

    /** 数据源内定位（库表 schema.table / 湖对象完整 key） */
    private String sourcePath;

    /** 状态 */
    private String status;

    /** 数据分层（DataLayer 枚举名） */
    private String layer;

    /** 近源区（仅 layer=RAW 时非空） */
    private String zone;

    /** 本次登记是否为新增（true=新建行，false=幂等更新既有行） */
    private boolean created;
}
