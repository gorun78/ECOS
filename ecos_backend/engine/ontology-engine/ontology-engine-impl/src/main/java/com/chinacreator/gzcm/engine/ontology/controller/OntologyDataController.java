package com.chinacreator.gzcm.engine.ontology.controller;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;

/**
 * 本体数据实例 Controller — 管理对象类型（ObjectType）下的数据记录。
 *
 * <p>Wave B-3 T15：从内存 ConcurrentHashMap 改造为 PostgreSQL 持久化
 * （表 public.ecos_ontology_data，见 V119__ecos_ontology_data.sql）。
 * 主键改全量 UUID（防多实例冲突），所有查询带 is_deleted=0 过滤（逻辑删除语义）。
 *
 * <p>注意（T15 过渡态）：DELETE /api/v1/ontology/data/{id} 与
 * DELETE /api/v1/ontology/data?... 端点本 commit 不执行库删除，
 * 逻辑删除（UPDATE is_deleted=1, status='ARCHIVED'）留给 Wave B-3 T17 统一收口。
 *
 * <h3>端点契约（仅实现方式变更，路径与出入参不变）：</h3>
 * <ul>
 *   <li>GET    /api/v1/ontology/data              — 数据记录列表（可按 type/objectTypeId 过滤 + 分页）</li>
 *   <li>GET    /api/v1/ontology/data/objects      — 可用对象类型列表（委托 OntologyService）</li>
 *   <li>GET    /api/v1/ontology/data/{id}         — 数据记录详情</li>
 *   <li>POST   /api/v1/ontology/data              — 创建数据记录</li>
 *   <li>PUT    /api/v1/ontology/data/{id}         — 更新数据记录</li>
 *   <li>DELETE /api/v1/ontology/data              — 清空（T15 过渡，仅查询计数不实际删）</li>
 * </ul>
 *
 * <p>持久化映射（表 public.ecos_ontology_data 字段 ↔ API 字段）：
 * <ul>
 *   <li>id (VARCHAR PK) ← 全量 UUID（替代原 "dat"+AtomicInteger 截断 ID）</li>
 *   <li>ontology_id (VARCHAR NOT NULL) ← objectTypeId（业务上对象类型 ID 即归属域）</li>
 *   <li>object_type (VARCHAR NOT NULL) ← objectTypeName（缺省回退 objectTypeId）</li>
 *   <li>record_key (VARCHAR) ← 前端传入 recordKey（缺省 = id）</li>
 *   <li>payload (TEXT) ← 完整 record JSON（含 objectTypeName/properties/createdBy/createdAt/updatedAt）</li>
 *   <li>create_by / update_by ← createdBy / 当前操作人</li>
 *   <li>create_time / update_time ← NOW()</li>
 *   <li>is_deleted (SMALLINT) ← 0（写）/ 查 0（读），T17 由 UPDATE 置 1</li>
 * </ul>
 *
 * @author ECOS Ontology — Wave B-3 T15
 */
@RestController
@RequestMapping("/api/v1/ontology/data")
public class OntologyDataController {

    private static final Logger log = LoggerFactory.getLogger(OntologyDataController.class);
    /** 静态单例 ObjectMapper（payload JSON 序列化/反序列化复用，避免每次 new）。 */
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final OntologyService ontologyService;
    private final JdbcTemplate jdbc;

    public OntologyDataController(OntologyService ontologyService, JdbcTemplate jdbc) {
        this.ontologyService = ontologyService;
        this.jdbc = jdbc;
    }

    // ═══════════════ 数据记录 CRUD ═══════════════════

    /**
     * GET /api/v1/ontology/data — 数据记录列表（PG 持久化，带 is_deleted=0 过滤 + 分页）。
     *
     * @param type         可选过滤参数，指定对象类型 ID（兼容 objectTypeId）
     * @param objectTypeId 可选过滤参数，指定对象类型 ID
     * @param page         页码，默认 1
     * @param size         每页大小，默认 20
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> listData(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "objectTypeId", required = false) String objectTypeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String filterType = (type != null && !type.isBlank()) ? type : objectTypeId;

        int safePage = Math.max(1, page);
        int safeSize = Math.min(200, Math.max(1, size));

        boolean hasFilter = filterType != null && !filterType.isBlank();
        final String where = hasFilter
                ? "WHERE ontology_id = ? AND is_deleted = 0"
                : "WHERE is_deleted = 0";

        // 先查 total
        Integer total = hasFilter
                ? jdbc.queryForObject("SELECT COUNT(*) FROM public.ecos_ontology_data " + where, Integer.class, filterType)
                : jdbc.queryForObject("SELECT COUNT(*) FROM public.ecos_ontology_data " + where, Integer.class);
        int totalSafe = total != null ? total : 0;

        // 分页查数据行
        int offset = (safePage - 1) * safeSize;
        List<Map<String, Object>> rows;
        if (hasFilter) {
            rows = jdbc.queryForList(
                    "SELECT id, ontology_id, object_type, record_key, payload, status, " +
                    "       create_time, update_time, create_by, update_by " +
                    "FROM public.ecos_ontology_data " + where + " " +
                    "ORDER BY create_time DESC LIMIT ? OFFSET ?",
                    filterType, safeSize, offset);
        } else {
            rows = jdbc.queryForList(
                    "SELECT id, ontology_id, object_type, record_key, payload, status, " +
                    "       create_time, update_time, create_by, update_by " +
                    "FROM public.ecos_ontology_data " + where + " " +
                    "ORDER BY create_time DESC LIMIT ? OFFSET ?",
                    safeSize, offset);
        }

        List<Map<String, Object>> pageData = rows.stream()
                .map(this::rowToRecord)
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", pageData);
        result.put("total", totalSafe);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("totalPages", (int) Math.ceil((double) totalSafe / safeSize));
        return ApiResponse.success(result);
    }

    /**
     * GET /api/v1/ontology/data/objects — 可用对象类型列表。
     * 委托 {@link OntologyService#listAllObjects()}，确保数据记录绑定到已定义的本体对象。
     */
    @GetMapping("/objects")
    public ApiResponse<List<Map<String, Object>>> listObjectTypes() {
        return ApiResponse.success(ontologyService.listAllObjects());
    }

    /**
     * GET /api/v1/ontology/data/{id} — 数据记录详情（is_deleted=0 过滤）。
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getData(@PathVariable String id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, ontology_id, object_type, record_key, payload, status, " +
                "       create_time, update_time, create_by, update_by " +
                "FROM public.ecos_ontology_data WHERE id = ? AND is_deleted = 0",
                id);
        if (rows.isEmpty()) {
            return ApiResponse.notFound("ONT-001: Data record '" + id + "' not found");
        }
        return ApiResponse.success(rowToRecord(rows.get(0)));
    }

    /**
     * POST /api/v1/ontology/data — 创建数据记录（落 PG，全量 UUID 主键）。
     * <p>Body 字段：
     * <ul>
     *   <li>objectTypeId — 必填，关联的对象类型 ID（落 ontology_id 列）</li>
     *   <li>objectTypeName — 可选，对象类型名称（落 object_type 列，缺省回退 objectTypeId）</li>
     *   <li>recordKey — 可选，业务记录键（落 record_key 列，缺省 = id）</li>
     *   <li>properties — 可选，属性键值对（落 payload JSON 内）</li>
     *   <li>createdBy — 可选，创建人（落 create_by 列）</li>
     * </ul>
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createData(@RequestBody Map<String, Object> body) {
        String objectTypeId = String.valueOf(body.getOrDefault("objectTypeId", "")).trim();
        if (objectTypeId.isEmpty()) {
            return ApiResponse.badRequest("ONT-002: 'objectTypeId' is required");
        }
        // 全量 UUID 主键（替代原 "dat"+AtomicInteger 截断 ID，防多实例冲突）
        String id = UUID.randomUUID().toString();
        String objectTypeName = String.valueOf(body.getOrDefault("objectTypeName", ""));
        if (objectTypeName.isEmpty()) {
            objectTypeName = objectTypeId;
        }
        String recordKey = String.valueOf(body.getOrDefault("recordKey", id)).trim();
        String createdBy = String.valueOf(body.getOrDefault("createdBy", "system"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = body.containsKey("properties")
                ? new LinkedHashMap<>((Map<String, Object>) body.get("properties"))
                : new LinkedHashMap<>();

        // record JSON 落 payload（包装层只读防护：拷贝避免外部持有可变引用）
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("id", id);
        payloadMap.put("objectTypeId", objectTypeId);
        payloadMap.put("objectTypeName", objectTypeName);
        payloadMap.put("properties", new LinkedHashMap<>(properties));
        payloadMap.put("createdBy", createdBy);
        payloadMap.put("createdAt", LocalDateTime.now().toString());

        String payloadJson = toPayloadJson(payloadMap);

        int rows = jdbc.update(
                "INSERT INTO public.ecos_ontology_data " +
                "(id, ontology_id, object_type, record_key, payload, status, " +
                " create_time, update_time, create_by, update_by, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now(), ?, ?, 0)",
                id, objectTypeId, objectTypeName, recordKey, payloadJson, createdBy, createdBy);
        log.info("Ontology data created: {} [objectType={}] rows={}", id, objectTypeId, rows);

        Map<String, Object> rec = new LinkedHashMap<>(payloadMap);
        rec.put("status", "ACTIVE");
        return ApiResponse.success(rec);
    }

    /**
     * PUT /api/v1/ontology/data/{id} — 更新数据记录（COALESCE 风格：null 参数不覆盖已有值）。
     * <p>支持更新 objectTypeName、properties、createdBy；objectTypeId 不可变更。
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateData(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> existingRows = jdbc.queryForList(
                "SELECT id, ontology_id, object_type, record_key, payload, status, " +
                "       create_time, update_time, create_by, update_by " +
                "FROM public.ecos_ontology_data WHERE id = ? AND is_deleted = 0",
                id);
        if (existingRows.isEmpty()) {
            return ApiResponse.notFound("ONT-001: Data record '" + id + "' not found");
        }
        Map<String, Object> existing = rowToRecord(existingRows.get(0));

        String newObjectName = body.containsKey("objectTypeName")
                ? String.valueOf(body.get("objectTypeName"))
                : String.valueOf(existing.getOrDefault("objectTypeName", ""));
        String newCreatedBy = body.containsKey("createdBy")
                ? String.valueOf(body.get("createdBy"))
                : String.valueOf(existing.getOrDefault("createdBy", "system"));
        @SuppressWarnings("unchecked")
        Map<String, Object> newProperties = body.containsKey("properties")
                ? new LinkedHashMap<>((Map<String, Object>) body.get("properties"))
                : new LinkedHashMap<>((Map<String, Object>) existing.getOrDefault("properties", new LinkedHashMap<>()));

        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put("id", id);
        updated.put("objectTypeId", existing.get("objectTypeId"));
        updated.put("objectTypeName", newObjectName);
        updated.put("properties", newProperties);
        updated.put("createdBy", newCreatedBy);
        updated.put("createdAt", existing.get("createdAt"));
        updated.put("updatedAt", LocalDateTime.now().toString());

        int rows = jdbc.update(
                "UPDATE public.ecos_ontology_data SET " +
                "  object_type = COALESCE(?, object_type), " +
                "  create_by   = COALESCE(?, create_by), " +
                "  update_by   = COALESCE(?, update_by), " +
                "  payload     = ?, " +
                "  update_time = now() " +
                "WHERE id = ? AND is_deleted = 0",
                newObjectName, newCreatedBy, "system",
                toPayloadJson(updated), id);
        log.info("Ontology data updated: {} rows={}", id, rows);
        return ApiResponse.success(updated);
    }

    /**
     * DELETE /api/v1/ontology/data/{id} — T15 过渡态：仅查存在性，不改库。
     * <p>逻辑删除（UPDATE is_deleted=1, status='ARCHIVED'）留给 Wave B-3 T17 统一收口，
     * 本端点保持可调用返回 success 不破坏前端契约。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteData(@PathVariable String id) {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.ecos_ontology_data WHERE id = ? AND is_deleted = 0",
                Integer.class, id);
        if (cnt == null || cnt == 0) {
            return ApiResponse.notFound("ONT-001: Data record '" + id + "' not found");
        }
        // T15 过渡：不执行 UPDATE，T17 补逻辑删除 SQL
        log.info("Ontology data delete noted (T17 收口逻辑删除): {}", id);
        return ApiResponse.success("Data record '" + id + "' marked for deletion (T17 收口)");
    }

    // ═══════════════ 批量操作（便捷） ═══════════════════

    /**
     * DELETE /api/v1/ontology/data — 清空指定对象类型下的全部数据记录。
     * <p>T15 过渡态：仅查询计数返回提示，不执行批量 UPDATE。
     * 真实批量逻辑删除留给 Wave B-3 T17 统一收口。
     * <p>未传 objectTypeId 时拒绝执行，避免误清空全表。
     */
    @DeleteMapping
    public ApiResponse<String> clearByObjectType(
            @RequestParam(value = "objectTypeId", required = false) String objectTypeId) {
        if (objectTypeId == null || objectTypeId.isBlank()) {
            return ApiResponse.badRequest("ONT-002: 'objectTypeId' query param is required for bulk delete");
        }
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.ecos_ontology_data " +
                "WHERE ontology_id = ? AND is_deleted = 0",
                Integer.class, objectTypeId);
        int count = cnt != null ? cnt : 0;
        log.info("Ontology data bulk delete noted (T17 收口): {} records for objectType={}", count, objectTypeId);
        return ApiResponse.success("Noted " + count + " records for objectType '" + objectTypeId + "' (T17 收口)");
    }

    // ═══════════════ Row 映射 + JSON 序列化 ═══════════════════

    /**
     * PG 行 → 前端契约 Map（保持 id/objectTypeId/objectTypeName/properties/createdBy/createdAt/updatedAt 字段名不变）。
     */
    private Map<String, Object> rowToRecord(Map<String, Object> row) {
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("id", row.get("id"));
        // ontology_id 列承载 objectTypeId（T15 映射决策，见类 JSDoc）
        Object ontologyId = row.get("ontology_id");
        rec.put("objectTypeId", ontologyId != null ? ontologyId.toString() : "");
        Object objectTypeName = row.get("object_type");
        rec.put("objectTypeName", objectTypeName != null ? objectTypeName.toString() : "");
        Object recordKey = row.get("record_key");
        if (recordKey != null) {
            rec.put("recordKey", recordKey.toString());
        }
        rec.put("status", row.getOrDefault("status", "ACTIVE"));
        rec.put("properties", new LinkedHashMap<String, Object>());
        try {
            if (row.get("payload") != null) {
                Map<String, Object> payload = JSON_MAPPER.readValue(row.get("payload").toString(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                Object props = payload.get("properties");
                if (props instanceof Map<?, ?> pm) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pmap = (Map<String, Object>) pm;
                    rec.put("properties", new LinkedHashMap<>(pmap));
                }
                if (payload.get("createdBy") != null) {
                    rec.put("createdBy", payload.get("createdBy"));
                }
                if (payload.get("createdAt") != null) {
                    rec.put("createdAt", payload.get("createdAt").toString());
                }
            }
        } catch (Exception e) {
            log.warn("payload JSON 反序列化失败 id={}: {}", row.get("id"), e.getMessage());
        }
        // create_time / update_time 兜底 → createdAt / updatedAt
        if (row.get("create_time") instanceof Timestamp ts) {
            rec.put("createdAt", ts.toLocalDateTime().toString());
        }
        if (row.get("update_time") instanceof Timestamp tsu) {
            rec.put("updatedAt", tsu.toLocalDateTime().toString());
        }
        Object createdBy = row.get("create_by");
        if (createdBy != null) {
            rec.putIfAbsent("createdBy", createdBy.toString());
        }
        return rec;
    }

    /** record Map → payload JSON 字符串（静态单例 ObjectMapper 复用，避免每次 new）。 */
    private static String toPayloadJson(Map<String, Object> record) {
        try {
            return JSON_MAPPER.writeValueAsString(record);
        } catch (Exception e) {
            throw new RuntimeException("payload JSON 序列化失败: " + e.getMessage(), e);
        }
    }
}
