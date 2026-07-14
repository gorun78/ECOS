package com.chinacreator.gzcm.dccheng.ontology.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import com.chinacreator.gzcm.dccheng.ontology.OntologyMappingStore;
import com.chinacreator.gzcm.dccheng.ontology.OntologyService;

/**
 * 本体映射管理 REST API — 将本体对象（实体/属性）映射到外部数据源（表、列、接口等）。
 *
 * <p>使用 {@link ConcurrentHashMap} 内存存储，进程重启后数据丢失。映射主键由 UUID 生成。</p>
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/ontology-mappings            — 映射列表（可按 objectId / sourceType 过滤）</li>
 *   <li>GET    /api/v1/ecos/ontology-mappings/{id}       — 映射详情</li>
 *   <li>POST   /api/v1/ecos/ontology-mappings            — 创建映射</li>
 *   <li>PUT    /api/v1/ecos/ontology-mappings/{id}       — 更新映射</li>
 *   <li>DELETE /api/v1/ecos/ontology-mappings/{id}       — 删除映射</li>
 *   <li>GET    /api/v1/ecos/ontology-mappings/objects    — 可被映射的本体对象列表（委托 OntologyService.listAllObjects）</li>
 * </ul>
 *
 * <p>本控制器只新增映射管理端点，不改动 {@link OntologyController} 的现有 CRUD 签名。</p>
 */
@RestController
@RequestMapping("/api/v1/ontology/mappings")
public class OntologyMappingController {

    private static final Logger log = LoggerFactory.getLogger(OntologyMappingController.class);

    /** 共享映射存储（@Component注入） */
    private final OntologyMappingStore mappingStoreRef;

    private final OntologyService ontologyService;

    public OntologyMappingController(OntologyService ontologyService, OntologyMappingStore mappingStoreRef) {
        this.ontologyService = ontologyService;
        this.mappingStoreRef = mappingStoreRef;
    }

    /** 便捷访问 */
    private Map<String, Map<String, Object>> getStore() { return mappingStoreRef.store; }

    // ═══════════════ 列表与详情 ═══════════════════

    /**
     * GET /api/v1/ecos/ontology-mappings — 映射列表
     *
     * @param objectId   可选，按本体对象 ID 过滤
     * @param sourceType 可选，按来源类型过滤（TABLE / COLUMN / API / FILE ...）
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listMappings(
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String sourceType) {
        List<Map<String, Object>> result = getStore().values().stream()
            .filter(m -> objectId == null || objectId.equals(m.get("objectId")))
            .filter(m -> sourceType == null || sourceType.equals(m.get("sourceType")))
            .collect(Collectors.toList());
        return ApiResponse.success(result);
    }

    /**
     * GET /api/v1/ecos/ontology-mappings/{id} — 映射详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getMapping(@PathVariable String id) {
        Map<String, Object> mapping = getStore().get(id);
        if (mapping == null) return ApiResponse.notFound("映射 " + id + " 不存在");
        return ApiResponse.success(mapping);
    }

    // ═══════════════ CRUD ═══════════════════

    /**
     * POST /api/v1/ecos/ontology-mappings — 创建映射
     * <p>Body 必填字段：objectId（本体对象 ID）、sourceType（来源类型）；
     * 可选字段：sourceName、sourceUri、fieldMappings（字段级映射数组）、description。</p>
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createMapping(@RequestBody Map<String, Object> body) {
        // PMO指令字段兼容: objectTypeId→objectId, datasetId→sourceType
        String objectId = String.valueOf(body.getOrDefault("objectId", body.getOrDefault("objectTypeId", ""))).trim();
        String datasetId = String.valueOf(body.getOrDefault("datasetId", "")).trim();
        String sourceType = String.valueOf(body.getOrDefault("sourceType", datasetId.isEmpty() ? "DATASET" : datasetId)).trim();
        if (objectId.isEmpty()) {
            return ApiResponse.badRequest("ONT-MAP-001: objectTypeId/objectId 不能为空");
        }
        String id = "map_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("id", id);
        mapping.put("objectId", objectId);
        mapping.put("objectTypeId", objectId);  // PMO指令字段
        mapping.put("datasetId", datasetId);     // PMO指令字段
        mapping.put("objectType", body.getOrDefault("objectType", "ENTITY"));
        mapping.put("sourceType", sourceType);
        mapping.put("sourceName", String.valueOf(body.getOrDefault("sourceName", "")));
        mapping.put("sourceUri", String.valueOf(body.getOrDefault("sourceUri", "")));
        // PMO指令: propertyMappings是dict{propId:colName}, 同时保留fieldMappings兼容
        Object pm = body.get("propertyMappings");
        if (pm != null) {
            mapping.put("propertyMappings", pm);
            // 转换为fieldMappings数组格式
            List<Map<String, Object>> fmList = new ArrayList<>();
            if (pm instanceof Map) {
                ((Map<?, ?>) pm).forEach((k, v) -> {
                    Map<String, Object> fm = new LinkedHashMap<>();
                    fm.put("source", String.valueOf(k));
                    fm.put("target", String.valueOf(v));
                    fmList.add(fm);
                });
            }
            mapping.put("fieldMappings", fmList);
        } else {
            mapping.put("propertyMappings", new LinkedHashMap<>());
            mapping.put("fieldMappings", body.getOrDefault("fieldMappings", new ArrayList<>()));
        }
        mapping.put("description", String.valueOf(body.getOrDefault("description", "")));
        mapping.put("status", "ACTIVE");
        mapping.put("createdAt", Instant.now().toString());
        mapping.put("updatedAt", Instant.now().toString());
        getStore().put(id, mapping);
        // 同时以objectId为key建立索引，方便GET /{objectId}查询
        getStore().put(objectId, mapping);
        log.info("Ontology mapping created: {} objectTypeId={} datasetId={}", id, objectId, datasetId);
        return ApiResponse.success(mapping);
    }

    /**
     * PUT /api/v1/ecos/ontology-mappings/{id} — 更新映射
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> updateMapping(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> existing = getStore().get(id);
        if (existing == null) return ApiResponse.notFound("映射 " + id + " 不存在");
        // 部分更新：仅覆盖 body 中出现的字段
        for (String key : new String[]{"objectType", "sourceType", "sourceName", "sourceUri",
                "fieldMappings", "description", "status"}) {
            if (body.containsKey(key)) existing.put(key, body.get(key));
        }
        existing.put("updatedAt", Instant.now().toString());
        log.info("Ontology mapping updated: {}", id);
        return ApiResponse.success(existing);
    }

    /**
     * DELETE /api/v1/ecos/ontology-mappings/{id} — 删除映射
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteMapping(@PathVariable String id) {
        if (getStore().remove(id) != null) {
            log.info("Ontology mapping deleted: {}", id);
            return ApiResponse.success("映射 " + id + " 已删除");
        }
        return ApiResponse.notFound("映射 " + id + " 不存在");
    }

    // ═══════════════ 可映射对象查询 ═══════════════════

    /**
     * GET /api/v1/ecos/ontology-mappings/objects — 可被映射的本体对象列表
     * <p>委托 {@link OntologyService#listAllObjects()} 返回全部实体，前端据此选择映射目标。</p>
     */
    @GetMapping("/objects")
    public ApiResponse<List<Map<String, Object>>> listMappableObjects() {
        return ApiResponse.success(ontologyService.listAllObjects());
    }
}
