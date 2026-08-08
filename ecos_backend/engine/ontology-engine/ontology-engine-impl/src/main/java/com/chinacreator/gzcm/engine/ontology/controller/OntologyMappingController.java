package com.chinacreator.gzcm.engine.ontology.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.dccheng.ontology.OntologyMappingStore;
import com.chinacreator.gzcm.dccheng.ontology.OntologyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 本体映射管理 REST API — 将本体对象（实体/属性）映射到外部数据源（表、列、接口等）。
 *
 * <p>使用 {@link JdbcTemplate} 持久化至 PostgreSQL 表 ecos_entity_table_mapping。
 * 映射主键由 UUID 生成。保留原有端点签名不变。</p>
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ontology/mappings            — 映射列表（可按 objectId / sourceType 过滤）</li>
 *   <li>GET    /api/v1/ontology/mappings/{id}       — 映射详情</li>
 *   <li>POST   /api/v1/ontology/mappings            — 创建映射</li>
 *   <li>PUT    /api/v1/ontology/mappings/{id}       — 更新映射</li>
 *   <li>DELETE /api/v1/ontology/mappings/{id}       — 删除映射</li>
 *   <li>GET    /api/v1/ontology/mappings/objects    — 可被映射的本体对象列表（委托 OntologyService.listAllObjects）</li>
 * </ul>
 *
 * <p>本控制器只管理映射端点，不改动 {@link OntologyVersionController} 的现有 CRUD 签名。</p>
 */
@RestController
@RequestMapping("/api/v1/ontology/mappings")
public class OntologyMappingController {

    private static final Logger log = LoggerFactory.getLogger(OntologyMappingController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 共享映射存储（@Component注入，保留以兼容 OntologyService.entityToMap） */
    private final OntologyMappingStore mappingStoreRef;

    private final OntologyService ontologyService;

    private final JdbcTemplate jdbc;

    public OntologyMappingController(OntologyService ontologyService,
                                      OntologyMappingStore mappingStoreRef,
                                      JdbcTemplate jdbc) {
        this.ontologyService = ontologyService;
        this.mappingStoreRef = mappingStoreRef;
        this.jdbc = jdbc;
    }

    // ═══════════════ 列表与详情 ═══════════════════

    /**
     * GET /api/v1/ontology/mappings — 映射列表
     *
     * @param objectId   可选，按本体对象 ID 过滤（映射到 entity_code）
     * @param sourceType 可选，按来源类型过滤（映射到 domain_code）
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listMappings(
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String sourceType) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ecos_entity_table_mapping WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (objectId != null && !objectId.isBlank()) {
            sql.append(" AND entity_code=?");
            params.add(objectId);
        }
        if (sourceType != null && !sourceType.isBlank()) {
            sql.append(" AND domain_code=?");
            params.add(sourceType);
        }
        sql.append(" ORDER BY created_at DESC");

        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params.toArray());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(rowToApiMap(row));
        }
        return ApiResponse.success(result);
    }

    /**
     * GET /api/v1/ontology/mappings/{id} — 映射详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getMapping(@PathVariable String id) {
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
            return ApiResponse.success(rowToApiMap(row));
        } catch (EmptyResultDataAccessException e) {
            return ApiResponse.notFound("映射 " + id + " 不存在");
        }
    }

    // ═══════════════ CRUD ═══════════════════

    /**
     * POST /api/v1/ontology/mappings — 创建映射
     * <p>Body 必填字段：objectId（本体对象 ID，映射到 entity_code）；
     * 可选字段：sourceName、sourceType、sourceUri、fieldMappings、description。</p>
     * <p>兼容旧字段: objectTypeId→objectId, datasetId→sourceType。</p>
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createMapping(@RequestBody Map<String, Object> body) {
        // PMO指令字段兼容: objectTypeId→objectId, datasetId→sourceType
        String objectId = String.valueOf(body.getOrDefault("objectId",
                body.getOrDefault("objectTypeId", ""))).trim();
        String datasetId = String.valueOf(body.getOrDefault("datasetId", "")).trim();
        String sourceType = String.valueOf(body.getOrDefault("sourceType",
                datasetId.isEmpty() ? "DATASET" : datasetId)).trim();
        if (objectId.isEmpty()) {
            return ApiResponse.badRequest("ONT-MAP-001: objectTypeId/objectId 不能为空");
        }

        String id = UUID.randomUUID().toString().replace("-", "");

        String sourceName = String.valueOf(body.getOrDefault("sourceName", ""));
        String sourceUri = String.valueOf(body.getOrDefault("sourceUri", ""));
        String description = String.valueOf(body.getOrDefault("description", ""));
        String status = String.valueOf(body.getOrDefault("status", "ACTIVE"));

        // Build field_mappings JSONB: store extended attributes
        Map<String, Object> extendedAttrs = new LinkedHashMap<>();
        extendedAttrs.put("objectType", body.getOrDefault("objectType", "ENTITY"));
        extendedAttrs.put("objectTypeId", objectId);
        extendedAttrs.put("datasetId", datasetId);
        extendedAttrs.put("sourceType", sourceType);
        extendedAttrs.put("description", description);
        extendedAttrs.put("status", status);

        // Process fieldMappings and propertyMappings
        Object rawFieldMappings = body.get("fieldMappings");
        if (rawFieldMappings != null) {
            extendedAttrs.put("fieldMappings", rawFieldMappings);
        }
        Object pm = body.get("propertyMappings");
        if (pm != null) {
            extendedAttrs.put("propertyMappings", pm);
            // 转换为 fieldMappings 数组格式（兼容旧逻辑）
            if (rawFieldMappings == null && pm instanceof Map) {
                List<Map<String, Object>> fmList = new ArrayList<>();
                ((Map<?, ?>) pm).forEach((k, v) -> {
                    Map<String, Object> fm = new LinkedHashMap<>();
                    fm.put("source", String.valueOf(k));
                    fm.put("target", String.valueOf(v));
                    fmList.add(fm);
                });
                extendedAttrs.put("fieldMappings", fmList);
            }
        }

        String fieldMappingsJson;
        try {
            fieldMappingsJson = MAPPER.writeValueAsString(extendedAttrs);
        } catch (JsonProcessingException e) {
            return ApiResponse.badRequest("ONT-MAP-003: Failed to serialize field mappings: " + e.getMessage());
        }

        jdbc.update(
                "INSERT INTO ecos_entity_table_mapping (id, entity_code, entity_name, domain_code, datasource_id, resource_name, table_schema, field_mappings, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW(), NOW())",
                id, objectId, sourceName, sourceType, "",
                sourceName, sourceUri, fieldMappingsJson);

        // 同步到 OntologyMappingStore 供 OntologyService.entityToMap() 读取
        Map<String, Object> apiMap = buildApiMap(id, objectId, sourceType, sourceName, sourceUri,
                extendedAttrs, description, status);
        mappingStoreRef.store.put(id, apiMap);
        mappingStoreRef.store.put(objectId, apiMap);

        Map<String, Object> created = jdbc.queryForMap(
                "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
        log.info("Ontology mapping created: {} objectTypeId={} datasetId={}", id, objectId, datasetId);
        return ApiResponse.success(rowToApiMap(created));
    }

    /**
     * PUT /api/v1/ontology/mappings/{id} — 更新映射
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateMapping(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> existing;
        try {
            existing = jdbc.queryForMap("SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
        } catch (EmptyResultDataAccessException e) {
            return ApiResponse.notFound("映射 " + id + " 不存在");
        }

        // 读取当前 extended attrs
        Object fmObj = existing.get("field_mappings");
        Map<String, Object> currentAttrs = parseFieldMappings(fmObj);

        // 部分更新 extended attrs
        if (body.containsKey("objectType")) currentAttrs.put("objectType", body.get("objectType"));
        if (body.containsKey("sourceType")) currentAttrs.put("sourceType", body.get("sourceType"));
        if (body.containsKey("fieldMappings")) currentAttrs.put("fieldMappings", body.get("fieldMappings"));
        if (body.containsKey("propertyMappings")) currentAttrs.put("propertyMappings", body.get("propertyMappings"));
        if (body.containsKey("description")) currentAttrs.put("description", body.get("description"));
        if (body.containsKey("status")) currentAttrs.put("status", body.get("status"));

        // 更新表级字段
        StringBuilder sql = new StringBuilder("UPDATE ecos_entity_table_mapping SET updated_at=NOW()");
        List<Object> params = new ArrayList<>();

        if (body.containsKey("sourceName")) {
            sql.append(", entity_name=?, resource_name=?");
            String sn = String.valueOf(body.get("sourceName"));
            params.add(sn);
            params.add(sn);
        }
        if (body.containsKey("sourceType")) {
            sql.append(", domain_code=?");
            params.add(String.valueOf(body.get("sourceType")));
        }
        if (body.containsKey("sourceUri")) {
            sql.append(", table_schema=?");
            params.add(String.valueOf(body.get("sourceUri")));
        }

        // 序列化 field_mappings
        try {
            sql.append(", field_mappings=?::jsonb");
            params.add(MAPPER.writeValueAsString(currentAttrs));
        } catch (JsonProcessingException e) {
            return ApiResponse.badRequest("ONT-MAP-003: Failed to serialize field mappings: " + e.getMessage());
        }

        sql.append(" WHERE id=?");
        params.add(id);
        jdbc.update(sql.toString(), params.toArray());

        Map<String, Object> updated = jdbc.queryForMap(
                "SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
        log.info("Ontology mapping updated: {}", id);
        return ApiResponse.success(rowToApiMap(updated));
    }

    /**
     * DELETE /api/v1/ontology/mappings/{id} — 删除映射
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteMapping(@PathVariable String id) {
        Map<String, Object> existing;
        try {
            existing = jdbc.queryForMap("SELECT * FROM ecos_entity_table_mapping WHERE id=?", id);
        } catch (EmptyResultDataAccessException e) {
            return ApiResponse.notFound("映射 " + id + " 不存在");
        }

        jdbc.update("DELETE FROM ecos_entity_table_mapping WHERE id=?", id);
        mappingStoreRef.store.remove(id);

        log.info("Ontology mapping deleted: {}", id);
        return ApiResponse.success("映射 " + id + " 已删除");
    }

    // ═══════════════ 可映射对象查询 ═══════════════════

    /**
     * GET /api/v1/ontology/mappings/objects — 可被映射的本体对象列表
     * <p>委托 {@link OntologyService#listAllObjects()} 返回全部实体，前端据此选择映射目标。</p>
     */
    @GetMapping("/objects")
    public ApiResponse<List<Map<String, Object>>> listMappableObjects() {
        return ApiResponse.success(ontologyService.listAllObjects());
    }

    // ═══════════════ 内部辅助方法 ═══════════════════

    /**
     * 将 ecos_entity_table_mapping 行转换为 API 响应 Map（兼容旧字段名）。
     */
    private Map<String, Object> rowToApiMap(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        String rowId = String.valueOf(row.get("id"));
        String entityCode = String.valueOf(row.getOrDefault("entity_code", ""));
        String entityName = String.valueOf(row.getOrDefault("entity_name", ""));
        String domainCode = String.valueOf(row.getOrDefault("domain_code", ""));
        String resourceName = String.valueOf(row.getOrDefault("resource_name", ""));
        String tableSchema = String.valueOf(row.getOrDefault("table_schema", ""));

        // 解析 field_mappings JSONB
        Object fmObj = row.get("field_mappings");
        Map<String, Object> attrs = parseFieldMappings(fmObj);

        m.put("id", rowId);
        m.put("objectId", entityCode);
        m.put("objectTypeId", entityCode);
        m.put("datasetId", attrs.getOrDefault("datasetId", domainCode));
        m.put("objectType", attrs.getOrDefault("objectType", "ENTITY"));
        m.put("sourceType", domainCode);
        m.put("sourceName", entityName.isEmpty() ? resourceName : entityName);
        m.put("sourceUri", tableSchema);
        m.put("fieldMappings", attrs.getOrDefault("fieldMappings", new ArrayList<>()));
        m.put("propertyMappings", attrs.getOrDefault("propertyMappings", new LinkedHashMap<>()));
        m.put("description", attrs.getOrDefault("description", ""));
        m.put("status", attrs.getOrDefault("status", "ACTIVE"));
        m.put("createdAt", String.valueOf(row.getOrDefault("created_at", "")));
        m.put("updatedAt", String.valueOf(row.getOrDefault("updated_at", "")));
        return m;
    }

    /**
     * 解析 field_mappings JSONB (可能是 PGobject, String, 或 Map)。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFieldMappings(Object fmObj) {
        if (fmObj == null) return new LinkedHashMap<>();
        if (fmObj instanceof Map) return new LinkedHashMap<>((Map<String, Object>) fmObj);
        try {
            return MAPPER.readValue(String.valueOf(fmObj), new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 构建兼容旧 API 的 Map（用于同步到 OntologyMappingStore）。
     */
    private Map<String, Object> buildApiMap(String id, String objectId, String sourceType,
                                             String sourceName, String sourceUri,
                                             Map<String, Object> attrs, String description, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("objectId", objectId);
        m.put("objectTypeId", objectId);
        m.put("datasetId", attrs.getOrDefault("datasetId", sourceType));
        m.put("objectType", attrs.getOrDefault("objectType", "ENTITY"));
        m.put("sourceType", sourceType);
        m.put("sourceName", sourceName);
        m.put("sourceUri", sourceUri);
        m.put("fieldMappings", attrs.getOrDefault("fieldMappings", new ArrayList<>()));
        m.put("propertyMappings", attrs.getOrDefault("propertyMappings", new LinkedHashMap<>()));
        m.put("description", description);
        m.put("status", status);
        m.put("createdAt", LocalDateTime.now().toString());
        m.put("updatedAt", LocalDateTime.now().toString());
        return m;
    }
}
