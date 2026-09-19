package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 数据工作台「通用数据资源登记」出站请求体
 * （{@code POST /api/v1/datanet/metadata/resources}，分层规范 §五/§六）。
 *
 * <p>A3 过渡态下知识工作台据此把「解析文本」登记为 {@code layer=CURATED} 资源。
 */
@Data
public class DataResourceRegisterRequest {

    /** 资源显示名 */
    private String resourceName;

    /** 资源类型：TABLE / VIEW / API / FILE / LAKE_OBJECT */
    private String resourceType;

    /** 数据分层（DataLayer 枚举名） */
    private String layer;

    /** 近源区（非 RAW 层必须为空） */
    private String zone;

    /** 数据源内定位（库表用 schema.table） */
    private String sourcePath;

    /** 上游数据源 ID（可为空） */
    private String datasourceId;

    /** 资源描述 */
    private String description;

    /** 标签（逗号分隔） */
    private String tags;

    /** 状态 */
    private String status;
}
