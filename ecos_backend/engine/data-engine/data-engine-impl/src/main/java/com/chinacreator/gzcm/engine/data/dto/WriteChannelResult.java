package com.chinacreator.gzcm.engine.data.dto;

/**
 * F02-05 / C.3.6 唯一写通道出参（与 {@link WriteChannelRequest} 配对的强类型响应，
 * 架构铁律 后端 1.5：禁 bare Map 出参）。
 */
public class WriteChannelResult {

    /** 本次写入的业务记录 ID（MC01 应用侧 UUID）。幂等命中时回放**首次**登记的 writeId。 */
    private String writeId;

    /** 幂等键（回显，便于客户端对账）。 */
    private String idempotencyKey;

    /** 全链路追踪 ID（回显）。 */
    private String traceId;

    /** 落层（DataLayer 枚举名）。 */
    private String layer;

    /** MC06 解析后的载体限定名（schema.table）。 */
    private String targetTable;

    /**
     * 载体存在性（D-9 同口径）：该层当前是否已登记 ≥1 载体。
     * 首次写入登记成功后通常为 true；重放命中时按台账层实时重算。
     */
    private boolean declared;

    /** true = 同 idempotencyKey 重放命中（未产生第二行、未重复发事件）。 */
    private boolean idempotentReplay;

    public String getWriteId() { return writeId; }
    public void setWriteId(String writeId) { this.writeId = writeId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getLayer() { return layer; }
    public void setLayer(String layer) { this.layer = layer; }

    public String getTargetTable() { return targetTable; }
    public void setTargetTable(String targetTable) { this.targetTable = targetTable; }

    public boolean isDeclared() { return declared; }
    public void setDeclared(boolean declared) { this.declared = declared; }

    public boolean isIdempotentReplay() { return idempotentReplay; }
    public void setIdempotentReplay(boolean idempotentReplay) { this.idempotentReplay = idempotentReplay; }
}
