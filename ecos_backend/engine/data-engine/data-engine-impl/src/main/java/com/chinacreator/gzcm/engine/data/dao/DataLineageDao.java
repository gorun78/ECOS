package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * 数据血缘（lineage）数据访问层。
 *
 * <p>R2-B1 数据访问收口（IR06/§2.5）：{@code DataLineageService} 原持 raw
 * {@code JdbcTemplate} 字段直连 JDBC，本 DAO 承接其全部 SQL。Service 层只做
 * 组装/解析/BFS 计算与降级语义（try/catch 保留在 Service），SQL 单一落点收敛至此。</p>
 *
 * <p>本 DAO 只读/写血缘表（{@code ecos_data.ecos_data_lineage_node/edge}）与
 * Pipeline 定义/任务表（读 yaml_content / definition_jsonb），schema 前缀遵循
 * V200 治理口径，不再改动。</p>
 */
@Repository
public class DataLineageDao extends JdbcTemplateDaoSupport {

    // ── 读：Pipeline 任务（yaml_content）──

    public Map<String, Object> getTaskYamlContent(String taskId) {
        return jdbcTemplate.queryForMap(
                "SELECT yaml_content FROM ecos_pipeline_task WHERE id = ?", taskId);
    }

    public Map<String, Object> getTaskNameAndYaml(String pid) {
        return jdbcTemplate.queryForMap(
                "SELECT name, yaml_content FROM ecos_pipeline_task WHERE id = ?", pid);
    }

    /** 含 yaml_content 的存量任务（YAML 风格），limit>0 时限条数（IR05 参数化）。 */
    public List<Map<String, Object>> listTasksWithYaml(int limit) {
        if (limit > 0) {
            return jdbcTemplate.queryForList(
                    "SELECT id, name, yaml_content FROM ecos_pipeline_task WHERE yaml_content IS NOT NULL ORDER BY updated_at DESC LIMIT ?",
                    limit);
        }
        return jdbcTemplate.queryForList(
                "SELECT id, name, yaml_content FROM ecos_pipeline_task WHERE yaml_content IS NOT NULL ORDER BY updated_at DESC");
    }

    // ── 读：Pipeline 定义 / 节点 ──

    public List<Map<String, Object>> listActiveDefinitions() {
        return jdbcTemplate.queryForList(
                "SELECT id, name, definition::text AS definition FROM ecos_pipeline_definition WHERE status != 'ARCHIVED' ORDER BY updated_at DESC");
    }

    public List<Map<String, Object>> listNodeTableByDefinition(String definitionId) {
        return jdbcTemplate.queryForList(
                "SELECT node_id, type, config::text AS config FROM ecos_pipeline_node WHERE definition_id = ?", definitionId);
    }

    // ── 读：持久化血缘表 ──

    public List<Map<String, Object>> listLineageNodes() {
        return jdbcTemplate.queryForList(
                "SELECT id, name, node_type as type, datasource_id, schema_name, table_name, layer FROM ecos_data.ecos_data_lineage_node ORDER BY name");
    }

    public List<Map<String, Object>> listLineageEdges() {
        return jdbcTemplate.queryForList(
                "SELECT id, source_node_id as source_id, target_node_id as target_id, edge_type, pipeline_task_id as pipeline_id FROM ecos_data.ecos_data_lineage_edge ORDER BY source_node_id");
    }

    public List<Map<String, Object>> listDataSourceTopologyNodes() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT datasource_id as id, 'datasource' as type, datasource_id as datasource FROM ecos_data.ecos_data_lineage_node WHERE datasource_id IS NOT NULL LIMIT 10");
    }

    public List<Map<String, Object>> listTopologyNodes() {
        return jdbcTemplate.queryForList(
                "SELECT id AS id, node_type AS type, name AS label, table_name AS table, "
                        + "COALESCE(pipeline_task_id, properties->>'task') AS pipeline_task_id "
                        + "FROM ecos_data.ecos_data_lineage_node ORDER BY created_at DESC");
    }

    public List<Map<String, Object>> listTopologyEdges() {
        return jdbcTemplate.queryForList(
                "SELECT id, source_node_id AS source, target_node_id AS target, edge_type AS transform, pipeline_task_id "
                        + "FROM ecos_data.ecos_data_lineage_edge");
    }

    // ── 写：持久化血缘表 ──

    /**
     * 批量写血缘节点（INSERT ... ON CONFLICT DO NOTHING）。
     * H8-T1：表结构唯一来源为迁移脚本（V58/V155/V162），写失败由 Service 层降级告警，不内嵌 DDL 兜底。
     */
    public int batchInsertLineageNodes(List<Object[]> nodeBatch) {
        String sql = "INSERT INTO ecos_data.ecos_data_lineage_node "
                + "(id, node_type, name, table_name, pipeline_task_id, properties, created_at, updated_at) "
                + "VALUES (?,?,?,?,?, ?::jsonb, NOW(), NOW()) ON CONFLICT (id) DO NOTHING";
        return batchUpdate(sql, nodeBatch);
    }

    /** 批量写血缘边（ON CONFLICT DO NOTHING），properties 用 jsonb_set 注入 task_name 防注入。 */
    public int batchInsertLineageEdges(List<Object[]> edgeBatch) {
        String sql = "INSERT INTO ecos_data.ecos_data_lineage_edge "
                + "(id, source_node_id, target_node_id, edge_type, pipeline_task_id, transformation, properties, created_at) "
                + "VALUES (?,?,?,?,?,?, jsonb_set('{}'::jsonb, '{task_name}', to_jsonb(?::text)), NOW()) "
                + "ON CONFLICT (id) DO NOTHING";
        return batchUpdate(sql, edgeBatch);
    }

    /** 重建前清空旧血缘数据（保证幂等）。 */
    public void clearLineageTables() {
        jdbcTemplate.execute("DELETE FROM ecos_data.ecos_data_lineage_edge");
        jdbcTemplate.execute("DELETE FROM ecos_data.ecos_data_lineage_node");
    }
}
