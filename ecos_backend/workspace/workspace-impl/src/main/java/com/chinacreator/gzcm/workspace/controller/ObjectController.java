package com.chinacreator.gzcm.workspace.controller;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.service.ObjectRuntimeService;
import com.chinacreator.gzcm.common.engine.StateMachineEngine;
import com.chinacreator.gzcm.dccheng.ontology.FunctionEvaluator;
import com.chinacreator.gzcm.common.context.TenantContextHolder;
import com.chinacreator.gzcm.workspace.security.AbacQueryFilter;
import com.chinacreator.gzcm.workspace.service.ObjectKgSyncService;

/**
 * Object Runtime REST API — 对象 CRUD + 动态 Schema。
 *
 * <h3>端点</h3>
 * <pre>
 * GET    /api/v1/ecos/objects/{entityCode}              — 列表（?keyword=&page=&size=）
 * GET    /api/v1/ecos/objects/{entityCode}/{id}         — 详情+关联+时间线
 * GET    /api/v1/ecos/objects/{entityCode}/schema       — 实体 Schema
 * GET    /api/v1/ecos/objects/search?q=&page=&size=     — 全局搜索
 * POST   /api/v1/ecos/objects/{entityCode}              — 创建
 * PUT    /api/v1/ecos/objects/{entityCode}/{id}         — 更新
 * PUT    /api/v1/ecos/objects/{entityCode}/{id}/status  — 状态变更 (deprecated, 用 /transition)
 * DELETE /api/v1/ecos/objects/{entityCode}/{id}         — 删除
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/ecos/objects")
public class ObjectController {

    private static final Logger log = LoggerFactory.getLogger(ObjectController.class);
    private final JdbcTemplate jdbc;
    private final ObjectRuntimeService runtimeService;
    private final StateMachineEngine stateMachineEngine;
    private final FunctionEvaluator functionEvaluator;
    private final AbacQueryFilter abacFilter;
    private final ObjectKgSyncService kgSyncService;

    /** 实体代码 → 表名映射（从 ecos_ontology_entity 动态发现，回退到 demo 表） */
    private final Map<String, String> ENTITY_TABLE;

    /** 列名缓存 — 表名 → 允许的列名集合（懒加载，从 information_schema 查询） */
    private static final Map<String, Set<String>> COLUMNS_CACHE = new ConcurrentHashMap<>();

    public ObjectController(JdbcTemplate jdbc, ObjectRuntimeService runtimeService,
                             StateMachineEngine stateMachineEngine, FunctionEvaluator functionEvaluator,
                             AbacQueryFilter abacFilter, ObjectKgSyncService kgSyncService) {
        this.jdbc = jdbc;
        this.runtimeService = runtimeService;
        this.stateMachineEngine = stateMachineEngine;
        this.functionEvaluator = functionEvaluator;
        this.abacFilter = abacFilter;
        this.kgSyncService = kgSyncService;
        this.ENTITY_TABLE = loadEntityTable();
    }

    /**
     * 从 ecos_ontology_entity 动态发现实体，回退到 demo 表。
     * <p>
     * 查询条件: entity_code, table_name WHERE status = 'published'
     * 如果本体表不存在或无数据，则使用 demo 表作为回退。
     */
    private Map<String, String> loadEntityTable() {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT entity_code, table_name FROM ecos_ontology_entity WHERE status = 'published'");
            for (Map<String, Object> row : rows) {
                String entityCode = (String) row.get("entity_code");
                String tableName = (String) row.get("table_name");
                if (entityCode != null && tableName != null) {
                    map.put(entityCode, tableName);
                }
            }
            if (!map.isEmpty()) {
                log.info("Loaded {} entities from ecos_ontology_entity", map.size());
                return Collections.unmodifiableMap(map);
            }
        } catch (Exception e) {
            log.warn("Failed to load entities from ecos_ontology_entity: {}. Falling back to demo tables.", e.getMessage());
        }

        // Fallback to demo tables
        log.info("Using demo entity tables as fallback");
        map.put("Customer", "demo_customer");
        map.put("Supplier", "demo_supplier");
        map.put("Invoice", "demo_invoice");
        map.put("Project", "demo_project");
        map.put("project", "demo_project");
        map.put("Order", "demo_order");
        map.put("order", "demo_order");
        return Collections.unmodifiableMap(map);
    }

    // ═══════════════ 列表 ═══════════════════

    @GetMapping("/{entityCode}")
    public ApiResponse<Map<String, Object>> listObjects(
            @PathVariable String entityCode,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        String table = ENTITY_TABLE.get(entityCode);
        if (table == null) return ApiResponse.notFound("实体 " + entityCode + " 不存在");

        // ★ ABAC 行过滤条件
        String abacRowFilter = abacFilter.buildRowFilterCondition(entityCode);

        int offset = (page - 1) * size;
        List<Map<String, Object>> rows;
        Long total;

        if (keyword.isEmpty()) {
            if (abacRowFilter.isEmpty()) {
                rows = jdbc.queryForList(
                    "SELECT * FROM " + table + " ORDER BY id LIMIT ? OFFSET ?", size, offset);
                total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            } else {
                rows = jdbc.queryForList(
                    "SELECT * FROM " + table + " WHERE " + abacRowFilter + " ORDER BY id LIMIT ? OFFSET ?", size, offset);
                total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE " + abacRowFilter, Long.class);
            }
        } else {
            if (abacRowFilter.isEmpty()) {
                rows = jdbc.queryForList(
                    "SELECT * FROM " + table + " WHERE CAST(id AS TEXT) ILIKE ? OR CAST(name AS TEXT) ILIKE ? ORDER BY id LIMIT ? OFFSET ?",
                    "%" + keyword + "%", "%" + keyword + "%", size, offset);
                total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE CAST(id AS TEXT) ILIKE ? OR CAST(name AS TEXT) ILIKE ?",
                    Long.class, "%" + keyword + "%", "%" + keyword + "%");
            } else {
                rows = jdbc.queryForList(
                    "SELECT * FROM " + table + " WHERE (CAST(id AS TEXT) ILIKE ? OR CAST(name AS TEXT) ILIKE ?) AND " + abacRowFilter + " ORDER BY id LIMIT ? OFFSET ?",
                    "%" + keyword + "%", "%" + keyword + "%", size, offset);
                total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE (CAST(id AS TEXT) ILIKE ? OR CAST(name AS TEXT) ILIKE ?) AND " + abacRowFilter,
                    Long.class, "%" + keyword + "%", "%" + keyword + "%");
            }
        }

        // ★ ABAC 列裁剪
        rows = abacFilter.filterColumns(entityCode, rows);

        // ★ Function 计算属性注入
        functionEvaluator.computeAndInject(entityCode, rows);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", rows != null ? rows : List.of());
        result.put("total", total != null ? total : 0);
        result.put("page", page);
        result.put("pageSize", size);
        return ApiResponse.success(result);
    }

    // ═══════════════ 详情 ═══════════════════

    @GetMapping("/{entityCode}/{id}")
    public ApiResponse<Map<String, Object>> getDetail(
            @PathVariable String entityCode,
            @PathVariable String id) {
        String table = ENTITY_TABLE.get(entityCode);
        if (table == null) return ApiResponse.notFound("实体 " + entityCode + " 不存在");

        // ★ ABAC 行过滤条件
        String abacRowFilter = abacFilter.buildRowFilterCondition(entityCode);

        List<Map<String, Object>> rows;
        if (abacRowFilter.isEmpty()) {
            rows = jdbc.queryForList(
                "SELECT * FROM " + table + " WHERE id = ?", id);
        } else {
            rows = jdbc.queryForList(
                "SELECT * FROM " + table + " WHERE id = ? AND " + abacRowFilter, id);
        }
        if (rows.isEmpty()) return ApiResponse.notFound(entityCode + " " + id + " 不存在");

        // ★ ABAC 列裁剪
        rows = abacFilter.filterColumns(entityCode, rows);

        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));

        // ★ Function 计算属性注入
        functionEvaluator.computeAndInject(entityCode, List.of(result));

        result.put("relations", loadRelations(entityCode, id));
        result.put("timeline", loadTimeline(entityCode, id));
        return ApiResponse.success(result);
    }

    // ═══════════════ 关联查询 — 本体关系 ═══════════════════

    /**
     * 从 ecos_ontology_relationship 查询与指定对象相关的所有关系。
     * 返回数据包含方向指示（source → target 或 target → source）。
     */
    private List<Map<String, Object>> loadRelations(String entityCode, String objectId) {
        String sql =
            "SELECT r.id, r.code, r.name, r.relationship_type, " +
            "       r.source_entity_id, r.target_entity_id, r.created_at " +
            "FROM ecos_ontology_relationship r " +
            "WHERE r.source_entity_id = ? OR r.target_entity_id = ? " +
            "ORDER BY r.created_at DESC";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, objectId, objectId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> rel = new LinkedHashMap<>();
                rel.put("id", row.get("id"));
                rel.put("code", row.get("code"));
                rel.put("name", row.get("name"));
                rel.put("relationshipType", row.get("relationship_type"));
                rel.put("sourceEntityId", row.get("source_entity_id"));
                rel.put("targetEntityId", row.get("target_entity_id"));
                rel.put("createdAt", row.get("created_at"));
                // 方向指示：当前对象在关系中的角色
                if (objectId.equals(row.get("source_entity_id"))) {
                    rel.put("direction", "source");
                    rel.put("relatedEntityId", row.get("target_entity_id"));
                } else {
                    rel.put("direction", "target");
                    rel.put("relatedEntityId", row.get("source_entity_id"));
                }
                rel.put("entityCode", entityCode);
                result.add(rel);
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to load relations for {} {}: {}", entityCode, objectId, e.getMessage());
            return List.of();
        }
    }

    // ═══════════════ 时间线 — 结构化 Timeline ═══════════════════

    /**
     * 从 ecos_object_timeline 查询结构化时间线（替代旧 LIKE 查询）。
     * 同时回退到 td_audit_log 兼容旧数据。
     */
    private List<Map<String, Object>> loadTimeline(String entityCode, String objectId) {
        // ★ Primary: structured timeline from ecos_object_timeline
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, object_id, entity_code, event_type, event_summary, actor, details, created_at " +
                "FROM ecos_object_timeline " +
                "WHERE object_id = ? " +
                "ORDER BY created_at DESC LIMIT 200", objectId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("eventId", row.get("id"));
                entry.put("eventType", row.get("event_type"));
                entry.put("timestamp", row.get("created_at") != null ? row.get("created_at").toString() : null);
                entry.put("actor", row.get("actor"));
                entry.put("summary", row.get("event_summary"));
                entry.put("details", row.get("details"));
                result.add(entry);
            }
            if (!result.isEmpty()) return result;
        } catch (Exception e) {
            log.debug("Structured timeline not available for {} {}: {}", entityCode, objectId, e.getMessage());
        }

        // Fallback: legacy audit_log LIKE query
        String sql =
            "SELECT log_id, event_type, timestamp, user_id, action, result, details " +
            "FROM td_audit_log " +
            "WHERE resource LIKE ? " +
            "ORDER BY timestamp DESC LIMIT 200";
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, "%" + objectId + "%");
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("logId", row.get("log_id"));
                entry.put("eventType", row.get("event_type"));
                entry.put("timestamp", row.get("timestamp"));
                entry.put("userId", row.get("user_id"));
                entry.put("action", row.get("action"));
                entry.put("result", row.get("result"));
                entry.put("details", row.get("details"));
                result.add(entry);
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to load timeline for {} {}: {}", entityCode, objectId, e.getMessage());
            return List.of();
        }
    }

    // ═══════════════ Schema ═══════════════════

    @GetMapping("/{entityCode}/schema")
    public ApiResponse<Map<String, Object>> getSchema(@PathVariable String entityCode) {
        String table = ENTITY_TABLE.get(entityCode);
        if (table == null) return ApiResponse.notFound("实体 " + entityCode + " 不存在");

        List<Map<String, Object>> columns = jdbc.queryForList(
            "SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = ? ORDER BY ordinal_position",
            table);

        List<Map<String, Object>> props = new ArrayList<>();
        for (Map<String, Object> col : columns) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("code", col.get("column_name"));
            prop.put("name", col.get("column_name"));
            prop.put("propertyType", col.get("data_type"));
            prop.put("requiredFlag", "NO".equals(col.get("is_nullable")) ? 1 : 0);
            props.add(prop);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityCode", entityCode);
        result.put("entityName", entityCode);
        result.put("properties", props);
        return ApiResponse.success(result);
    }

    // ═══════════════ 搜索 ═══════════════════

    @GetMapping("/search")
    public ApiResponse<Map<String, Object>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<Map<String, Object>> allRows = new ArrayList<>();
        int offset = (page - 1) * size;

        for (Map.Entry<String, String> entry : ENTITY_TABLE.entrySet()) {
            String entityCode = entry.getKey();
            String table = entry.getValue();
            try {
                // ★ ABAC 行过滤条件
                String abacRowFilter = abacFilter.buildRowFilterCondition(entityCode);
                List<Map<String, Object>> rows;
                if (abacRowFilter.isEmpty()) {
                    rows = jdbc.queryForList(
                        "SELECT *, '" + table + "' AS _entity FROM " + table
                        + " WHERE CAST(id AS TEXT) ILIKE ? OR CAST(name AS TEXT) ILIKE ? LIMIT ?",
                        "%" + q + "%", "%" + q + "%", size);
                } else {
                    rows = jdbc.queryForList(
                        "SELECT *, '" + table + "' AS _entity FROM " + table
                        + " WHERE (CAST(id AS TEXT) ILIKE ? OR CAST(name AS TEXT) ILIKE ?) AND " + abacRowFilter + " LIMIT ?",
                        "%" + q + "%", "%" + q + "%", size);
                }
                if (rows != null) {
                    // ★ ABAC 列裁剪
                    rows = abacFilter.filterColumns(entityCode, rows);
                    allRows.addAll(rows);
                }
            } catch (Exception e) {
                log.debug("Search skip table {}: {}", table, e.getMessage());
            }
        }

        int total = allRows.size();
        int from = Math.min(offset, allRows.size());
        int to = Math.min(offset + size, allRows.size());
        List<Map<String, Object>> pageRows = allRows.subList(from, to);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", pageRows);
        result.put("total", total);
        return ApiResponse.success(result);
    }

    // ═══════════════ 创建 ═══════════════════

    @PostMapping("/{entityCode}")
    public ApiResponse<Map<String, Object>> createObject(
            @PathVariable String entityCode,
            @RequestBody Map<String, Object> body) {
        String table = ENTITY_TABLE.get(entityCode);
        if (table == null) return ApiResponse.notFound("实体 " + entityCode + " 不存在");

        // 配额检查：max_storage_mb（admin操作跳过）
        // TenantAwareJdbcTemplate 自动对 ecos_object_data 追加 tenant_id 过滤
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && !tenantId.isBlank()) {
            try {
                Long maxStorageMb = jdbc.queryForObject(
                    "SELECT max_storage_mb FROM ecos_tenant WHERE id = ?", Long.class, tenantId);
                if (maxStorageMb != null && maxStorageMb > 0) {
                    Integer objectCount = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ecos_object_data", Integer.class);
                    if (objectCount != null && objectCount.longValue() >= maxStorageMb) {
                        return ApiResponse.error(400, "存储量已达租户配额上限");
                    }
                }
            } catch (Exception e) {
                log.warn("配额检查失败，放行: {}", e.getMessage());
            }
        }

        // ★ 列名白名单校验：只允许 information_schema 中存在的列
        List<String> validColumns = validateColumns(table, body.keySet());

        // Ensure status field defaults to Draft
        if (!body.containsKey("status")) {
            body.put("status", "Draft");
            validColumns.add("status");
        }

        StringBuilder sql = new StringBuilder("INSERT INTO " + table + " (");
        StringBuilder vals = new StringBuilder(" VALUES (");
        List<Object> params = new ArrayList<>();

        boolean first = true;
        for (String col : validColumns) {
            if (!first) { sql.append(", "); vals.append(", "); }
            sql.append("\"").append(col.replace("\"", "\"\"")).append("\"");
            vals.append("?");
            params.add(body.get(col));
            first = false;
        }
        sql.append(")");
        vals.append(")");
        jdbc.update(sql.toString() + vals.toString(), params.toArray());

        // Return created object (fetch by id if id was in body)
        Object idObj = body.get("id");
        if (idObj != null) {
            String objectId = idObj.toString();
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM " + table + " WHERE id = ?", objectId);
            if (!rows.isEmpty()) {
                Map<String, Object> created = rows.get(0);

                // ★ Event Bus: publish ObjectCreated event
                runtimeService.publishObjectCreated(objectId, entityCode, created);

                // ★ Timeline: record creation
                runtimeService.recordTimeline(objectId, entityCode, "ObjectCreated",
                    "对象创建", (String) body.getOrDefault("createdBy", "system"), created);

                // ★ Version: create initial version snapshot
                runtimeService.createVersion(objectId, entityCode, created,
                    "初始版本", (String) body.getOrDefault("createdBy", "system"));

                // ★ KG Sync: async sync to Neo4j
                kgSyncService.syncObjectToNeo4j(entityCode, objectId, created, "CREATE");

                return ApiResponse.success(created);
            }
        }
        return ApiResponse.success(body);
    }

    // ═══════════════ 更新 ═══════════════════

    @PutMapping("/{entityCode}/{id}")
    public ApiResponse<Map<String, Object>> updateObject(
            @PathVariable String entityCode,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        String table = ENTITY_TABLE.get(entityCode);
        if (table == null) return ApiResponse.notFound("实体 " + entityCode + " 不存在");

        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT * FROM " + table + " WHERE id = ?", id);
        if (existing.isEmpty()) return ApiResponse.notFound(entityCode + " " + id + " 不存在");

        // ★ 列名白名单校验：只允许 information_schema 中存在的列
        List<String> validColumns = validateColumns(table, body.keySet());

        // Track changed fields for event
        Map<String, Object> oldData = existing.get(0);
        List<String> changedFields = new ArrayList<>();

        StringBuilder sql = new StringBuilder("UPDATE " + table + " SET ");
        List<Object> params = new ArrayList<>();
        boolean first = true;
        for (String col : validColumns) {
            if ("id".equals(col)) continue;
            if (!first) sql.append(", ");
            sql.append("\"").append(col.replace("\"", "\"\"")).append("\" = ?");
            params.add(body.get(col));
            first = false;

            // Track changed fields
            Object oldVal = oldData.get(col);
            Object newVal = body.get(col);
            if (!Objects.equals(oldVal, newVal)) {
                changedFields.add(col);
            }
        }
        sql.append(" WHERE id = ?");
        params.add(id);

        if (!params.isEmpty() && params.size() > 1) { // has SET clauses beyond id
            jdbc.update(sql.toString(), params.toArray());
        }

        List<Map<String, Object>> updated = jdbc.queryForList(
            "SELECT * FROM " + table + " WHERE id = ?", id);
        Map<String, Object> result = updated.get(0);

        // ★ Event Bus: publish ObjectUpdated event
        if (!changedFields.isEmpty()) {
            runtimeService.publishObjectUpdated(id, entityCode, changedFields);
        }

        // ★ Timeline: record update
        String actor = (String) body.getOrDefault("updatedBy", "system");
        Map<String, Object> detailMap = new LinkedHashMap<>();
        detailMap.put("changedFields", changedFields);
        runtimeService.recordTimeline(id, entityCode, "ObjectUpdated",
            "更新字段: " + String.join(", ", changedFields), actor, detailMap);

        // ★ Version: create version snapshot
        runtimeService.createVersion(id, entityCode, result,
            "更新: " + String.join(", ", changedFields), actor);

        // ★ KG Sync: async sync to Neo4j
        kgSyncService.syncObjectToNeo4j(entityCode, id, result, "UPDATE");

        return ApiResponse.success(result);
    }

    // ═══════════════ 状态变更 ═══════════════════

    /**
     * PUT /{entityCode}/{id}/status
     * 变更对象状态。必须通过状态机验证，非法转换直接返回 400。
     *
     * Request body:
     * {
     *   "status": "Submitted",       // 目标状态（可选，由 transition 推导）
     *   "transition": "Submit",      // 转换动作编码（必填）
     *   "actor": "user001",
     *   "comment": "提交审核"
     * }
     */
    @PutMapping("/{entityCode}/{id}/status")
    public ApiResponse<Map<String, Object>> updateStatus(
            @PathVariable String entityCode,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        String table = ENTITY_TABLE.get(entityCode);
        if (table == null) return ApiResponse.notFound("实体 " + entityCode + " 不存在");

        // Get current object
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT * FROM " + table + " WHERE id = ?", id);
        if (existing.isEmpty()) return ApiResponse.notFound("OBJ-001: " + entityCode + " " + id + " 不存在");

        Object currentStatusObj = existing.get(0).get("status");
        String currentStatus = currentStatusObj != null ? currentStatusObj.toString().trim() : "Draft";
        if (currentStatus.isEmpty()) currentStatus = "Draft";

        // ★ State Machine validation: transition is mandatory
        String transitionCode = (String) body.get("transition");
        if (transitionCode == null || transitionCode.isEmpty()) {
            return ApiResponse.badRequest("OBJ-002: 缺少 transition 参数，状态变更必须指定转换动作");
        }

        String actor = (String) body.getOrDefault("actor", "system");
        String comment = (String) body.getOrDefault("comment", "");

        // ★ Execute transition via state machine engine (validates + updates DB + records timeline + publishes event)
        try {
            Map<String, Object> transitionResult = stateMachineEngine.executeTransition(
                entityCode, id, currentStatus, transitionCode, actor, comment);

            // Fetch updated object
            List<Map<String, Object>> updated = jdbc.queryForList(
                "SELECT * FROM " + table + " WHERE id = ?", id);
            Map<String, Object> result = updated.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(updated.get(0));
            result.put("previousStatus", transitionResult.get("previousStatus"));
            result.put("newStatus", transitionResult.get("newStatus"));
            result.put("transitionCode", transitionResult.get("transitionCode"));

            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Status change failed for {}/{}: {}", entityCode, id, e.getMessage());
            return ApiResponse.internalError("状态变更失败: " + e.getMessage());
        }
    }

    // ═══════════════ 删除 ═══════════════════

    @DeleteMapping("/{entityCode}/{id}")
    public ApiResponse<String> deleteObject(
            @PathVariable String entityCode,
            @PathVariable String id) {
        String table = ENTITY_TABLE.get(entityCode);
        if (table == null) return ApiResponse.notFound("实体 " + entityCode + " 不存在");

        int rows = jdbc.update("DELETE FROM " + table + " WHERE id = ?", id);
        if (rows == 0) return ApiResponse.notFound(entityCode + " " + id + " 不存在");

        // ★ Timeline: record deletion
        runtimeService.recordTimeline(id, entityCode, "ObjectDeleted",
            "对象删除", "system", null);

        // ★ KG Sync: async sync to Neo4j
        kgSyncService.syncObjectToNeo4j(entityCode, id, null, "DELETE");

        return ApiResponse.success(entityCode + " " + id + " 已删除");
    }

    // ═══════════════ A2 批量回填 KG ═══════════════════

    /**
     * POST /api/v1/ecos/objects/sync-all-to-neo4j
     * 全量遍历 ecos_object_data 中所有 Object 实例，回填到 Neo4j 知识图谱。
     * 种子数据绕过 API 创建，未触发 ObjectKgSyncService，此端点一次性补齐。
     *
     * @return {synced: N, total: M} — synced 为本次同步数，total 为总对象数
     */
    @PostMapping("/sync-all-to-neo4j")
    public ApiResponse<Map<String, Object>> syncAllToNeo4j() {
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList(
                "SELECT id, entity_code, object_data FROM ecos_object_data ORDER BY entity_code, id");
        } catch (Exception e) {
            log.error("查询 ecos_object_data 失败: {}", e.getMessage());
            return ApiResponse.internalError("查询对象数据失败: " + e.getMessage());
        }

        int synced = 0;
        int total = rows.size();
        for (Map<String, Object> row : rows) {
            try {
                String entityCode = (String) row.get("entity_code");
                String objectId = (String) row.get("id");
                Object objectDataObj = row.get("object_data");
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = (objectDataObj instanceof Map)
                    ? (Map<String, Object>) objectDataObj
                    : new LinkedHashMap<>();
                // 兜底：增加 id 字段
                if (!properties.containsKey("id")) {
                    properties = new LinkedHashMap<>(properties);
                    properties.put("id", objectId);
                }
                kgSyncService.syncObjectToNeo4j(entityCode, objectId, properties, "CREATE");
                synced++;
            } catch (Exception e) {
                log.warn("KG 回填跳过 {}/{}: {}", row.get("entity_code"), row.get("id"), e.getMessage());
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("synced", synced);
        result.put("total", total);
        log.info("KG 批量回填完成: {}/{} 个对象已提交同步", synced, total);
        return ApiResponse.success(result);
    }

    // ═══════════════ 列名安全校验 ═══════════════════

    /**
     * 校验用户传入的列名是否在白名单中。
     * 从 information_schema.columns 获取表中允许的所有列名，仅返回合法的列名。
     * 非法列名直接抛 IllegalArgumentException，最终返回 400。
     */
    private List<String> validateColumns(String table, Set<String> inputKeys) {
        Set<String> allowed = getAllowedColumns(table);
        List<String> valid = new ArrayList<>();
        for (String key : inputKeys) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("非法字段: " + key);
            }
            valid.add(key);
        }
        return valid;
    }

    /**
     * 从缓存或数据库查询 information_schema.columns 获取表的所有列名。
     * 缓存在 COLUMNS_CACHE 中，首次查询后不再查库。
     */
    private Set<String> getAllowedColumns(String table) {
        return COLUMNS_CACHE.computeIfAbsent(table, t -> {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = ? ORDER BY ordinal_position", t);
            Set<String> columns = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                columns.add((String) row.get("column_name"));
            }
            log.debug("Cached {} columns for table {}: {}", columns.size(), t, columns);
            return Collections.unmodifiableSet(columns);
        });
    }
}
