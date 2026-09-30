package com.chinacreator.gzcm.engine.data.fact.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 业务事实导入结果 — 详细设计-02 D.3 FactImportResult。
 *
 * <p>{@code {batchId, accepted, rejected:[{rowNo,field,ruleId,message,suggestion}], coverageWarning?}}。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FactImportResult {

    private String batchId;
    private int accepted;
    private List<RowRejection> rejected = new ArrayList<>();

    /** 批次覆盖率低于阈值时的警告文案（WARN 级，不阻断；F02-07 队列写入由外向无归属方另行负责） */
    private String coverageWarning;

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public int getAccepted() { return accepted; }
    public void setAccepted(int accepted) { this.accepted = accepted; }
    public List<RowRejection> getRejected() { return rejected; }
    public void setRejected(List<RowRejection> rejected) {
        this.rejected = rejected == null ? new ArrayList<>() : rejected;
    }
    public String getCoverageWarning() { return coverageWarning; }
    public void setCoverageWarning(String coverageWarning) { this.coverageWarning = coverageWarning; }

    /** 便捷：按行转 Map 视图（供兼容旧消费方）。 */
    public Map<String, Object> asMap() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("batchId", batchId);
        m.put("accepted", accepted);
        m.put("rejected", rejected);
        if (coverageWarning != null) {
            m.put("coverageWarning", coverageWarning);
        }
        return m;
    }
}
