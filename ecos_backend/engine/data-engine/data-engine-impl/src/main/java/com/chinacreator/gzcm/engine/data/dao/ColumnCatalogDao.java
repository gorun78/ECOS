package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * P0-3（2026-10-14 差距补齐）血缘列供给 DAO ——
 * 只读 PG 目录（{@code information_schema}），为
 * {@code GET /api/v1/datanet/metadata/columns?tableRef=...} 提供列清单与表定位。
 *
 * <p><b>数据源权威裁决</b>（登记于 {@code docs/40-实现/DDL迁移脚本落地登记-2026-10-14.md}）：
 * 本端点列清单<b>单源 = PG 目录</b>，<b>不取 {@code td_data_field}</b>。理由：
 * V3 门禁判的是「事实表列的<b>真实存在性</b>」（本体侧 {@code DatNetLineageGuard} javadoc 原话），
 * {@code td_data_field} 是「已采集元数据」快照，dev 库实测 0 行（需人工触发采集才有数据），
 * 以它为准会把「表真实存在」误判成「列缺失」，属 fail-closed 的错误归因。
 * 与既有先例一致：{@link DataLayerInstanceDao#loadColumns} 同用 {@code information_schema.columns}。</p>
 *
 * <p>红线遵循：SQL 参数化（schema/表名由 service 侧过标识符白名单后才传入，禁拼接）；
 * 无 {@code SELECT *}；只读、不落任何写操作。</p>
 */
@Repository
public class ColumnCatalogDao extends JdbcTemplateDaoSupport {

    /** 按 (schema, table) 取列名清单，序 = ordinal_position（与物理列序一致）。 */
    public List<String> columnNames(String schema, String table) {
        return jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position",
                String.class, schema, table);
    }

    /** 取该表名当前落在哪些 schema（用于裸表名 tableRef 的定位与优先级择一）。 */
    public List<String> schemasHolding(String table) {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT table_schema FROM information_schema.tables WHERE table_name = ?",
                String.class, table);
    }
}
