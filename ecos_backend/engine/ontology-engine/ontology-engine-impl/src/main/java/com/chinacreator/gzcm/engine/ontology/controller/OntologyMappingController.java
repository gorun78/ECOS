package com.chinacreator.gzcm.engine.ontology.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.chinacreator.gzcm.engine.ontology.dto.OntologyEntityVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyMappingCreateDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyMappingValidateDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyMappingValidationVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyMappingVO;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyMappingStore;
import com.chinacreator.gzcm.engine.ontology.service.OntologyMappingService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 本体映射管理 REST API — 将本体对象（实体/属性）映射到外部数据源（表、列、接口等）。
 *
 * <p>持久化委托至 {@link OntologyMappingService}（PostgreSQL 表 ecos_entity_table_mapping）。
 * 映射主键由 UUID 生成。保留原有端点签名不变。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ontology/mappings            — 映射列表（可按 objectId / sourceType 过滤）</li>
 *   <li>GET    /api/v1/ontology/mappings/{id}       — 映射详情</li>
 *   <li>POST   /api/v1/ontology/mappings            — 创建映射</li>
 *   <li>PUT    /api/v1/ontology/mappings/{id}       — 更新映射</li>
 *   <li>DELETE /api/v1/ontology/mappings/{id}       — 删除映射</li>
 *   <li>POST   /api/v1/ontology/mappings/validate   — 映射一致性校验（C4 映射有效性）</li>
 *   <li>GET    /api/v1/ontology/mappings/objects    — 可被映射的本体对象列表（委托 OntologyService）</li>
 * </ul>
 *
 * <p>T16-2 (2026-09-12)：方法入/出参由 {@code Map<String,Object>} 改为强类型
 * {@code OntologyMappingVO} / {@code OntologyMappingSaveDTO}（JSDoc 溯源 {@code T16-2}）。
 * Service 旧 Map 签名保留（C1 mock 兼容）；VO 重载新增。
 *
 * <p>本控制器只管理映射端点，不改动 {@code OntologyVersionController} 的现有 CRUD 签名。
 */
@RestController
@RequestMapping("/api/v1/ontology/mappings")
public class OntologyMappingController {

    private static final Logger log = LoggerFactory.getLogger(OntologyMappingController.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 共享映射存储（@Component 注入，保留以兼容 {@code OntologyService.entityToMap}） */
    private final OntologyMappingStore mappingStoreRef;

    private final OntologyService ontologyService;

    private final OntologyMappingService mappingService;

    public OntologyMappingController(OntologyService ontologyService,
                                      OntologyMappingStore mappingStoreRef,
                                      OntologyMappingService mappingService) {
        this.ontologyService = ontologyService;
        this.mappingStoreRef = mappingStoreRef;
        this.mappingService = mappingService;
    }

    // ═══════════════ 列表与详情 ═══════════════════

    /**
     * GET /api/v1/ontology/mappings — 映射列表（强类型 VO）。
     *
     * @param objectId   可选，按本体对象 ID 过滤（映射到 entity_code）
     * @param sourceType 可选，按来源类型过滤（映射到 domain_code）
     */
    @GetMapping
    public ApiResponse<List<OntologyMappingVO>> listMappings(
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String sourceType) {
        return ApiResponse.success(mappingService.listMappingsVO(objectId, sourceType));
    }

    /**
     * GET /api/v1/ontology/mappings/{id} — 映射详情（强类型 VO）。
     */
    @GetMapping("/{id}")
    public ApiResponse<OntologyMappingVO> getMapping(@PathVariable String id) {
        OntologyMappingVO vo = mappingService.findMappingByIdVO(id);
        if (vo == null) {
            return ApiResponse.notFound("映射 " + id + " 不存在");
        }
        return ApiResponse.success(vo);
    }

    // ═══════════════ CRUD ═══════════════════

    /**
     * POST /api/v1/ontology/mappings — 创建映射（强类型 DTO）。
     * <p>Body 必填字段：objectId（本体对象 ID，映射到 entity_code）；
     * 可选字段：sourceName、sourceType、sourceUri、fieldMappings、description。
     * <p>兼容旧字段: objectTypeId→objectId, datasetId→sourceType。
     */
    @PostMapping
    public ApiResponse<OntologyMappingVO> createMapping(@RequestBody OntologyMappingCreateDTO dto) {
        // PMO 指令字段兼容: objectTypeId → objectId
        String objectId = (dto.getObjectId() != null && !dto.getObjectId().isBlank())
            ? dto.getObjectId().trim()
            : (dto.getObjectTypeId() != null ? dto.getObjectTypeId().trim() : "");
        String datasetId = dto.getDatasetId() != null ? dto.getDatasetId().trim() : "";
        String sourceType = (dto.getSourceType() != null && !dto.getSourceType().isBlank())
            ? dto.getSourceType().trim()
            : (datasetId.isEmpty() ? "DATASET" : datasetId.trim());
        if (objectId.isEmpty()) {
            return ApiResponse.badRequest("ONT-MAP-001: objectTypeId/objectId 不能为空");
        }

        String id = UUID.randomUUID().toString().replace("-", "");

        String sourceName = dto.getSourceName() != null ? dto.getSourceName() : "";
        String sourceUri = dto.getSourceUri() != null ? dto.getSourceUri() : "";
        String description = dto.getDescription() != null ? dto.getDescription() : "";
        String status = dto.getStatus() != null ? dto.getStatus() : "ACTIVE";

        // Build extended_attrs JSONB（包含 objectType/status/description + fieldMappings 等）
        Map<String, Object> extendedAttrs = new LinkedHashMap<>();
        extendedAttrs.put("objectType", dto.getObjectType() != null ? dto.getObjectType() : "ENTITY");
        extendedAttrs.put("objectTypeId", objectId);
        extendedAttrs.put("datasetId", datasetId);
        extendedAttrs.put("sourceType", sourceType);
        extendedAttrs.put("description", description);
        extendedAttrs.put("status", status);
        if (dto.getFieldMappings() != null) {
            extendedAttrs.put("fieldMappings", dto.getFieldMappings());
        }
        if (dto.getPropertyMappings() != null) {
            extendedAttrs.put("propertyMappings", dto.getPropertyMappings());
            // 转换为 fieldMappings 数组格式（兼容旧逻辑）
            if (dto.getFieldMappings() == null) {
                List<Map<String, Object>> fmList = new ArrayList<>();
                dto.getPropertyMappings().forEach((k, v) -> {
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
        } catch (Exception e) {
            return ApiResponse.badRequest("ONT-MAP-003: Failed to serialize field mappings: " + e.getMessage());
        }

        // Q2 裁决：materialized 未传时由 Service 按默认 true 落库
        mappingService.insertMapping(id, objectId, sourceName, sourceType, sourceUri,
                fieldMappingsJson, dto.getMaterialized());

        // 同步到 OntologyMappingStore 供 OntologyService.entityToMap() 读取
        Map<String, Object> apiMap = buildApiMap(id, objectId, sourceType, sourceName, sourceUri,
                extendedAttrs, description, status);
        mappingStoreRef.store.put(id, apiMap);
        mappingStoreRef.store.put(objectId, apiMap);

        OntologyMappingVO created = mappingService.findMappingByIdVO(id);
        log.info("Ontology mapping created: {} objectTypeId={} datasetId={}", id, objectId, datasetId);
        return ApiResponse.success(created);
    }

    /**
     * PUT /api/v1/ontology/mappings/{id} — 更新映射（强类型 DTO）。
     */
    @PutMapping("/{id}")
    public ApiResponse<OntologyMappingVO> updateMapping(
            @PathVariable String id,
            @RequestBody OntologyMappingCreateDTO dto) {
        Map<String, Object> existing = mappingService.findMappingById(id);
        if (existing == null) {
            return ApiResponse.notFound("映射 " + id + " 不存在");
        }

        // 读取当前 extended attrs
        @SuppressWarnings("unchecked")
        Map<String, Object> currentAttrs = parseFieldMappings(existing.get("field_mappings"));

        // 部分更新 extended attrs（与旧 Map 版 body.containsKey 语义一致：仅"非 null"时覆盖）
        if (dto.getObjectType() != null) currentAttrs.put("objectType", dto.getObjectType());
        if (dto.getSourceType() != null) currentAttrs.put("sourceType", dto.getSourceType());
        if (dto.getFieldMappings() != null) currentAttrs.put("fieldMappings", dto.getFieldMappings());
        if (dto.getPropertyMappings() != null) currentAttrs.put("propertyMappings", dto.getPropertyMappings());
        if (dto.getDescription() != null) currentAttrs.put("description", dto.getDescription());
        if (dto.getStatus() != null) currentAttrs.put("status", dto.getStatus());

        // 更新表级字段
        StringBuilder sql = new StringBuilder("UPDATE ecos_entity_table_mapping SET updated_at=NOW()");
        List<Object> params = new ArrayList<>();
        if (dto.getSourceName() != null) {
            sql.append(", entity_name=?, resource_name=?");
            String sn = dto.getSourceName();
            params.add(sn);
            params.add(sn);
        }
        if (dto.getSourceType() != null) {
            sql.append(", domain_code=?");
            params.add(dto.getSourceType());
        }
        if (dto.getSourceUri() != null) {
            sql.append(", table_schema=?");
            params.add(dto.getSourceUri());
        }
        // Q2 裁决：materialized 仅"非 null"时覆盖（与上方部分更新语义一致）
        if (dto.getMaterialized() != null) {
            sql.append(", materialized=?");
            params.add(dto.getMaterialized());
        }

        try {
            sql.append(", field_mappings=?::jsonb");
            params.add(MAPPER.writeValueAsString(currentAttrs));
        } catch (JsonProcessingException e) {
            return ApiResponse.badRequest("ONT-MAP-003: Failed to serialize field mappings: " + e.getMessage());
        }

        sql.append(" WHERE id=?");
        params.add(id);

        OntologyMappingVO updated = mappingService.updateMappingVO(id, sql, params);
        log.info("Ontology mapping updated: {}", id);
        return ApiResponse.success(updated);
    }

    /**
     * DELETE /api/v1/ontology/mappings/{id} — 删除映射。
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteMapping(@PathVariable String id) {
        Map<String, Object> existing = mappingService.deleteMapping(id);
        if (existing == null) {
            return ApiResponse.notFound("映射 " + id + " 不存在");
        }

        mappingStoreRef.store.remove(id);

        log.info("Ontology mapping deleted: {}", id);
        return ApiResponse.success("映射 " + id + " 已删除");
    }

    // ═══════════════ 可映射对象查询 ═══════════════════

    /**
     * GET /api/v1/ontology/mappings/objects — 可被映射的本体对象列表（强类型 VO）。
     * <p>委托 {@link OntologyService#listEntitiesVO(String)} 返回全量实体
     * （ontologyId = "" 时包含全部，与 T16-1 既有 VO 重载行为一致）。
     */
    @GetMapping("/objects")
    public ApiResponse<List<OntologyEntityVO>> listMappableObjects() {
        return ApiResponse.success(ontologyService.listEntitiesVO(""));
    }

    // ═══════════════ 映射一致性校验（C4） ═══════════════

    /**
     * POST /api/v1/ontology/mappings/validate — 映射一致性校验（方案 §2.3 C4 映射有效性）。
     *
     * <p>校验映射指向的 DW 表 / 列是否存在、类型是否兼容；DW 元数据经 data-engine REST 获取。
     * 校验失败不返回 500：统一返回 {@code ApiResponse.success}，由 {@code data.valid=false}
     * + {@code data.issues[]} 表达结果，{@code data.rejectCode=INVALID_MAPPING} 供 kb 侧
     * 拒绝该实体实例抽取。
     *
     * @param dto 校验范围（mappingId / 内联 datasetId+fieldMappings / entityCode / 全量）
     */
    @PostMapping("/validate")
    public ApiResponse<OntologyMappingValidationVO> validateMappings(
            @RequestBody OntologyMappingValidateDTO dto) {
        return ApiResponse.success(mappingService.validateMappings(dto));
    }

    // ═══════════════ 内部辅助方法 ═══════════════════

    /**
     * 解析 field_mappings JSONB（可能是 PGobject / String / Map）— 与旧 {@code rowToApiMap} 行为一致。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFieldMappings(Object fmObj) {
        if (fmObj == null) {
            return new LinkedHashMap<>();
        }
        if (fmObj instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) fmObj);
        }
        try {
            return MAPPER.readValue(String.valueOf(fmObj), new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 构建兼容旧 API 的 Map（用于同步到 {@code OntologyMappingStore}）。
     * 该 Map 形态仍被 {@code OntologyService.entityToMap} 等历史消费者使用，
     * 因此保留 Map 形态（与 T16-2"service 旧 Map 签名保留"原则一致）。
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
