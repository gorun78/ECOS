package com.chinacreator.gzcm.common.data.model;

import java.time.LocalDateTime;

/**
 * 数据资源实体 — 代表一个可被编目和访问的数据资源。
 * <p>
 * 数据资源是数据网络的原子单位，可能来自：
 * <ul>
 *   <li>关系型数据库的表/视图</li>
 *   <li>REST API 端点</li>
 *   <li>文件系统（CSV/Excel/Parquet）</li>
 * </ul>
 * 每个资源归属于某个部门（orgId），注册后进入数据目录对外可见。
 *
 * @author DataBridge Datanet Team
 */
public class DataResource {

    /** 资源唯一标识 */
    private String resourceId;

    /** 资源名称（中文） */
    private String resourceName;

    /** 资源类型: TABLE, VIEW, API, FILE */
    private String resourceType;

    /** 所属部门/组织 ID */
    private String orgId;

    /** 所属部门名称 */
    private String orgName;

    /** 数据源 ID（关联 DataSourceEntity） */
    private String datasourceId;

    /** 数据源内定位（如 schema.table_name 或 API path） */
    private String sourcePath;

    /** 资源描述 */
    private String description;

    /** 标签（逗号分隔） */
    private String tags;

    /** 状态: ACTIVE, INACTIVE, ERROR */
    private String status;

    /** 字段数量 */
    private Integer fieldCount;

    /** 数据量（估算行数） */
    private Long recordCount;

    /** 最后同步时间 */
    private LocalDateTime lastSyncTime;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 数据分层: SOURCE/RAW/CURATED/SEMANTIC/APPLICATION */
    private String layer;

    /** 近源区（仅 layer=RAW 时有效）: STRUCTURED=结构化近源 / UNSTRUCTURED=非结构化近源 */
    private String zone;

    /**
     * 载体子类（F02-05）：数据湖五层内的载体子分类。
     * 对应列 {@code layer_bucket VARCHAR(30)}（V260 新增，只加）。
     */
    private String layerBucket;

    /**
     * 载体标识（F02-05）：对象 key / 表名 / 视图名 / 图谱 label。
     * 跨域只存定位符（铁律 §3.5），对应列 {@code carrier_ref VARCHAR(255)}。
     */
    private String carrierRef;

    /**
     * 存储载体类型（F02-05）：PG/MINIO/DORIS/CLICKHOUSE/NEO4J/PG_VECTOR。
     * 对应列 {@code storage_kind VARCHAR(20)}（湖规范 v2.0 §七A 载体分档）。
     */
    private String storageKind;

    // ===== Getters/Setters =====

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getResourceName() { return resourceName; }
    public void setResourceName(String resourceName) { this.resourceName = resourceName; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getOrgId() { return orgId; }
    public void setOrgId(String orgId) { this.orgId = orgId; }

    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }

    public String getDatasourceId() { return datasourceId; }
    public void setDatasourceId(String datasourceId) { this.datasourceId = datasourceId; }

    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getFieldCount() { return fieldCount; }
    public void setFieldCount(Integer fieldCount) { this.fieldCount = fieldCount; }

    public Long getRecordCount() { return recordCount; }
    public void setRecordCount(Long recordCount) { this.recordCount = recordCount; }

    public LocalDateTime getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(LocalDateTime lastSyncTime) { this.lastSyncTime = lastSyncTime; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public String getLayerBucket() { return layerBucket; }
    public void setLayerBucket(String layerBucket) { this.layerBucket = layerBucket; }

    public String getCarrierRef() { return carrierRef; }
    public void setCarrierRef(String carrierRef) { this.carrierRef = carrierRef; }

    public String getStorageKind() { return storageKind; }
    public void setStorageKind(String storageKind) { this.storageKind = storageKind; }
}
