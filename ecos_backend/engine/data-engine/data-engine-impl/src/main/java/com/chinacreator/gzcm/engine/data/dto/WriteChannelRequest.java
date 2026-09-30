package com.chinacreator.gzcm.engine.data.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * F02-05 唯一写通道入参（C.3.6 / D.2）。
 *
 * <p>入参必带 {@code layer} + {@code carrierRef} + {@code traceId} + {@code idempotencyKey}
 * （F02-05 第 3 条，缺一即拒）。data-engine 侧职责：MC06 schema 限定名解析、MC02 列类型
 * 白名单校验（裸 JSON 列参与 WHERE 即拒）、ST06 审计事件、{@code PipelineEvent} 发布、
 * 同 {@code idempotencyKey} 重放幂等（不产生第二行）。
 *
 * <p>强类型（架构铁律 后端 1.5：禁 {@code Map<String,Object>} 作必填接口）；
 * 实际写入的业务行载荷走 {@link #rows}（动态列结构，逐行 Map 承载，与
 * {@link DataLayerRowsVO#rows} 同口径）。
 */
public class WriteChannelRequest {

    /** 落层：SOURCE/RAW/CURATED/SEMANTIC/APPLICATION（DataLayer 枚举名，必填）。 */
    private String layer;

    /**
     * 载体标识（必填）：对象 key / 表名 / 视图名 / 图谱 label。
     * 若已形如 {@code schema.table} 则原样使用（MC06）；否则落业务域 DW schema 前缀。
     */
    private String carrierRef;

    /** 全链路追踪 ID（必填）。 */
    private String traceId;

    /** 幂等键（必填）：同 key 重放不得产生第二行。 */
    private String idempotencyKey;

    /** 存储载体类型：PG/MINIO/DORIS/CLICKHOUSE/NEO4J/PG_VECTOR（可选，缺省按层推断）。 */
    private String storageKind;

    /** 载体子类（可选）。 */
    private String layerBucket;

    /**
     * 参与 WHERE 的列名清单（可选）：供 MC02 校验。
     * 任一列若为「裸 JSON 列」（类型 JSON 或列名 {@code *_json}、未投影）→ 拒。
     */
    private List<String> whereColumns = new ArrayList<>();

    /** 写入的业务行载荷（可选，动态列→值）。 */
    private Map<String, Object> rows;

    /** 多租户预留（DR08），缺省 default。 */
    private String domain = "default";

    /** 操作人（缺省 system）。 */
    private String operator = "system";

    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }

    public String getCarrierRef() { return carrierRef; }
    public void setCarrierRef(String carrierRef) { this.carrierRef = carrierRef; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getStorageKind() { return storageKind; }
    public void setStorageKind(String storageKind) { this.storageKind = storageKind; }

    public String getLayerBucket() { return layerBucket; }
    public void setLayerBucket(String layerBucket) { this.layerBucket = layerBucket; }

    public List<String> getWhereColumns() { return whereColumns; }
    public void setWhereColumns(List<String> whereColumns) { this.whereColumns = whereColumns; }

    public Map<String, Object> getRows() { return rows; }
    public void setRows(Map<String, Object> rows) { this.rows = rows; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
