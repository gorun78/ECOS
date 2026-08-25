package com.chinacreator.gzcm.engine.data.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Pipeline Repository — 基于 JdbcTemplate 的 CRUD 操作。
 *
 * @author DataBridge Datanet Team
 */
@Repository
public class PipelineRepository {

    private static final Logger log = LoggerFactory.getLogger(PipelineRepository.class);
    private final JdbcTemplate jdbc;

    public PipelineRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * PMO-3J: 确保 ecos_pipeline_node 表有 depends_on 列（jsonb）。
     * 该列由 PMO-3J T2 引入以存储节点 DAG 依赖，但表由旧初始化逻辑创建，
     * 缺少此列。遵循 schema 只加不删规则，启动时 ALTER ADD COLUMN IF NOT EXISTS。
     */
    @PostConstruct
    public void ensureDependsOnColumn() {
        try {
            jdbc.execute("ALTER TABLE ecos_pipeline_node ADD COLUMN IF NOT EXISTS depends_on jsonb");
            log.info("PipelineRepository: ensured depends_on column on ecos_pipeline_node");
        } catch (Exception e) {
            log.warn("PipelineRepository: failed to ensure depends_on column: {}", e.getMessage());
        }
    }

    // ==================== PipelineDefinition ====================

    public PipelineDefinition insertDefinition(PipelineDefinition def) {
        String id = def.getId() != null ? def.getId() : UUID.randomUUID().toString().replace("-", "");
        String definitionJson = buildDefinitionJson(def);
        String sql = "INSERT INTO ecos_pipeline_definition (id, name, description, definition, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?::jsonb, ?, NOW(), NOW())";
        jdbc.update(sql, id, def.getName(), def.getDescription(),
                definitionJson,
                def.getStatus() != null ? def.getStatus() : "DRAFT");
        return findDefinitionById(id);
    }

    public PipelineDefinition updateDefinition(String id, String name, String description, String status) {
        StringBuilder set = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (name != null) {
            set.append("name = ?, ");
            params.add(name);
        }
        if (description != null) {
            set.append("description = ?, ");
            params.add(description);
        }
        if (status != null) {
            set.append("status = ?, ");
            params.add(status);
        }

        if (set.isEmpty()) {
            return findDefinitionById(id);
        }

        set.append("updated_at = NOW()");
        params.add(id);

        String sql = "UPDATE ecos_pipeline_definition SET " + set + " WHERE id = ?";
        jdbc.update(sql, params.toArray());
        return findDefinitionById(id);
    }

    /**
     * 更新 Pipeline 定义的 schedule（cron + scheduleId），存入 definition JSONB 列。
     */
    public void updateDefinitionSchedule(String id, String scheduleCron, String scheduleId) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> schedule = new LinkedHashMap<>();
            schedule.put("cron", scheduleCron);
            if (scheduleId != null) schedule.put("scheduleId", scheduleId);
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("schedule", schedule);
            String json = om.writeValueAsString(root);
            String sql = "UPDATE ecos_pipeline_definition SET definition = ?::jsonb, updated_at = NOW() WHERE id = ?";
            jdbc.update(sql, json, id);
        } catch (Exception e) {
            log.warn("Failed to update schedule for definition {}: {}", id, e.getMessage());
        }
    }

    public void deleteDefinition(String id) {
        String sql = "UPDATE ecos_pipeline_definition SET status = 'ARCHIVED', updated_at = NOW() WHERE id = ?";
        jdbc.update(sql, id);
    }

    public PipelineDefinition findDefinitionById(String id) {
        String sql = "SELECT id, name, description, definition, status, created_at, updated_at " +
                "FROM ecos_pipeline_definition WHERE id = ?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
        if (rows.isEmpty()) return null;
        return mapToDefinition(rows.get(0));
    }

    public List<PipelineDefinition> findAllDefinitions() {
        String sql = "SELECT id, name, description, definition, status, created_at, updated_at " +
                "FROM ecos_pipeline_definition WHERE status != 'ARCHIVED' ORDER BY created_at DESC";
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        List<PipelineDefinition> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(mapToDefinition(row));
        }
        return result;
    }

    public boolean definitionExists(String id) {
        String sql = "SELECT COUNT(*) FROM ecos_pipeline_definition WHERE id = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, id);
        return count != null && count > 0;
    }

    // ==================== PipelineNode ====================

    public void insertNode(PipelineNode node) {
        String id = node.getId() != null ? node.getId() : UUID.randomUUID().toString().replace("-", "");
        String sql = "INSERT INTO ecos_pipeline_node (id, definition_id, node_id, type, config, depends_on, position_x, position_y, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, NOW(), NOW())";
        jdbc.update(sql, id, node.getDefinitionId(), node.getNodeId(), node.getType(),
                node.getConfig(), node.getDependsOn() != null ? node.getDependsOn() : "[]",
                node.getPositionX(), node.getPositionY());
    }

    public void deleteNodesByDefinitionId(String definitionId) {
        String sql = "DELETE FROM ecos_pipeline_node WHERE definition_id = ?";
        jdbc.update(sql, definitionId);
    }

    public void updateNodeDependsOn(String definitionId, String nodeId, String dependsOnJson) {
        String sql = "UPDATE ecos_pipeline_node SET depends_on = ?::jsonb WHERE definition_id = ? AND node_id = ?";
        jdbc.update(sql, dependsOnJson, definitionId, nodeId);
    }

    public List<PipelineNode> findNodesByDefinitionId(String definitionId) {
        String sql = "SELECT id, definition_id, node_id, type, config, depends_on, position_x, position_y, created_at, updated_at " +
                "FROM ecos_pipeline_node WHERE definition_id = ? ORDER BY created_at";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, definitionId);
        List<PipelineNode> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(mapToNode(row));
        }
        return result;
    }

    // ==================== PipelineExecution ====================

    public PipelineExecution insertExecution(PipelineExecution exec) {
        String id = exec.getId() != null ? exec.getId() : UUID.randomUUID().toString().replace("-", "");
        String sql = "INSERT INTO ecos_pipeline_execution (id, pipeline_id, status, started_at) VALUES (?, ?, ?, NOW())";
        jdbc.update(sql, id, exec.getDefinitionId(),
                exec.getStatus() != null ? exec.getStatus() : "PENDING");
        return findExecutionById(id);
    }

    public void updateExecutionStatus(String id, String status, String errorMessage, Long rowsProcessed) {
        String sql = "UPDATE ecos_pipeline_execution SET status = ?, error_message = ?, rows_processed = ?";
        List<Object> params = new ArrayList<>();
        params.add(status);
        params.add(errorMessage);
        params.add(rowsProcessed);

        if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
            sql += ", finished_at = NOW()";
        }
        sql += " WHERE id = ?";
        params.add(id);

        jdbc.update(sql, params.toArray());
    }

    public PipelineExecution findExecutionById(String id) {
        String sql = "SELECT id, pipeline_id, status, started_at, finished_at, error_message, rows_processed " +
                "FROM ecos_pipeline_execution WHERE id = ?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
        if (rows.isEmpty()) return null;
        return mapToExecution(rows.get(0));
    }

    // ==================== Mappers ====================

    private PipelineDefinition mapToDefinition(Map<String, Object> row) {
        PipelineDefinition def = new PipelineDefinition();
        def.setId((String) row.get("id"));
        def.setName((String) row.get("name"));
        def.setDescription((String) row.get("description"));
        def.setStatus((String) row.get("status"));
        // 解析 definition JSONB：{schedule:{cron,scheduleId}, ...}
        Object defVal = row.get("definition");
        if (defVal != null) {
            parseDefinitionJson(def, defVal.toString());
        }
        def.setCreatedAt(toLocalDateTime(row.get("created_at")));
        def.setUpdatedAt(toLocalDateTime(row.get("updated_at")));
        return def;
    }

    /**
     * 解析 definition JSONB 列，填充 scheduleCron 与 extensions。
     */
    @SuppressWarnings("unchecked")
    private void parseDefinitionJson(PipelineDefinition def, String json) {
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

    /**
     * 构造 definition JSONB 列内容，包含 schedule（cron + scheduleId）。
     */
    private String buildDefinitionJson(PipelineDefinition def) {
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

    private PipelineNode mapToNode(Map<String, Object> row) {
        PipelineNode node = new PipelineNode();
        node.setId((String) row.get("id"));
        node.setDefinitionId((String) row.get("definition_id"));
        node.setNodeId((String) row.get("node_id"));
        node.setType((String) row.get("type"));
        // JSONB columns come back as PGobject — use toString()
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

    private PipelineExecution mapToExecution(Map<String, Object> row) {
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

    private LocalDateTime toLocalDateTime(Object val) {
        if (val == null) return null;
        if (val instanceof Timestamp ts) return ts.toLocalDateTime();
        if (val instanceof LocalDateTime ldt) return ldt;
        return null;
    }

    private Integer toInteger(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    private Long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }
}
