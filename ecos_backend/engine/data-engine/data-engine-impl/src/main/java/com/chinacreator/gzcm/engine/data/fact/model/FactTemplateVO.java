package com.chinacreator.gzcm.engine.data.fact.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 按 factType 的导入模板 — 详细设计-02 D.2 getFactImportTemplate（PRD-02 §1.4：CSV 含表头/单位/示例/必填标记）。
 *
 * @param factType   事实类型名
 * @param table      schema 限定表名
 * @param headerCsv  CSV 表头行（含单位括号，如 amount(元)）
 * @param units      列 → 单位说明
 * @param required   必填列清单
 * @param exampleRows 示例 2 行（JSONArray 风格，对应表头顺序）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FactTemplateVO(
        String factType,
        String table,
        String headerCsv,
        List<String> units,
        List<String> required,
        List<List<String>> exampleRows
) {
    public List<List<String>> exampleRowsSafe() {
        return exampleRows == null ? new ArrayList<>() : exampleRows;
    }
}
