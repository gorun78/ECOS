package com.chinacreator.gzcm.engine.data.writechannel;

import java.time.LocalDateTime;

/**
 * F02-05 / C.3.6 唯一写通道 · 幂等台账实体（V260 {@code td_dw_write_channel_idempotency}）。
 * <p>同 {@code idempotencyKey} 重放不产生第二行：唯一索引 {@code uniq_dw_wc_idempotency_key}
 * 兜底并发竞态，应用侧先 {@code findByKey} 判重命中即复用既有 {@code writeId}。
 */
public class DwWriteChannelIdempotency {

    /** MC01：应用侧 UUID。 */
    private String id;

    /** 幂等键（唯一索引）。 */
    private String idempotencyKey;

    /** 全链路追踪 ID。 */
    private String traceId;

    /** 落层。 */
    private String layer;

    /** 载体子类。 */
    private String layerBucket;

    /** 载体标识。 */
    private String carrierRef;

    /** 存储载体类型。 */
    private String storageKind;

    /** MC06 解析后的 schema 限定名。 */
    private String targetTable;

    /** 本次写入业务记录 ID（应用侧 UUID）。 */
    private String writeId;

    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
    private String versionNo;
    private Integer isDeleted;
    private String domain;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }

    public String getLayerBucket() { return layerBucket; }
    public void setLayerBucket(String layerBucket) { this.layerBucket = layerBucket; }

    public String getCarrierRef() { return carrierRef; }
    public void setCarrierRef(String carrierRef) { this.carrierRef = carrierRef; }

    public String getStorageKind() { return storageKind; }
    public void setStorageKind(String storageKind) { this.storageKind = storageKind; }

    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }

    public String getWriteId() { return writeId; }
    public void setWriteId(String writeId) { this.writeId = writeId; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }

    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
}
