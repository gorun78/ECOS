package com.chinacreator.gzcm.runtime.core.security;

/**
 * 行级过滤条件 — 描述查询时自动注入的 WHERE 子句。
 *
 * <p>示例：用户只能查询自己街道的低保数据
 * <pre>{@code
 * RowFilter filter = new RowFilter("dept_id", "=", "430102001");
 * // SQL 改写为: SELECT ... FROM person_info WHERE (original_condition) AND dept_id = '430102001'
 * }</pre>
 */
public class RowFilter {

    private final String column;
    private final String operator;
    private final Object value;

    public RowFilter(String column, String operator, Object value) {
        this.column = column;
        this.operator = operator;
        this.value = value;
    }

    public String getColumn() { return column; }
    public String getOperator() { return operator; }
    public Object getValue() { return value; }

    /** 生成 SQL WHERE 片段。调用方负责防注入。 */
    public String toSqlFragment() {
        if ("IN".equalsIgnoreCase(operator) || "NOT IN".equalsIgnoreCase(operator)) {
            return column + " " + operator + " (" + value + ")";
        }
        if (value instanceof String) {
            return column + " " + operator + " '" + value + "'";
        }
        return column + " " + operator + " " + value;
    }

    @Override
    public String toString() {
        return "RowFilter{" + column + " " + operator + " " + value + "}";
    }
}
