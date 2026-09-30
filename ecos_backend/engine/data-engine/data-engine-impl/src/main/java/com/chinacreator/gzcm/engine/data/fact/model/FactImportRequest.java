package com.chinacreator.gzcm.engine.data.fact.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 业务事实导入请求 — 详细设计-02 D.3 FactImportRequest（只增不改）。
 *
 * <p>body {@code {batchId?, rows:[{...}], currency?, period?}}。
 * rows ≥1 且 ≤100（EN03 整批上限，超限 400）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FactImportRequest {

    /** 批次 ID（可选；缺省由应用侧生成 UUID — MC01，本批内多类型调用一致） */
    private String batchId;

    /** 行数组（≤100，EN03）。键 = 业务列 camelCase（见 {@code FactType.columnWhitelist()}） */
    private List<Map<String, Object>> rows = new ArrayList<>();

    /** 批次币种（可选，≤3 字符）；提供时行内 currency 必须与其一致（DQ-F05 批级不变量） */
    private String currency;

    /** 批次期间（可选，'YYYY-MM'，仅记录/审计用） */
    private String period;

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public List<Map<String, Object>> getRows() { return rows; }
    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows == null ? new ArrayList<>() : rows;
    }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
}
