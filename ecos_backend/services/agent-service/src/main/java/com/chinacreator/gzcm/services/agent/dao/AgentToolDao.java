package com.chinacreator.gzcm.services.agent.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * R2-B4b 数据访问收口（架构铁律 §2.5 #1 / IR06）：agent-service {@code toolrouter/tools}
 * 9 个工具内联 SQL 的统一落点。
 *
 * <p>只搬不改：SQL 文本 / 表名 / 字段 / 参数序与原 Tool 逐字节一致。</p>
 *
 * <p>承接原文件：ExecuteExpertRuleTool / FindGraphPathTool / GetDataLineageTool /
 * GetTableSchemaTool / ListDataSourcesTool / QueryPhysicalTableTool / RAGQueryTool /
 * RunQualityCheckTool / SearchCatalogTool / SearchKnowledgeGraphTool / TriggerPipelineTool。</p>
 */
@Repository
public class AgentToolDao extends JdbcTemplateDaoSupport {

    /** ExecuteExpertRuleTool */
    public Map<String, Object> findExpertRule(String ruleId) {
        return jdbcTemplate.queryForMap(
            "SELECT id, name, rule_type, expression, enabled FROM ecos_expert_rule WHERE id = ?", ruleId);
    }

    /** FindGraphPathTool */
    public List<Map<String, Object>> listGraphEdgesForPath(String from, String to) {
        return jdbcTemplate.queryForList(
            "SELECT source_id, target_id, relation_type, weight FROM ecos_graph_edge " +
            "WHERE source_id = ? OR target_id = ? OR source_id = ? OR target_id = ? " +
            "ORDER BY weight ASC LIMIT 50", from, from, to, to);
    }

    /** GetDataLineageTool */
    public Map<String, Object> findPipeline(String pipelineId) {
        return jdbcTemplate.queryForMap(
            "SELECT id, name, description, status, created_at, updated_at FROM ecos_pipeline WHERE id = ?", pipelineId);
    }

    public List<Map<String, Object>> listPipelineSteps(String pipelineId) {
        return jdbcTemplate.queryForList(
            "SELECT step_order, step_name, step_type, config FROM ecos_pipeline_step WHERE pipeline_id = ? ORDER BY step_order", pipelineId);
    }

    /** GetTableSchemaTool */
    public List<Map<String, Object>> listTableSchemaFields(String resourceId) {
        return jdbcTemplate.queryForList(
            "SELECT column_name, data_type, is_nullable, column_default, column_comment " +
            "FROM ecos_metadata_field WHERE resource_id = ? ORDER BY ordinal_position", resourceId);
    }

    /** ListDataSourcesTool */
    public List<Map<String, Object>> listDataSources() {
        return jdbcTemplate.queryForList(
            "SELECT id, name, type, host, port, database_name, status, created_at FROM ecos_datasource ORDER BY name");
    }

    /** QueryPhysicalTableTool */
    public int insertQueryLog(String id, String datasourceId, String sql, int maxRows) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_query_log (id, datasource_id, sql_text, max_rows, status, created_at) " +
            "VALUES (?, ?, ?, ?, 'SUBMITTED', NOW())",
            id, datasourceId, sql, maxRows);
    }

    /** QueryPhysicalTableTool 动态物理查表（sql 为上游已解析的物理查询，原样下推 LIMIT） */
    public List<Map<String, Object>> queryPhysicalTable(String sql, int maxRows) {
        return jdbcTemplate.queryForList(sql + " LIMIT " + maxRows);
    }

    /** RAGQueryTool */
    public List<Map<String, Object>> searchKnowledgeChunks(String queryText, int topK) {
        return jdbcTemplate.queryForList(
            "SELECT id, content, source, score FROM ecos_knowledge_chunk " +
            "WHERE to_tsvector('chinese', content) @@ plainto_tsquery('chinese', ?) " +
            "ORDER BY score DESC LIMIT ?", queryText, topK);
    }

    /** RunQualityCheckTool */
    public int insertQualityCheck(String checkId, String ruleId, String datasourceId, String tableName, int sampleSize) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_quality_check (id, rule_id, datasource_id, table_name, sample_size, status, created_at) " +
            "VALUES (?, ?, ?, ?, ?, 'RUNNING', NOW())",
            checkId, ruleId, datasourceId, tableName, sampleSize);
    }

    /** SearchCatalogTool */
    public List<Map<String, Object>> searchCatalog(String keyword) {
        return jdbcTemplate.queryForList(
            "SELECT id, name, type, description, owner, created_at FROM ecos_catalog_item " +
            "WHERE name ILIKE ? OR description ILIKE ? ORDER BY name LIMIT 50",
            "%" + keyword + "%", "%" + keyword + "%");
    }

    /** SearchKnowledgeGraphTool */
    public List<Map<String, Object>> searchGraphNodes(String query) {
        return jdbcTemplate.queryForList(
            "SELECT id, name, type, properties FROM ecos_graph_node " +
            "WHERE name ILIKE ? OR type ILIKE ? ORDER BY name LIMIT 50",
            "%" + query + "%", "%" + query + "%");
    }

    public List<Map<String, Object>> searchGraphEdges(String query) {
        return jdbcTemplate.queryForList(
            "SELECT e.id, e.source_id, e.target_id, e.relation_type, e.weight " +
            "FROM ecos_graph_edge e " +
            "WHERE e.relation_type ILIKE ? LIMIT 50",
            "%" + query + "%");
    }

    /** TriggerPipelineTool */
    public int insertPipelineRun(String runId, String taskId) {
        return jdbcTemplate.update(
            "INSERT INTO ecos_pipeline_run (id, task_id, status, created_at) VALUES (?, ?, 'PENDING', NOW())",
            runId, taskId);
    }
}
