package com.chinacreator.gzcm.engine.data.fact.model;

/**
 * 导入被拒行定位 — 详细设计-02 C.5 / D.3 FactImportResult.rejected 元素。
 *
 * <p>字段 = C.5 字面四元组 {@code {rowNo, field, ruleId, message, suggestion}}。
 *
 * @param rowNo      行号（1-based，指 rows 数组下标 +1）
 * @param field      违规字段（camelCase 业务列名；批级规则如 DQ-F05 用 "currency"/"-")
 * @param ruleId     规则 ID（本批内建 DQ-F05；其余 DQ-F01..F11 由 DqRowGate 实现方提供）
 * @param message    违规描述
 * @param suggestion 修复建议
 */
public record RowRejection(int rowNo, String field, String ruleId, String message, String suggestion) {
}
