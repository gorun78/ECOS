package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

/**
 * 通用数据资源登记入参（方案 §5.3「知识→数据 登记数据资源」）。
 *
 * <p>供知识工作台在 A3 过渡态把「解析文本」登记为 {@code layer=CURATED} 资源使用；
 * 亦可供其他工作台登记任意分层的资源。分层/近源区必须通过数据湖存储分层规范 §五
 * 合法性矩阵校验（仅 {@code RAW} 允许 {@code zone}，其余层 {@code zone} 必须为空）。
 */
@Data
public class DataResourceRegisterDTO {

    /** 资源显示名，必填，≤256 */
    private String resourceName;

    /** 资源类型：TABLE / VIEW / API / FILE / LAKE_OBJECT，必填 */
    private String resourceType;

    /** 数据分层（DataLayer 枚举名），必填 */
    private String layer;

    /** 近源区：仅 layer=RAW 时允许 STRUCTURED / UNSTRUCTURED，其余层必须为空 */
    private String zone;

    /** 数据源内定位（库表用 schema.table；湖对象用完整对象 key），必填，≤512 */
    private String sourcePath;

    /** 上游数据源 ID（可选；为空落兜底值） */
    private String datasourceId;

    /** 资源描述（可选） */
    private String description;

    /** 标签（逗号分隔，可选） */
    private String tags;

    /** 状态（可选，默认 ACTIVE） */
    private String status;
}
