package com.chinacreator.gzcm.dccheng.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AutoDiscover Controller — 从物理表一键生成本体实体 + 属性 + 实体表映射。
 *
 * <h3>端点：</h3>
 * POST /api/v1/ecos/domains/{domainCode}/auto-discover
 *   请求体: { datasourceId, resourceNames: ["table1","table2"] }
 *   响应: [{ entityCode, entityName, propertyCount, mapping }]
 *
 * GET /api/v1/ecos/entity-mappings?domainCode=xxx
 *   响应: [{ entityCode, datasourceId, resourceName, fieldMappings }]
 */
@RestController
@RequestMapping("/api/v1/ecos")
public class AutoDiscoverController {

    private static final Logger log = LoggerFactory.getLogger(AutoDiscoverController.class);

    private final JdbcTemplate jdbc;

    public AutoDiscoverController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/domains/{domainCode}/auto-discover")
    public ApiResponse<List<Map<String, Object>>> autoDiscover(
            @PathVariable String domainCode,
            @RequestBody Map<String, Object> body) {
        try {
            String datasourceId = (String) body.get("datasourceId");
            @SuppressWarnings("unchecked")
            List<String> resourceNames = (List<String>) body.get("resourceNames");

            if (datasourceId == null || datasourceId.isBlank()) {
                return ApiResponse.badRequest("datasourceId is required");
            }
            if (resourceNames == null || resourceNames.isEmpty()) {
                return ApiResponse.badRequest("resourceNames is required");
            }

            List<Map<String, Object>> results = new ArrayList<>();

            for (String resourceName : resourceNames) {
                // 获取物理表字段
                List<Map<String, Object>> fields = getResourceFields(datasourceId, resourceName);
                if (fields.isEmpty()) {
                    log.warn("No fields found for resource: {}", resourceName);
                    continue;
                }

                // 从表名生成实体编码（下划线转驼峰，首字母大写）
                String entityCode = toEntityCode(resourceName);

                // 1. 创建本体实体
                Map<String, Object> entity = createOntologyEntity(entityCode, resourceName, domainCode);

                // 2. 为每个物理字段创建本体属性
                int propCount = 0;
                for (Map<String, Object> field : fields) {
                    String fieldName = (String) field.get("fieldName");
                    String dataType = (String) field.get("dataType");
                    if (fieldName == null) continue;

                    createEntityProperty(entityCode, fieldName, dataType, domainCode);
                    propCount++;
                }

                // 3. 创建实体-表映射（落PG）
                Map<String, Object> mapping = createMapping(entityCode, resourceName, domainCode, datasourceId, (String) fieldValue(fields, "schema"), fields);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("entityCode", entityCode);
                result.put("entityName", resourceName);
                result.put("domainCode", domainCode);
                result.put("propertyCount", propCount);
                result.put("mapping", mapping);
                results.add(result);

                log.info("Auto-discovered: {} -> entity {} with {} properties", resourceName, entityCode, propCount);
            }

            return ApiResponse.success(results);
        } catch (Exception e) {
            log.error("AutoDiscover failed for domain={}", domainCode, e);
            return ApiResponse.internalError("AutoDiscover failed: " + e.getMessage());
        }
    }

    @GetMapping("/entity-mappings")
    public ApiResponse<List<Map<String, Object>>> listMappings(@RequestParam(required = false) String domainCode) {
        String sql;
        Object[] params;
        if (domainCode != null && !domainCode.isBlank()) {
            sql = "SELECT * FROM ecos_entity_table_mapping WHERE domain_code=? ORDER BY created_at DESC";
            params = new Object[]{domainCode};
        } else {
            sql = "SELECT * FROM ecos_entity_table_mapping ORDER BY created_at DESC";
            params = new Object[]{};
        }
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
        return ApiResponse.success(rows);
    }

    // ── Private helpers ──

    private List<Map<String, Object>> getResourceFields(String datasourceId, String resourceName) {
        try {
            // 从 td_data_field 表查询字段元数据
            String sql = "SELECT f.field_name AS \"fieldName\", f.field_type AS \"dataType\", " +
                         "COALESCE(f.description, '') AS \"comment\", " +
                         "f.nullable AS \"nullable\" " +
                         "FROM td_data_field f " +
                         "INNER JOIN td_data_resource r ON f.resource_id = r.resource_id " +
                         "WHERE r.resource_name=? AND r.datasource_id=? " +
                         "ORDER BY f.field_order LIMIT 200";
            return jdbc.queryForList(sql, resourceName, datasourceId);
        } catch (Exception e) {
            log.warn("Cannot query td_data_field for {}: {}", resourceName, e.getMessage());
            return Collections.emptyList();
        }
    }

    private Map<String, Object> createOntologyEntity(String entityCode, String tableName, String domainCode) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        // 找到或创建 domain 对应的 ontology
        String ontologyId = findOrCreateOntology(domainCode);

        // 检查是否已存在
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_ontology_entity WHERE code=? AND domain_id=(SELECT id FROM ecos_domain WHERE code=?)",
            Integer.class, entityCode, domainCode);
        if (count != null && count > 0) {
            log.info("Entity {} already exists in domain {}", entityCode, domainCode);
            return jdbc.queryForMap(
                "SELECT id, code, name FROM ecos_ontology_entity WHERE code=? AND domain_id=(SELECT id FROM ecos_domain WHERE code=?)",
                entityCode, domainCode);
        }

        jdbc.update(
            "INSERT INTO ecos_ontology_entity (id, ontology_id, code, name, entity_type, description, domain_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, 'MASTER', ?, (SELECT id FROM ecos_domain WHERE code=?), NOW(), NOW())",
            id, ontologyId, entityCode, tableName, "Auto-discovered from " + tableName, domainCode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("code", entityCode);
        result.put("name", tableName);
        return result;
    }

    private String findOrCreateOntology(String domainCode) {
        // 查找 domain 对应的 ontology
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT o.id FROM ecos_ontology o WHERE o.code=?", domainCode.toLowerCase());
        if (!rows.isEmpty()) {
            return (String) rows.get(0).get("id");
        }

        // 创建新 ontology（以 domainCode 为 ontology code）
        String oid = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        jdbc.update(
            "INSERT INTO ecos_ontology (id, code, name, version, status, description) " +
            "VALUES (?, ?, ?, '1.0', 'DRAFT', ?)",
            oid, domainCode.toLowerCase(), domainCode + " Ontology",
            "Auto-created for domain " + domainCode);
        return oid;
    }

    private void createEntityProperty(String entityCode, String fieldName, String dataType, String domainCode) {
        String propCode = fieldName;
        String ontologyType = mapSqlType(dataType);
        String propId = UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        // 查重
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ecos_ontology_property WHERE code=? AND entity_id=" +
            "(SELECT id FROM ecos_ontology_entity WHERE code=? AND domain_id=(SELECT id FROM ecos_domain WHERE code=?))",
            Integer.class, propCode, entityCode, domainCode);
        if (count != null && count > 0) return;

        jdbc.update(
            "INSERT INTO ecos_ontology_property (id, code, name, property_type, entity_id, searchable_flag, required_flag, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, (SELECT id FROM ecos_ontology_entity WHERE code=? AND domain_id=(SELECT id FROM ecos_domain WHERE code=?)), 1, 0, NOW(), NOW())",
            propId, propCode, fieldName, ontologyType, entityCode, domainCode);
    }

    private Map<String, Object> createMapping(String entityCode, String resourceName, String domainCode,
                                               String datasourceId, String schema, List<Map<String, Object>> fields) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        String entityName = resourceName;

        // Build field_mappings JSON from fields
        List<Map<String, String>> fmList = new ArrayList<>();
        for (Map<String, Object> f : fields) {
            Map<String, String> fm = new LinkedHashMap<>();
            fm.put("field", (String) f.get("fieldName"));
            fm.put("type", (String) f.get("dataType"));
            fm.put("propertyCode", (String) f.get("fieldName"));
            fmList.add(fm);
        }

        String fieldMappingsJson = toJsonString(fmList);

        // Upsert
        jdbc.update(
            "INSERT INTO ecos_entity_table_mapping (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, field_mappings, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW()) " +
            "ON CONFLICT (entity_code, datasource_id, resource_name) DO UPDATE SET field_mappings=?::jsonb, updated_at=NOW()",
            id, entityCode, entityName, domainCode, datasourceId, resourceName, schema, fieldMappingsJson, fieldMappingsJson);

        return jdbc.queryForMap("SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
    }

    // ── Utility ──

    private String toEntityCode(String tableName) {
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : tableName.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') {
                capitalize = true;
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private String mapSqlType(String sqlType) {
        if (sqlType == null) return "STRING";
        String t = sqlType.toUpperCase();
        if (t.contains("INT") || t.contains("BIGINT") || t.contains("SMALLINT") || t.contains("TINYINT") || t.contains("NUMERIC") || t.contains("DECIMAL") || t.contains("FLOAT") || t.contains("DOUBLE"))
            return "NUMBER";
        if (t.contains("BOOL"))
            return "BOOLEAN";
        if (t.contains("DATE") || t.contains("TIME") || t.contains("TIMESTAMP"))
            return "DATETIME";
        return "STRING";
    }

    private Object fieldValue(List<Map<String, Object>> fields, String key) {
        if (fields.isEmpty()) return null;
        return fields.get(0).get(key);
    }

    private String toJsonString(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
