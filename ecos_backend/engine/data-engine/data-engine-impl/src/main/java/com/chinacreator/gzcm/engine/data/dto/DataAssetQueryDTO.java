package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

import java.util.List;

/**
 * 数据资产列表查询参数 DTO（强类型 —— 后端规范 §4 接口禁用 Map）。
 *
 * <p>过滤维度：</p>
 * <ul>
 *   <li>keyword —— 模糊匹配 assetName / businessDesc / description</li>
 *   <li>sensitivityLevel —— L1/L2/L3/L4 精确匹配</li>
 *   <li>categoryId —— 业务分类 ID 精确匹配</li>
 *   <li>layer/zone —— 数据湖分层（按数据湖存储分层规范 §五 合法性矩阵）</li>
 *   <li>datasourceId —— 上游数据源 ID 精确匹配</li>
 *   <li>owner —— 责任人精确匹配</li>
 *   <li>domain —— 多租户域</li>
 * </ul>
 *
 * @author DataBridge Datanet Team
 */
@Data
public class DataAssetQueryDTO {

    /** 关键字（模糊匹配 assetName/businessDesc） */
    private String keyword;

    /** 敏感度（L1/L2/L3/L4） */
    private String sensitivityLevel;

    /** 业务分类 ID */
    private String categoryId;

    /** 数据湖分层：SOURCE/RAW/CURATED/SEMANTIC/APPLICATION */
    private String layer;

    /** 近源区：STRUCTURED/UNSTRUCTURED（仅 layer=RAW 时生效） */
    private String zone;

    /** 上游数据源 ID */
    private String datasourceId;

    /** 责任人 */
    private String owner;

    /** 资源类型（TABLE / VIEW / LAKE_OBJECT） */
    private String resourceType;

    /** 业务域 */
    private String domain;

    /** 分类状态（PENDING / PENDING_CONFIRMATION / CONFIRMED / DISRUPTED） */
    private String categoryStatus;

    /** 页码（从 1 开始） */
    private Integer page = 1;

    /** 每页条数（默认 20） */
    private Integer pageSize = 20;
}
