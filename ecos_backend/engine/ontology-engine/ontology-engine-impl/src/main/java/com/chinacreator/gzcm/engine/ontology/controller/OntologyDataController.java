package com.chinacreator.gzcm.engine.ontology.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.chinacreator.gzcm.dccheng.ontology.OntologyService;

/**
 * 本体数据实例 Controller — 管理对象类型（ObjectType）下的数据记录。
 *
 * <p>使用 {@link ConcurrentHashMap} 内存存储，进程重启后数据丢失；
 * 适合开发演示与轻量级原型。对象类型来源由 {@link OntologyService#listAllObjects()}
 * 提供，保证数据记录与本体定义一致。</p>
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ontology/data              — 数据记录列表（可按 objectTypeId 过滤）</li>
 *   <li>GET    /api/v1/ontology/data/objects      — 可用对象类型列表（委托 OntologyService）</li>
 *   <li>GET    /api/v1/ontology/data/{id}         — 数据记录详情</li>
 *   <li>POST   /api/v1/ontology/data              — 创建数据记录</li>
 *   <li>PUT    /api/v1/ontology/data/{id}         — 更新数据记录</li>
 *   <li>DELETE /api/v1/ontology/data/{id}         — 删除数据记录</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ontology/data")
public class OntologyDataController {

    private static final Logger log = LoggerFactory.getLogger(OntologyDataController.class);

    /** 内存存储：dataId → 数据记录（线程安全） */
    private final ConcurrentHashMap<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    /** 自增 ID 序列，与 OntologyService 风格保持一致 */
    private static final AtomicInteger ID_SEQ = new AtomicInteger(0);

    private final OntologyService ontologyService;

    public OntologyDataController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    private String nextId() {
        return "dat" + ID_SEQ.incrementAndGet();
    }

    // ═══════════════ 数据记录 CRUD ═══════════════════

    /**
     * GET /api/v1/ontology/data — 数据记录列表（支持分页）。
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
        List<Map<String, Object>> filtered = store.values().stream()
            .filter(rec -> filterType == null || filterType.isBlank()
                || filterType.equals(rec.get("objectTypeId")))
            .collect(Collectors.toList());

        int total = filtered.size();
        int fromIndex = Math.min((page - 1) * size, total);
        int toIndex = Math.min(fromIndex + size, total);
        List<Map<String, Object>> pageData = filtered.subList(fromIndex, toIndex);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", pageData);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", (int) Math.ceil((double) total / size));
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
     * GET /api/v1/ontology/data/{id} — 数据记录详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getData(@PathVariable String id) {
        Map<String, Object> rec = store.get(id);
        if (rec == null) {
            return ApiResponse.notFound("ONT-001: Data record '" + id + "' not found");
        }
        return ApiResponse.success(rec);
    }

    /**
     * POST /api/v1/ontology/data — 创建数据记录。
     * <p>Body 字段：
     * <ul>
     *   <li>objectTypeId — 必填，关联的对象类型 ID</li>
     *   <li>objectTypeName — 可选，对象类型名称（冗余存储便于展示）</li>
     *   <li>properties — 可选，属性键值对 Map</li>
     *   <li>createdBy — 可选，创建人</li>
     * </ul>
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createData(@RequestBody Map<String, Object> body) {
        String objectTypeId = String.valueOf(body.getOrDefault("objectTypeId", "")).trim();
        if (objectTypeId.isEmpty()) {
            return ApiResponse.badRequest("ONT-002: 'objectTypeId' is required");
        }
        String id = nextId();
        Map<String, Object> rec = new LinkedHashMap<>();
        rec.put("id", id);
        rec.put("objectTypeId", objectTypeId);
        rec.put("objectTypeName", body.getOrDefault("objectTypeName", ""));
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = body.containsKey("properties")
            ? new LinkedHashMap<>((Map<String, Object>) body.get("properties"))
            : new LinkedHashMap<>();
        rec.put("properties", properties);
        rec.put("createdBy", body.getOrDefault("createdBy", "system"));
        String now = Instant.now().toString();
        rec.put("createdAt", now);
        rec.put("updatedAt", now);
        store.put(id, rec);
        log.info("Ontology data created: {} [objectType={}]", id, objectTypeId);
        return ApiResponse.success(rec);
    }

    /**
     * PUT /api/v1/ontology/data/{id} — 更新数据记录。
     * <p>支持更新 objectTypeName、properties、createdBy；objectTypeId 不可变更。
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateData(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> existing = store.get(id);
        if (existing == null) {
            return ApiResponse.notFound("ONT-001: Data record '" + id + "' not found");
        }
        // 原子化更新：复制后修改再写回，避免并发读到半更新状态
        Map<String, Object> updated = new LinkedHashMap<>(existing);
        if (body.containsKey("objectTypeName")) {
            updated.put("objectTypeName", body.get("objectTypeName"));
        }
        if (body.containsKey("createdBy")) {
            updated.put("createdBy", body.get("createdBy"));
        }
        if (body.containsKey("properties")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = new LinkedHashMap<>((Map<String, Object>) body.get("properties"));
            updated.put("properties", properties);
        }
        updated.put("updatedAt", Instant.now().toString());
        store.put(id, updated);
        log.info("Ontology data updated: {}", id);
        return ApiResponse.success(updated);
    }

    /**
     * DELETE /api/v1/ontology/data/{id} — 删除数据记录。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteData(@PathVariable String id) {
        Map<String, Object> removed = store.remove(id);
        if (removed == null) {
            return ApiResponse.notFound("ONT-001: Data record '" + id + "' not found");
        }
        log.info("Ontology data deleted: {}", id);
        return ApiResponse.success("Data record '" + id + "' deleted");
    }

    // ═══════════════ 批量操作（便捷） ═══════════════════

    /**
     * DELETE /api/v1/ontology/data — 清空指定对象类型下的全部数据记录。
     * <p>未传 objectTypeId 时拒绝执行，避免误清空全表。
     */
    @DeleteMapping
    public ApiResponse<String> clearByObjectType(
            @RequestParam(value = "objectTypeId", required = false) String objectTypeId) {
        if (objectTypeId == null || objectTypeId.isBlank()) {
            return ApiResponse.badRequest("ONT-002: 'objectTypeId' query param is required for bulk delete");
        }
        List<String> toRemove = store.values().stream()
            .filter(rec -> objectTypeId.equals(rec.get("objectTypeId")))
            .map(rec -> String.valueOf(rec.get("id")))
            .collect(Collectors.toList());
        toRemove.forEach(store::remove);
        log.info("Ontology data bulk deleted: {} records for objectType={}", toRemove.size(), objectTypeId);
        return ApiResponse.success("Cleared " + toRemove.size() + " records for objectType '" + objectTypeId + "'");
    }
}
