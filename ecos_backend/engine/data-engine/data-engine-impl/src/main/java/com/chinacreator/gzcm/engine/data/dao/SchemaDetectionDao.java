package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * B4a 数据访问收口（R2-B4a）：{@code SchemaChangeDetector}（..scheduler..）
 * 内联 raw-SQL（information_schema 扫描 + schema_snapshots / schema_changes
 * 读写）的 DAO 承载点。
 *
 * <p>架构铁律 §2.5#1 / IR06：Task/检测层禁私持 {@code JdbcTemplate}，
 * SQL 一律下沉 {@code ..dao..}；本 DAO 只搬不改，SQL 文本、列别名、
 * 参数序与 {@code SchemaChangeDetector} 原实现逐字节一致。
 * 读取侧返回原始行（Map/raw 类型），列的解析与哈希映射保留在检测器内，
 * 确保 diff / 持久化行为与原实现完全一致。</p>
 */
@Repository
public class SchemaDetectionDao extends JdbcTemplateDaoSupport {

    /**
     * 全库扫描 information_schema.columns，按 (table_schema, table_name) 聚合列签名。
     * 返回每行: datasource_id / table_name / col_sig（原始拼签名，未哈希）。
     */
    public List<Map<String, Object>> scanCurrentSchema() {
        String sql = """
            SELECT table_schema || '.' || table_name AS full_table,
                   table_schema AS datasource_id,
                   table_name,
                   STRING_AGG(
                       column_name || '|' || data_type || '|' || COALESCE(character_maximum_length::text,'')
                       || '|' || COALESCE(is_nullable,'YES'),
                       ',' ORDER BY ordinal_position
                   ) AS col_sig
            FROM information_schema.columns
            WHERE table_schema NOT IN ('information_schema', 'pg_catalog')
            GROUP BY table_schema, table_name
            ORDER BY table_schema, table_name
            """;
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * 读取单表明细列（细粒度 diff 用）。
     * 返回每行: column_name / data_type / char_len / is_nullable。
     */
    public List<Map<String, Object>> scanCurrentColumns(String schema, String table) {
        String sql = """
            SELECT column_name, data_type, COALESCE(character_maximum_length::text,'') AS char_len,
                   COALESCE(is_nullable,'YES') AS is_nullable,
                   ordinal_position
            FROM information_schema.columns
            WHERE table_schema = ? AND table_name = ?
            ORDER BY ordinal_position
            """;
        return jdbcTemplate.queryForList(sql, schema, table);
    }

    /**
     * 读各 (datasource_id, table_name) 最近一次快照的 column_hash。
     * 返回 key = "datasource_id::table_name"，value = column_hash。
     */
    public Map<String, String> findLatestSnapshotHashes() {
        String sql = """
            SELECT DISTINCT ON (datasource_id, table_name)
                   datasource_id, table_name, column_hash
            FROM schema_snapshots
            ORDER BY datasource_id, table_name, snapshot_at DESC
            """;
        Map<String, String> result = new java.util.HashMap<>();
        jdbcTemplate.query(sql, (rs) -> {
            String key = rs.getString("datasource_id") + "::" + rs.getString("table_name");
            result.put(key, rs.getString("column_hash"));
        });
        return result;
    }

    /**
     * 按 (datasource_id, table_name, column_hash) 取最近一张快照的 col_sig
     * 原始签名串；无快照 / 空签名时返回 {@code null}。
     */
    public String findSnapshotColSig(String datasourceId, String tableName, String hash) {
        // 从最近一次快照的 col_sig 字段解析列信息
        String sql = """
            SELECT col_sig FROM schema_snapshots
            WHERE datasource_id = ? AND table_name = ? AND column_hash = ?
            ORDER BY snapshot_at DESC LIMIT 1
            """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, datasourceId, tableName, hash);
        if (rows.isEmpty()) {
            return null;
        }
        return (String) rows.get(0).get("col_sig");
    }

    /** 写入一张表的本次列签名快照（V162 建表已含 col_sig 列，运行时不再 ALTER）。 */
    public void insertSnapshot(String datasourceId, String tableName, String columnHash, String colSig) {
        jdbcTemplate.update(
            "INSERT INTO schema_snapshots (datasource_id, table_name, column_hash, snapshot_at, col_sig) VALUES (?,?,?,?,?)",
            datasourceId, tableName, columnHash, LocalDateTime.now(), colSig
        );
    }

    /** 写入一条 schema 变更记录（初始 acknowledged = false）。 */
    public void insertChange(String datasourceId, String tableName, String changeType, String detailJson) {
        jdbcTemplate.update(
            "INSERT INTO schema_changes (datasource_id, table_name, change_type, detail_json, detected_at, acknowledged) VALUES (?,?,?,?,?,?)",
            datasourceId, tableName, changeType, detailJson, LocalDateTime.now(), false
        );
    }
}
