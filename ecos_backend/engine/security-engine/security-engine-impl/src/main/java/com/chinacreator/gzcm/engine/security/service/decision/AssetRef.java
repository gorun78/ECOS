package com.chinacreator.gzcm.engine.security.service.decision;

import java.util.List;

/**
 * 详细设计-01 C.2.1 — 受保护对象最小单元（IR02/ST07 对齐）。
 *
 * @param schema  schema（缺省 public）
 * @param table   表名
 * @param columns 请求投影列（可空 = 整表）
 */
public record AssetRef(String schema, String table, List<String> columns) {
}
