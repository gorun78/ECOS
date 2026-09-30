package com.chinacreator.gzcm.engine.data.fact.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 批次摘要 — 详细设计-02 D.2 getFactBatch：
 * 每表 dq_status 计数 + 批次行清单（按 factType 分组）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FactBatchSummary {

    private String batchId;

    /** 每 fact_type 行：{factType, table, byDqStatus:{PENDING:n,...}, total} */
    private List<Map<String, Object>> byFactType = new ArrayList<>();

    /** 各表命中 batch_id 的行数合计 */
    private long totalRows;

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public List<Map<String, Object>> getByFactType() { return byFactType; }
    public void setByFactType(List<Map<String, Object>> byFactType) {
        this.byFactType = byFactType == null ? new ArrayList<>() : byFactType;
    }
    public long getTotalRows() { return totalRows; }
    public void setTotalRows(long totalRows) { this.totalRows = totalRows; }
}
