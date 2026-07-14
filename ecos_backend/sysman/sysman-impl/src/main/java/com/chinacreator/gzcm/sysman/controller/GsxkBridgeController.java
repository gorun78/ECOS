package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 高速信科演示数据 Bridge Controller
 * 直接用 JdbcTemplate 读取数据库，绕过 workspace 模块 Controller 加载问题。
 */
@RestController
@RequestMapping("/api/v1/gsxk")
public class GsxkBridgeController {

    private static final Logger log = LoggerFactory.getLogger(GsxkBridgeController.class);
    private final JdbcTemplate jdbc;

    public GsxkBridgeController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ========== Objects ==========

    @GetMapping("/objects")
    public ApiResponse<Map<String, Object>> listObjects(
            @RequestParam(defaultValue = "") String entityCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            boolean hasFilter = entityCode != null && !entityCode.isEmpty();
            int total;
            List<Map<String, Object>> rows;
            int offset = (page - 1) * pageSize;

            if (hasFilter) {
                total = jdbc.queryForObject(
                    "SELECT count(*) FROM ecos_object_data WHERE entity_code = ?", Integer.class, entityCode);
                rows = jdbc.queryForList(
                    "SELECT id, entity_code, object_data, status, created_at, updated_at FROM ecos_object_data"
                    + " WHERE entity_code = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                    entityCode, pageSize, offset);
            } else {
                total = jdbc.queryForObject("SELECT count(*) FROM ecos_object_data", Integer.class);
                rows = jdbc.queryForList(
                    "SELECT id, entity_code, object_data, status, created_at, updated_at FROM ecos_object_data"
                    + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                    pageSize, offset);
            }
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", row.get("id"));
                item.put("entityCode", row.get("entity_code"));
                item.put("status", row.get("status"));
                item.put("createdAt", row.get("created_at"));
                item.put("updatedAt", row.get("updated_at"));
                // Parse object_data JSON string
                Object od = row.get("object_data");
                if (od instanceof String) {
                    try {
                        item.put("data", new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) od));
                    } catch (Exception e) {
                        item.put("data", od);
                    }
                } else {
                    item.put("data", od);
                }
                items.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("page", page);
            result.put("pageSize", pageSize);
            result.put("total", total);
            result.put("data", items);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to query objects", e);
            return ApiResponse.internalError("查询对象失败: " + e.getMessage());
        }
    }

    @GetMapping("/objects/{entityCode}")
    public ApiResponse<Map<String, Object>> getObjectsByEntity(
            @PathVariable String entityCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        try {
            int offset = (page - 1) * pageSize;
            int total = jdbc.queryForObject(
                "SELECT count(*) FROM ecos_object_data WHERE entity_code = ?", Integer.class, entityCode);

            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, entity_code, object_data, status, created_at, updated_at FROM ecos_object_data"
                + " WHERE entity_code = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                entityCode, pageSize, offset);
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", row.get("id"));
                item.put("entityCode", row.get("entity_code"));
                item.put("status", row.get("status"));
                item.put("createdAt", row.get("created_at"));
                item.put("updatedAt", row.get("updated_at"));
                Object od = row.get("object_data");
                if (od instanceof String) {
                    try {
                        item.put("data", new com.fasterxml.jackson.databind.ObjectMapper().readTree((String) od));
                    } catch (Exception e) {
                        item.put("data", od);
                    }
                } else {
                    item.put("data", od);
                }
                items.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("page", page);
            result.put("pageSize", pageSize);
            result.put("total", total);
            result.put("data", items);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to query objects by entityCode", e);
            return ApiResponse.internalError("查询对象失败: " + e.getMessage());
        }
    }

    // ========== Object Schema ==========

    @GetMapping("/objects/{entityCode}/schema")
    public ApiResponse<Map<String, Object>> getObjectSchema(@PathVariable String entityCode) {
        try {
            // Query entity info — use queryForList to handle empty results gracefully
            List<Map<String, Object>> entities = jdbc.queryForList(
                "SELECT id, code, name, description FROM ecos_ontology_entity WHERE code = ? LIMIT 1",
                entityCode);
            if (entities.isEmpty()) {
                return ApiResponse.notFound("实体不存在: " + entityCode);
            }
            Map<String, Object> entity = entities.get(0);
            // Query properties
            List<Map<String, Object>> properties = jdbc.queryForList(
                "SELECT code, name, property_type, required_flag, searchable_flag, sort_order " +
                "FROM ecos_ontology_property WHERE entity_id = ? ORDER BY sort_order",
                entity.get("id"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("entityCode", entity.get("code"));
            result.put("entityName", entity.get("name"));
            result.put("properties", properties);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to get schema for {}", entityCode, e);
            return ApiResponse.internalError("获取Schema失败: " + e.getMessage());
        }
    }

    // ========== Ontology Entities ==========

    @GetMapping("/ontology/entities")
    public ApiResponse<List<Map<String, Object>>> listEntities() {
        try {
            String sql = "SELECT e.id, e.ontology_id, e.code, e.name, e.description, e.entity_type, e.sort_order, " +
                "(SELECT count(*) FROM ecos_ontology_property p WHERE p.entity_id = e.id) as property_count " +
                "FROM ecos_ontology_entity e ORDER BY e.sort_order, e.name";
            List<Map<String, Object>> rows = jdbc.queryForList(sql);
            return ApiResponse.success(rows);
        } catch (Exception e) {
            log.error("Failed to query ontology entities", e);
            return ApiResponse.internalError("查询本体实体失败: " + e.getMessage());
        }
    }

    @PostMapping("/ontology/entities")
    public ApiResponse<Map<String, Object>> createEntity(@RequestBody Map<String, Object> body) {
        try {
            String id = UUID.randomUUID().toString();
            String code = body.get("code") != null ? (String) body.get("code") :
                    ((String) body.get("name")).toLowerCase().replaceAll("\\s+", "-").replaceAll("[^a-z0-9\\-]", "");
            jdbc.update("INSERT INTO ecos_ontology_entity (id, ontology_id, name, code, description, entity_type, created_at) VALUES (?,?,?,?,?,?,now())",
                id, "ont001", body.get("name"), code, body.get("description"), body.getOrDefault("entity_type", body.get("domain")));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Failed to create ontology entity", e);
            return ApiResponse.internalError("创建本体实体失败: " + e.getMessage());
        }
    }

    @PutMapping("/ontology/entities/{entityId}")
    public ApiResponse<Map<String, Object>> updateEntity(@PathVariable String entityId, @RequestBody Map<String, Object> body) {
        try {
            jdbc.update("UPDATE ecos_ontology_entity SET name=?, description=?, entity_type=? WHERE id=?",
                body.get("name"), body.get("description"), body.getOrDefault("entity_type", body.get("domain")), entityId);
            return ApiResponse.success(Map.of("id", entityId));
        } catch (Exception e) {
            log.error("Failed to update ontology entity", e);
            return ApiResponse.internalError("更新本体实体失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/ontology/entities/{entityId}")
    public ApiResponse<Void> deleteEntity(@PathVariable String entityId) {
        try {
            jdbc.update("DELETE FROM ecos_ontology_entity WHERE id=?", entityId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("Failed to delete ontology entity", e);
            return ApiResponse.internalError("删除本体实体失败: " + e.getMessage());
        }
    }

    @GetMapping("/ontology/properties")
    public ApiResponse<List<Map<String, Object>>> listProperties(@RequestParam(defaultValue = "") String entityId) {
        try {
            boolean hasFilter = entityId != null && !entityId.isEmpty();
            String sql;
            Object[] params;

            if (hasFilter) {
                sql = "SELECT p.id, p.entity_id, e.name as entity_name, p.code, p.name, p.property_type, p.required_flag, p.searchable_flag, p.sort_order " +
                    "FROM ecos_ontology_property p LEFT JOIN ecos_ontology_entity e ON p.entity_id = e.id" +
                    " WHERE p.entity_id = ? ORDER BY p.sort_order";
                params = new Object[]{entityId};
            } else {
                sql = "SELECT p.id, p.entity_id, e.name as entity_name, p.code, p.name, p.property_type, p.required_flag, p.searchable_flag, p.sort_order " +
                    "FROM ecos_ontology_property p LEFT JOIN ecos_ontology_entity e ON p.entity_id = e.id" +
                    " ORDER BY p.sort_order";
                params = new Object[0];
            }
            List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
            return ApiResponse.success(rows);
        } catch (Exception e) {
            log.error("Failed to query properties", e);
            return ApiResponse.internalError("查询属性失败: " + e.getMessage());
        }
    }

    // ========== World Model ==========

    @GetMapping("/worldmodel/goals")
    public ApiResponse<List<Map<String, Object>>> listGoals() {
        try {
            String sql = "SELECT id, code, name, description, target_value, current_value, unit, status, priority, category, deadline FROM ecos_world_goal ORDER BY priority, created_at DESC";
            return ApiResponse.success(jdbc.queryForList(sql));
        } catch (Exception e) {
            log.error("Failed to query goals", e);
            return ApiResponse.internalError("查询目标失败: " + e.getMessage());
        }
    }

    @GetMapping("/worldmodel/scenarios")
    public ApiResponse<List<Map<String, Object>>> listScenarios() {
        try {
            String sql = "SELECT s.id, s.code, s.name, s.description, s.status, s.probability, s.impact_score, " +
                "(SELECT count(*) FROM ecos_world_scenario_impact si WHERE si.scenario_id = s.id) as impact_count " +
                "FROM ecos_world_scenario s ORDER BY s.created_at DESC";
            return ApiResponse.success(jdbc.queryForList(sql));
        } catch (Exception e) {
            log.error("Failed to query scenarios", e);
            return ApiResponse.internalError("查询情景失败: " + e.getMessage());
        }
    }

    @GetMapping("/worldmodel/impacts")
    public ApiResponse<List<Map<String, Object>>> listImpacts() {
        try {
            String sql = "SELECT si.id, si.scenario_id, s.name as scenario_name, si.goal_id, g.name as goal_name, " +
                "si.projected_delta, si.confidence, si.rationale " +
                "FROM ecos_world_scenario_impact si " +
                "LEFT JOIN ecos_world_scenario s ON si.scenario_id = s.id " +
                "LEFT JOIN ecos_world_goal g ON si.goal_id = g.id " +
                "ORDER BY si.confidence DESC";
            return ApiResponse.success(jdbc.queryForList(sql));
        } catch (Exception e) {
            log.error("Failed to query impacts", e);
            return ApiResponse.internalError("查询情景影响失败: " + e.getMessage());
        }
    }

    @GetMapping("/worldmodel/causal-links")
    public ApiResponse<List<Map<String, Object>>> listCausalLinks() {
        try {
            String sql = "SELECT cl.id, cl.source_goal_id, g1.name as source_goal_name, " +
                "cl.target_goal_id, g2.name as target_goal_name, " +
                "cl.relationship_type, cl.description, cl.created_at " +
                "FROM ecos_wm_causal_link cl " +
                "LEFT JOIN ecos_wm_goal g1 ON cl.source_goal_id = g1.id " +
                "LEFT JOIN ecos_wm_goal g2 ON cl.target_goal_id = g2.id " +
                "ORDER BY cl.created_at DESC";
            return ApiResponse.success(jdbc.queryForList(sql));
        } catch (Exception e) {
            log.error("Failed to query causal links", e);
            return ApiResponse.internalError("查询因果链失败: " + e.getMessage());
        }
    }

    // ========== DQ Rules ==========

    @GetMapping("/dq/rules")
    public ApiResponse<List<Map<String, Object>>> listDqRules() {
        try {
            String sql = "SELECT id, name, description, rule_type, severity, enabled, created_at, updated_at FROM ecos_dq_rule_v2 ORDER BY id";
            return ApiResponse.success(jdbc.queryForList(sql));
        } catch (Exception e) {
            log.error("Failed to query DQ rules", e);
            return ApiResponse.internalError("查询DQ规则失败: " + e.getMessage());
        }
    }

    // ========== Monitoring Summary ==========

    @GetMapping({"/monitoring/summary", "/monitoring/dashboard"})
    public ApiResponse<Map<String, Object>> monitoringSummary() {
        try {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("objectCount", jdbc.queryForObject("SELECT count(*) FROM ecos_object_data", Integer.class));
            summary.put("entityCount", jdbc.queryForObject("SELECT count(*) FROM ecos_ontology_entity", Integer.class));
            summary.put("goalCount", jdbc.queryForObject("SELECT count(*) FROM ecos_world_goal", Integer.class));
            summary.put("dqRuleCount", jdbc.queryForObject("SELECT count(*) FROM ecos_dq_rule_v2", Integer.class));
            summary.put("orgCount", jdbc.queryForObject("SELECT count(*) FROM td_organization", Integer.class));
            summary.put("userCount", jdbc.queryForObject("SELECT count(*) FROM td_sm_user", Integer.class));
            return ApiResponse.success(summary);
        } catch (Exception e) {
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    // ========== Biz Dashboard ==========

    @GetMapping("/biz/dashboard")
    public ApiResponse<Map<String, Object>> bizDashboard() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();

            // Departments from td_organization
            List<Map<String, Object>> depts = jdbc.queryForList(
                "SELECT \"ORG_ID\" as id, \"ORG_NAME\" as name, \"ORG_CODE\" as code, \"ORG_TYPE\" as type, \"DESCRIPTION\" as description FROM td_organization WHERE \"ORG_TYPE\"='DEPARTMENT'");
            data.put("departments", depts);

            // Project stats
            int projectTotal = jdbc.queryForObject("SELECT count(*) FROM ecos_object_data WHERE entity_code = 'Project'", Integer.class);
            Map<String, Object> projectStats = new LinkedHashMap<>();
            projectStats.put("total", projectTotal);
            projectStats.put("inProgress", projectTotal);
            data.put("projectStats", projectStats);

            // Contract stats (from demo data)
            Map<String, Object> contractStats = new LinkedHashMap<>();
            contractStats.put("total", 12);
            contractStats.put("totalValue", "¥4,200万");
            data.put("contractStats", contractStats);

            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Biz dashboard failed", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    // ========== Search ==========

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "all") String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            List<Map<String, Object>> results = new ArrayList<>();
            int total = 0;

            String likeQ = "%" + q + "%";

            if ("all".equals(type) || "object".equals(type)) {
                List<Map<String, Object>> objs = jdbc.queryForList(
                    "SELECT id, entity_code as type, object_data::text as name FROM ecos_object_data WHERE object_data::text ILIKE ? LIMIT ?",
                    likeQ, size);
                for (Map<String, Object> o : objs) {
                    o.put("category", "object");
                    results.add(o);
                }
            }
            if ("all".equals(type) || "ontology".equals(type)) {
                List<Map<String, Object>> ents = jdbc.queryForList(
                    "SELECT id, 'entity' as type, name, description FROM ecos_ontology_entity WHERE name ILIKE ? OR description ILIKE ? LIMIT ?",
                    likeQ, likeQ, size);
                for (Map<String, Object> e : ents) {
                    e.put("category", "ontology");
                    results.add(e);
                }
            }
            if ("all".equals(type) || "department".equals(type)) {
                List<Map<String, Object>> orgs = jdbc.queryForList(
                    "SELECT \"ORG_ID\" as id, 'org' as type, \"ORG_NAME\" as name FROM td_organization WHERE \"ORG_NAME\" ILIKE ? LIMIT ?",
                    likeQ, size);
                for (Map<String, Object> o : orgs) {
                    o.put("category", "department");
                    results.add(o);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", results);
            result.put("total", results.size());
            result.put("page", page);
            result.put("size", size);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Search failed", e);
            return ApiResponse.internalError("搜索失败: " + e.getMessage());
        }
    }

    // ========== Actions ==========

    @PostMapping("/actions/execute")
    public ApiResponse<Map<String, Object>> executeAction(@RequestBody Map<String, Object> body) {
        try {
            String entityCode = (String) body.getOrDefault("entityCode", "");
            String operatorName = (String) body.getOrDefault("operatorName", "unknown");
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "操作成功: " + operatorName + " on " + entityCode);
            result.put("entityCode", entityCode);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.internalError("操作失败: " + e.getMessage());
        }
    }

    @GetMapping("/actions")
    public ApiResponse<List<Map<String, Object>>> listActions() {
        try {
            String sql = "SELECT id, code, name, description, action_type, strategy, status, created_at FROM ecos_ontology_action ORDER BY name";
            return ApiResponse.success(jdbc.queryForList(sql));
        } catch (Exception e) {
            log.error("Failed to list actions", e);
            return ApiResponse.internalError("查询Actions失败: " + e.getMessage());
        }
    }

    // ========== Workflows (read-only from ecos_workflow_v2) ==========

    @GetMapping("/workflows")
    public ApiResponse<Map<String, Object>> listWorkflows(@RequestParam(defaultValue = "50") int pageSize) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name, description, status, mode, created_at, updated_at FROM ecos_workflow_v2 ORDER BY created_at DESC LIMIT ?", pageSize);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("data", rows);
            result.put("total", rows.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Workflow query failed", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    // ========== Relationships ==========

    @GetMapping("/relationships")
    public ApiResponse<List<Map<String, Object>>> listRelationships() {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, source_entity_code, target_entity_code, relation_type, description FROM ecos_object_relationship ORDER BY id LIMIT 100");
            return ApiResponse.success(rows);
        } catch (Exception e) {
            return ApiResponse.success(Collections.emptyList());
        }
    }

    // ========== Entity Properties CRUD (ontology designer needs these) ==========

    @GetMapping("/entities/{entityId}/properties")
    public ApiResponse<List<Map<String, Object>>> getEntityProperties(@PathVariable String entityId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order FROM ecos_ontology_property WHERE entity_id = ? ORDER BY sort_order",
                entityId);
            return ApiResponse.success(rows);
        } catch (Exception e) {
            return ApiResponse.internalError("查询属性失败: " + e.getMessage());
        }
    }

    @PostMapping("/entities/{entityId}/properties")
    public ApiResponse<Map<String, Object>> createEntityProperty(@PathVariable String entityId, @RequestBody Map<String, Object> body) {
        try {
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag, sort_order) VALUES (?,?,?,?,?,?,?,?)",
                id, entityId, body.get("code"), body.get("name"), body.getOrDefault("propertyType", "STRING"),
                body.getOrDefault("requiredFlag", 0), body.getOrDefault("searchableFlag", 0), body.getOrDefault("sortOrder", 0));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.internalError("创建属性失败: " + e.getMessage());
        }
    }

    @PutMapping("/entities/{entityId}/properties/{propId}")
    public ApiResponse<Map<String, Object>> updateEntityProperty(@PathVariable String entityId, @PathVariable String propId, @RequestBody Map<String, Object> body) {
        try {
            jdbc.update("UPDATE ecos_ontology_property SET name=?, property_type=?, required_flag=?, sort_order=? WHERE id=? AND entity_id=?",
                body.get("name"), body.getOrDefault("propertyType", "STRING"),
                body.getOrDefault("requiredFlag", 0), body.getOrDefault("sortOrder", 0), propId, entityId);
            return ApiResponse.success(Map.of("id", propId));
        } catch (Exception e) {
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/entities/{entityId}/properties/{propId}")
    public ApiResponse<Void> deleteEntityProperty(@PathVariable String entityId, @PathVariable String propId) {
        try {
            jdbc.update("DELETE FROM ecos_ontology_property WHERE id=? AND entity_id=?", propId, entityId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/entities/{entityId}/relationships")
    public ApiResponse<List<Map<String, Object>>> getEntityRelationships(@PathVariable String entityId) {
        // Return object relationships for the given entity
        try {
            String entityCode = jdbc.queryForObject("SELECT code FROM ecos_ontology_entity WHERE id = ?", String.class, entityId);
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, source_entity_code, target_entity_code, relation_type, description FROM ecos_object_relationship WHERE source_entity_code = ? OR target_entity_code = ? LIMIT 50",
                entityCode, entityCode);
            return ApiResponse.success(rows);
        } catch (Exception e) {
            return ApiResponse.success(Collections.emptyList());
        }
    }

    @PostMapping("/entities/{entityId}/relationships")
    public ApiResponse<Map<String, Object>> createEntityRelationship(@PathVariable String entityId, @RequestBody Map<String, Object> body) {
        try {
            String id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO ecos_object_relationship (id, source_entity_code, target_entity_code, relation_type, description) VALUES (?,?,?,?,?)",
                id, body.get("sourceEntityCode"), body.get("targetEntityCode"), body.getOrDefault("relationType", "RELATES_TO"), body.get("description"));
            return ApiResponse.success(Map.of("id", id));
        } catch (Exception e) {
            return ApiResponse.internalError("创建关系失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/entities/{entityId}/relationships/{relId}")
    public ApiResponse<Void> deleteEntityRelationship(@PathVariable String entityId, @PathVariable String relId) {
        try {
            jdbc.update("DELETE FROM ecos_object_relationship WHERE id = ?", relId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    // ========== Datasets ==========

    @GetMapping("/datasets")
    public ApiResponse<List<Map<String, Object>>> listDatasets() {
        try {
            String sql = "SELECT tablename as name, 'dataset' as type, '' as description " +
                "FROM pg_catalog.pg_tables " +
                "WHERE schemaname = 'public' AND tablename LIKE 'ecos_%' " +
                "ORDER BY tablename";
            return ApiResponse.success(jdbc.queryForList(sql));
        } catch (Exception e) {
            log.error("Failed to list datasets", e);
            return ApiResponse.internalError("查询Datasets失败: " + e.getMessage());
        }
    }
}
