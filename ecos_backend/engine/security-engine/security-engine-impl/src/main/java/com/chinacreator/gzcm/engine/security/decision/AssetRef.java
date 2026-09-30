package com.chinacreator.gzcm.engine.security.decision;

import java.util.Collections;
import java.util.List;

/**
 * 详细设计-01 C.2.1 — 受保护对象最小单元。
 *
 * <p>{@code schema.table} 定位符（IR02/ST07：跨域存定位符，不落具体 endpoint）。
 * {@code columns} 空 = 未指定列（决定侧按整表 CLS 处理）。</p>
 */
public final class AssetRef {
    private final String schema;
    private final String table;
    private final List<String> columns;

    public AssetRef(String schema, String table, List<String> columns) {
        this.schema = schema == null ? "public" : schema;
        this.table = table;
        this.columns = columns == null ? Collections.emptyList() : List.copyOf(columns);
    }

    public String getSchema() { return schema; }
    public String getTable() { return table; }
    public List<String> getColumns() { return columns; }

    /** 定位符（与 V164 ecos_security_asset.asset_key 同形）。 */
    public String key() {
        return table == null ? "" : (schema.equals("public") ? table : schema + "." + table);
    }

    public static AssetRef fromMap(java.util.Map<String, Object> m) {
        if (m == null) return null;
        Object schema = m.get("schema");
        Object table = m.get("table");
        @SuppressWarnings("unchecked")
        List<String> cols = (List<String>) (m.get("columns") == null
                ? null : ((java.util.List<?>) m.get("columns")).stream().map(String::valueOf).toList());
        if (table == null || table.toString().isBlank()) return null;
        return new AssetRef(schema == null ? null : schema.toString(), table.toString(), cols);
    }
}
