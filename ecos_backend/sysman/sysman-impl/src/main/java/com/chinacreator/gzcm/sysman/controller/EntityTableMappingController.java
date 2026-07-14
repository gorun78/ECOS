package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Entity-Table Mapping Controller — 管理本体实体与物理数据表的映射关系。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET  /api/v1/ecos/entity-table-mappings?entityCode=xxx — 列出实体的所有映射</li>
 *   <li>POST /api/v1/ecos/entity-table-mappings — 创建映射</li>
 *   <li>DELETE /api/v1/ecos/entity-table-mappings/{id} — 删除映射</li>
 *   <li>GET  /api/v1/ecos/entity-table-mappings/datasource/{entityCode} — 获取可映射的数据资源</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ecos/entity-table-mappings")
public class EntityTableMappingController {

    private static final Logger log = LoggerFactory.getLogger(EntityTableMappingController.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EntityTableMappingController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ────────────────────────────────────────────────
    // GET /api/v1/ecos/entity-table-mappings?entityCode=xxx
    // ────────────────────────────────────────────────

    /**
     * 列出指定实体的所有映射，或列出全部映射。
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listMappings(
            @RequestParam(required = false) String entityCode) {
        try {
            String sql;
            Object[] params;
            if (entityCode != null && !entityCode.isBlank()) {
                sql = "SELECT * FROM ecos_entity_table_mapping WHERE entity_code=? ORDER BY created_at DESC";
                params = new Object[]{entityCode};
            } else {
                sql = "SELECT * FROM ecos_entity_table_mapping ORDER BY created_at DESC";
                params = new Object[]{};
            }
            List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
            return ApiResponse.success(rows);
        } catch (Exception e) {
            log.error("查询实体表映射失败: entityCode={}", entityCode, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    // POST /api/v1/ecos/entity-table-mappings
    // ────────────────────────────────────────────────

    /**
     * 创建实体-表映射。
     * 请求体：{ entityCode, entityName, domainCode, datasourceId, resourceName, tableSchema, fieldMappings }
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createMapping(@RequestBody Map<String, Object> body) {
        try {
            String entityCode = (String) body.get("entityCode");
            String entityName = (String) body.get("entityName");
            String domainCode = (String) body.get("domainCode");
            String datasourceId = (String) body.get("datasourceId");
            String resourceName = (String) body.get("resourceName");
            String tableSchema = (String) body.get("tableSchema");

            if (entityCode == null || entityCode.isBlank()) {
                return ApiResponse.badRequest("entityCode is required");
            }
            if (datasourceId == null || datasourceId.isBlank()) {
                return ApiResponse.badRequest("datasourceId is required");
            }
            if (resourceName == null || resourceName.isBlank()) {
                return ApiResponse.badRequest("resourceName is required");
            }

            // Check if mapping already exists
            Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_entity_table_mapping WHERE entity_code=? AND datasource_id=? AND resource_name=?",
                Integer.class, entityCode, datasourceId, resourceName);
            if (existing != null && existing > 0) {
                return ApiResponse.badRequest("Mapping already exists: entityCode=" + entityCode
                    + ", datasourceId=" + datasourceId + ", resourceName=" + resourceName);
            }

            // Build field_mappings JSON
            String fieldMappingsJson = null;
            Object fieldMappingsRaw = body.get("fieldMappings");
            if (fieldMappingsRaw != null) {
                if (fieldMappingsRaw instanceof String) {
                    fieldMappingsJson = (String) fieldMappingsRaw;
                } else {
                    fieldMappingsJson = objectMapper.writeValueAsString(fieldMappingsRaw);
                }
            }

            // Generate ID
            String id = UUID.randomUUID().toString().replace("-", "");

            jdbc.update(
                "INSERT INTO ecos_entity_table_mapping (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, field_mappings, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
                id, entityCode, entityName, domainCode, datasourceId, resourceName, tableSchema, fieldMappingsJson);

            Map<String, Object> created = jdbc.queryForMap(
                "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);

            log.info("创建实体表映射成功: entityCode={}, datasourceId={}, resourceName={}",
                entityCode, datasourceId, resourceName);

            return ApiResponse.success(created);
        } catch (Exception e) {
            log.error("创建实体表映射失败", e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    // DELETE /api/v1/ecos/entity-table-mappings/{id}
    // ────────────────────────────────────────────────

    /**
     * 删除指定ID的映射。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> deleteMapping(@PathVariable String id) {
        try {
            // Check existence
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ecos_entity_table_mapping WHERE id=?",
                Integer.class, id);
            if (count == null || count == 0) {
                return ApiResponse.notFound("Mapping not found: " + id);
            }

            jdbc.update("DELETE FROM ecos_entity_table_mapping WHERE id=?", id);

            log.info("删除实体表映射成功: id={}", id);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("id", id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("删除实体表映射失败: id={}", id, e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    // ────────────────────────────────────────────────
    // GET /api/v1/ecos/entity-table-mappings/datasource/{entityCode}
    // ────────────────────────────────────────────────

    /**
     * 获取指定实体可映射的数据资源列表。
     * 连接 td_data_resource 和 td_datasource，标记已映射和未映射的资源。
     */
    @GetMapping("/datasource/{entityCode}")
    public ApiResponse<Map<String, Object>> getAvailableDataResources(@PathVariable String entityCode) {
        try {
            // Get all active data resources with their datasource info
            String resourcesSql = """
                SELECT r.resource_id, r.resource_name, r.resource_type, r.source_path,
                       r.description, r.field_count, r.record_count, r.status,
                       r.datasource_id, d.datasource_name
                FROM td_data_resource r
                LEFT JOIN td_datasource d ON r.datasource_id = d.datasource_id
                WHERE r.status = 'ACTIVE'
                ORDER BY d.datasource_name, r.resource_name
                """;
            List<Map<String, Object>> allResources = jdbc.queryForList(resourcesSql);

            // Get already-mapped resource names for this entity
            String mappedSql = """
                SELECT datasource_id, resource_name
                FROM ecos_entity_table_mapping
                WHERE entity_code = ?
                """;
            List<Map<String, Object>> mappedRows = jdbc.queryForList(mappedSql, entityCode);

            // Build set of mapped keys: "datasource_id|resource_name"
            Set<String> mappedKeys = new HashSet<>();
            for (Map<String, Object> row : mappedRows) {
                String dsId = (String) row.get("datasource_id");
                String resName = (String) row.get("resource_name");
                mappedKeys.add(dsId + "|" + resName);
            }

            // Partition into available and mapped
            List<Map<String, Object>> available = new ArrayList<>();
            List<Map<String, Object>> mapped = new ArrayList<>();

            for (Map<String, Object> res : allResources) {
                String dsId = (String) res.get("datasource_id");
                String resName = (String) res.get("resource_name");
                if (mappedKeys.contains(dsId + "|" + resName)) {
                    mapped.add(res);
                } else {
                    available.add(res);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("entityCode", entityCode);
            result.put("available", available);
            result.put("mapped", mapped);
            result.put("availableCount", available.size());
            result.put("mappedCount", mapped.size());

            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询可映射数据资源失败: entityCode={}", entityCode, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }
}
