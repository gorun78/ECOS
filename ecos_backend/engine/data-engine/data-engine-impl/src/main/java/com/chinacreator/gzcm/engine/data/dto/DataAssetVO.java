package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

/**
 * 数据资产实体 VO（出参，强类型 —— 后端规范 §8 接口不允 Map 响应）。
 *
 * <p>范围 A：把 td_data_resource（V150 物理层）升级为"可手工维护"的业务资产。
 * 本 VO 是业务层视图，含业务字段 + 元数据.</p>
 *
 * @author DataBridge Datanet Team
 */
@Data
public class DataAssetVO {

    /** 资产唯一标识（ecos_data_asset.asset_id） */
    private String assetId;

    /** 物理资源 ID（→ td_data_resource.resource_id） */
    private String resourceId;

    /** 资源显示名（冗余缓存，物理层改名时不强制同步资产名） */
    private String resourceName;

    /** 资源类型（TABLE / VIEW / LAKE_OBJECT 等，冗余缓存用于列表筛选） */
    private String resourceType;

    /** 上游数据源 ID */
    private String datasourceId;

    /** 资产业务名（手工维护） */
    private String assetName;

    /** 业务说明（手工维护） */
    private String businessDesc;

    /** 责任人（手工维护） */
    private String owner;

    /** 负责组织 */
    private String ownerOrg;

    /** 数据粒度（手工维护，"用户级/订单级/审计级"等自由文本） */
    private String dataGrain;

    /** 业务分类 ID（→ ecos_data_category_tree.category_id） */
    private String categoryId;

    /** 业务分类名（冗余缓存，列表显示） */
    private String categoryName;

    /** 敏感度 L1/L2/L3/L4 */
    private String sensitivityLevel;

    /** 敏感度中文名（公开/内部/敏感/核心机密） */
    private String levelName;

    /** 分类状态（PENDING / NOT_RECOMMENDED / PENDING_CONFIRMATION / CONFIRMED / DISRUPTED） */
    private String categoryStatus;

    /** 命中字段总数（来自字段级敏感度表） */
    private Integer confirmedFieldCount;

    /** 近源层（layer: SOURCE/RAW/CURATED/SEMANTIC/APPLICATION） */
    private String layer;

    /** 近源区（zone: STRUCTURED/UNSTRUCTURED） */
    private String zone;

    /** 资源描述（来自 td_data_resource） */
    private String description;

    /** 字段总数（来自 td_data_resource.field_count） */
    private Integer fieldCount;

    /** 记录数（来自 td_data_resource.record_count） */
    private Long recordCount;

    /** 最近状态同步时间（来自 td_data_resource.last_sync_time） */
    private java.time.LocalDateTime lastSyncTime;

    /** 最近打标人 */
    private String lastTaggedBy;

    /** 最近打标时间 */
    private java.time.LocalDateTime lastTaggedAt;

    /** 业务域（多租户预留） */
    private String domain;
}
