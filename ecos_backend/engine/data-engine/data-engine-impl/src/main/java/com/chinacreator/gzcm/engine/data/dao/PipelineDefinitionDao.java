package com.chinacreator.gzcm.engine.data.dao;

import com.chinacreator.gzcm.engine.data.pipeline.PipelineDefinition;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineExecution;
import com.chinacreator.gzcm.engine.data.pipeline.PipelineNode;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B4a 数据访问收口（R2-B4a）：{@code PipelineRepository} 内联 raw-SQL
 * （ecos_pipeline_definition / ecos_pipeline_node / ecos_pipeline_execution）
 * 的 DAO 承载点，返回原始 Row Map 与写入结果，行级 mapper 保留在
 * {@code PipelineRepository}。
 *
 * <p>架构铁律 §2.5#1 / IR06：Repository 层禁私持 {@code JdbcTemplate}，
 * SQL 一律下沉到 {@code ..dao..}；本 DAO 只搬不改，SQL 文本/表名/字段/
 * 参数序与 {@code PipelineRepository} 原实现逐字节一致。</p>
 */
@Repository
public class PipelineDefinitionDao extends JdbcTemplateDaoSupport {

    private static final Logger log = LoggerFactory.getLogger(PipelineDefinitionDao.class);

    public void insertDefinition(String id, String name, String description,
                                 String definitionJson, String status) {
        String sql = "INSERT INTO ecos_pipeline_definition (id, name, description, definition, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?::jsonb, ?, NOW(), NOW())";
        jdbcTemplate.update(sql, id, name, description,
                definitionJson,
                status != null ? status : "DRAFT");
    }

    public int updateDefinition(String id, String setSql, List<Object> params) {
        String sql = "UPDATE ecos_pipeline_definition SET " + setSql + " WHERE id = ?";
        return jdbcTemplate.update(sql, params.toArray());
    }

    public void updateDefinitionScheduleJson(String json, String id) {
        String sql = "UPDATE ecos_pipeline_definition SET definition = ?::jsonb, updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, json, id);
    }

    public void archiveDefinition(String id) {
        String sql = "UPDATE ecos_pipeline_definition SET status = 'ARCHIVED', updated_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public List<Map<String, Object>> findDefinitionRow(String id) {
        String sql = "SELECT id, name, description, definition, status, created_at, updated_at " +
                "FROM ecos_pipeline_definition WHERE id = ?";
        return jdbcTemplate.queryForList(sql, id);
    }

    public List<Map<String, Object>> findDefinitionRowsActive() {
        String sql = "SELECT id, name, description, definition, status, created_at, updated_at " +
                "FROM ecos_pipeline_definition WHERE status != 'ARCHIVED' ORDER BY created_at DESC";
        return jdbcTemplate.queryForList(sql);
    }

    public Integer countDefinition(String id) {
        String sql = "SELECT COUNT(*) FROM ecos_pipeline_definition WHERE id = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, id);
    }

    // ==================== PipelineNode ====================

    public void insertNode(String id, String definitionId, String nodeId, String type,
                           String config, String dependsOn, Integer positionX, Integer positionY) {
        String sql = "INSERT INTO ecos_pipeline_node (id, definition_id, node_id, type, config, depends_on, position_x, position_y, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, NOW(), NOW())";
        jdbcTemplate.update(sql, id, definitionId, nodeId, type,
                config, dependsOn, positionX, positionY);
    }

    public int deleteNodesByDefinitionId(String definitionId) {
        String sql = "DELETE FROM ecos_pipeline_node WHERE definition_id = ?";
        return jdbcTemplate.update(sql, definitionId);
    }

    public int updateNodeDependsOn(String definitionId, String nodeId, String dependsOnJson) {
        String sql = "UPDATE ecos_pipeline_node SET depends_on = ?::jsonb WHERE definition_id = ? AND node_id = ?";
        return jdbcTemplate.update(sql, dependsOnJson, definitionId, nodeId);
    }

    public List<Map<String, Object>> findNodeRowsByDefinitionId(String definitionId) {
        String sql = "SELECT id, definition_id, node_id, type, config, depends_on, position_x, position_y, created_at, updated_at " +
                "FROM ecos_pipeline_node WHERE definition_id = ? ORDER BY created_at";
        return jdbcTemplate.queryForList(sql, definitionId);
    }

    // ==================== PipelineExecution ====================

    public void insertExecution(String id, String definitionId, String status) {
        String sql = "INSERT INTO ecos_pipeline_execution (id, pipeline_id, status, started_at) VALUES (?, ?, ?, NOW())";
        jdbcTemplate.update(sql, id, definitionId,
                status != null ? status : "PENDING");
    }

    public int updateExecutionStatus(String id, String status, String errorMessage,
                                     Long rowsProcessed, boolean finishedNow) {
        String sql = "UPDATE ecos_pipeline_execution SET status = ?, error_message = ?, rows_processed = ?";
        List<Object> params = new ArrayList<>();
        params.add(status);
        params.add(errorMessage);
        params.add(rowsProcessed);

        if (finishedNow) {
            sql += ", finished_at = NOW()";
        }
        sql += " WHERE id = ?";
        params.add(id);

        return jdbcTemplate.update(sql, params.toArray());
    }

    public List<Map<String, Object>> findExecutionRow(String id) {
        String sql = "SELECT id, pipeline_id, status, started_at, finished_at, error_message, rows_processed " +
                "FROM ecos_pipeline_execution WHERE id = ?";
        return jdbcTemplate.queryForList(sql, id);
    }

    public List<Map<String, Object>> findExecutionRowsByDefinitionId(String definitionId, int pageSize, int offset) {
        String sql = "SELECT id, pipeline_id, status, started_at, finished_at, error_message, rows_processed " +
                "FROM ecos_pipeline_execution WHERE pipeline_id = ? " +
                "ORDER BY started_at DESC, created_at DESC LIMIT ? OFFSET ?";
        return jdbcTemplate.queryForList(sql, definitionId, pageSize, offset);
    }

    public Integer countExecutionsByDefinitionId(String definitionId) {
        String sql = "SELECT COUNT(*) FROM ecos_pipeline_execution WHERE pipeline_id = ?";
        return jdbcTemplate.queryForObject(sql, Integer.class, definitionId);
    }

    // ==================== Mappers (moved from PipelineRepository so it can drop JdbcTemplate) ====================

    public static PipelineDefinition mapDefinition(Map<String, Object> row) {
        PipelineDefinition def = new PipelineDefinition();
        def.setId((String) row.get("id"));
        def.setName((String) row.get("name"));
        def.setDescription((String) row.get("description"));
        def.setStatus((String) row.get("status"));
        Object defVal = row.get("definition");
        if (defVal != null) {
            parseDefinitionJson(def, defVal.toString());
        }
        def.setCreatedAt(toLocalDateTime(row.get("created_at")));
        def.setUpdatedAt(toLocalDateTime(row.get("updated_at")));
        return def;
    }

    public static PipelineNode mapNode(Map<String, Object> row) {
        PipelineNode node = new PipelineNode();
        node.setId((String) row.get("id"));
        node.setDefinitionId((String) row.get("definition_id"));
        node.setNodeId((String) row.get("node_id"));
        node.setType((String) row.get("type"));
        Object configVal = row.get("config");
        node.setConfig(configVal != null ? configVal.toString() : null);
        Object dependsOnVal = row.get("depends_on");
        node.setDependsOn(dependsOnVal != null ? dependsOnVal.toString() : null);
        node.setPositionX(toInteger(row.get("position_x")));
        node.setPositionY(toInteger(row.get("position_y")));
        node.setCreatedAt(toLocalDateTime(row.get("created_at")));
        node.setUpdatedAt(toLocalDateTime(row.get("updated_at")));
        return node;
    }

    public static PipelineExecution mapExecution(Map<String, Object> row) {
        PipelineExecution exec = new PipelineExecution();
        exec.setId((String) row.get("id"));
        exec.setDefinitionId((String) row.get("pipeline_id"));
        exec.setStatus((String) row.get("status"));
        exec.setStartedAt(toLocalDateTime(row.get("started_at")));
        exec.setCompletedAt(toLocalDateTime(row.get("finished_at")));
        exec.setErrorMessage((String) row.get("error_message"));
        exec.setRowsProcessed(toLong(row.get("rows_processed")));
        return exec;
    }

    @SuppressWarnings("unchecked")
    static void parseDefinitionJson(PipelineDefinition def, String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> root = om.readValue(json, Map.class);
            Object schedule = root.get("schedule");
            Map<String, Object> extensions = new LinkedHashMap<>(root);
            if (schedule instanceof Map) {
                Map<String, Object> sch = (Map<String, Object>) schedule;
                Object cron = sch.get("cron");
                if (cron != null) def.setScheduleCron(cron.toString());
                Object sid = sch.get("scheduleId");
                if (sid != null) extensions.put("scheduleId", sid);
            }
            def.setExtensions(extensions);
        } catch (Exception e) {
            log.warn("Failed to parse definition JSON: {}", json, e);
        }
    }

    public static String buildDefinitionJson(PipelineDefinition def) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> root = new LinkedHashMap<>();
            if (def.getScheduleCron() != null || def.getExtensions() != null) {
                Map<String, Object> schedule = new LinkedHashMap<>();
                if (def.getScheduleCron() != null) schedule.put("cron", def.getScheduleCron());
                if (def.getExtensions() != null && def.getExtensions().get("scheduleId") != null) {
                    schedule.put("scheduleId", def.getExtensions().get("scheduleId"));
                }
                root.put("schedule", schedule);
            }
            if (def.getExtensions() != null) {
                for (Map.Entry<String, Object> e : def.getExtensions().entrySet()) {
                    if (!"schedule".equals(e.getKey())) root.put(e.getKey(), e.getValue());
                }
            }
            return om.writeValueAsString(root);
        } catch (Exception e) {
            return "{}";
        }
    }

    static LocalDateTime toLocalDateTime(Object val) {
        if (val == null) return null;
        if (val instanceof Timestamp ts) return ts.toLocalDateTime();
        if (val instanceof LocalDateTime ldt) return ldt;
        return null;
    }

    static Integer toInteger(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    static Long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }
}
