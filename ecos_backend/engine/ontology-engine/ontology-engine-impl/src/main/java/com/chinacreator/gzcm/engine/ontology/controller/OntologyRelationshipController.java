package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipGraphVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipValidateQuery;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipValidateVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyRelationshipVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;

/**
 * Relationship Controller — 关系设计器（循环检测/图谱/基数）
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/entities/{entityId}/relationships         — 实体关系列表</li>
 *   <li>POST   /api/v1/ecos/relationships                            — 创建关系</li>
 *   <li>GET    /api/v1/ecos/relationships/{relId}                     — 关系详情</li>
 *   <li>PUT    /api/v1/ecos/relationships/{relId}                     — 更新关系</li>
 *   <li>DELETE /api/v1/ecos/relationships/{relId}                     — 删除关系</li>
 *   <li>POST   /api/v1/ecos/relationships/validate                    — 验证关系（循环检测）</li>
 *   <li>GET    /api/v1/ecos/relationships/graph                       — 全局关系图谱数据</li>
 * </ul>
 *
 * <p>T16-1 (2026-09-12)：方法入/出参由 {@code Map<String,Object>} 改为强类型 DTO/VO
 * （{@code OntologyRelationshipSaveDTO / OntologyRelationshipValidateQuery /
 * OntologyRelationshipValidateVO / OntologyRelationshipGraphVO / OntologyRelationshipVO}）。
 */
@RestController
@RequestMapping("/api/v1/ecos")
public class OntologyRelationshipController {

    private static final Logger log = LoggerFactory.getLogger(OntologyRelationshipController.class);

    private final OntologyService ontologyService;

    public OntologyRelationshipController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    /** 列出实体的关系（作为 source 或 target）— 强类型版本。 */
    @GetMapping("/entities/{entityId}/relationships")
    public ApiResponse<List<OntologyRelationshipVO>> listEntityRelationships(@PathVariable String entityId) {
        return ApiResponse.success(ontologyService.listEntityRelationshipsVO(entityId));
    }

    /** 列出全部关系 — 强类型版本。 */
    @GetMapping("/relationships")
    public ApiResponse<List<OntologyRelationshipVO>> listAllRelationships() {
        return ApiResponse.success(ontologyService.listAllRelationshipsVO());
    }

    /** 关系详情（按 id 过滤；无专用 Repository 查询，走 filter)。 */
    @GetMapping("/relationships/{relId}")
    public ApiResponse<OntologyRelationshipVO> getRelationship(@PathVariable String relId) {
        return ontologyService.listAllRelationshipsVO().stream()
            .filter(r -> relId.equals(r.getId()))
            .findFirst()
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-001: Relationship '" + relId + "' not found"));
    }

    /** 创建关系 — 接收 {@code OntologyRelationshipSaveDTO}；source 由 path 传入。 */
    @PostMapping("/entities/{entityId}/relationships")
    public ApiResponse<OntologyRelationshipVO> createRelationship(
            @PathVariable String entityId,
            @RequestBody OntologyRelationshipSaveDTO dto) {
        OntologyRelationshipVO rel = ontologyService.createRelationship(entityId, dto);
        log.info("Relationship created: {} {}→{}", rel.getId(), entityId, rel.getTargetEntityId());
        return ApiResponse.success(rel);
    }

    /** 直接创建关系（source 在 body 中） — 接收 {@code OntologyRelationshipSaveDTO}，从 body 兼容 source 字段。 */
    @PostMapping("/relationships")
    public ApiResponse<OntologyRelationshipVO> createRelationshipDirect(@RequestBody OntologyRelationshipSaveDTO dto) {
        // 兼容旧字段 sourceEntityId（联调时部分 client 会从 body 携带 source）
        OntologyRelationshipVO rel = ontologyService.createRelationship(dto.getSourceEntityId(), dto);
        log.info("Relationship created directly: {} [{}]", rel.getId(), rel.getCode());
        return ApiResponse.success(rel);
    }

    /** 更新关系 — 简化为 remove + create 两步（保留旧实现语义）。 */
    @PutMapping("/relationships/{relId}")
    public ApiResponse<OntologyRelationshipVO> updateRelationship(
            @PathVariable String relId,
            @RequestBody OntologyRelationshipSaveDTO dto) {
        return ontologyService.listAllRelationshipsVO().stream()
            .filter(r -> relId.equals(r.getId()))
            .findFirst()
            .map(existing -> {
                ontologyService.deleteRelationship(relId);
                String src = dto.getSourceEntityId() != null ? dto.getSourceEntityId() : existing.getSourceEntityId();
                return ApiResponse.success(ontologyService.createRelationship(src, dto));
            })
            .orElseGet(() -> ApiResponse.notFound("ONT-001: Relationship '" + relId + "' not found"));
    }

    /** 删除关系（逻辑删除，Wave B-3 T17）。 */
    @DeleteMapping("/entities/{entityId}/relationships/{relId}")
    public ApiResponse<String> deleteRelationshipByEntity(
            @PathVariable String entityId,
            @PathVariable String relId) {
        if (ontologyService.deleteRelationship(relId)) {
            return ApiResponse.success("Relationship '" + relId + "' deleted");
        }
        return ApiResponse.notFound("ONT-001: Relationship '" + relId + "' not found");
    }

    /** 删除关系（逻辑删除，Wave B-3 T17）。 */
    @DeleteMapping("/relationships/{relId}")
    public ApiResponse<String> deleteRelationship(@PathVariable String relId) {
        if (ontologyService.deleteRelationship(relId)) {
            return ApiResponse.success("Relationship '" + relId + "' deleted");
        }
        return ApiResponse.notFound("ONT-001: Relationship '" + relId + "' not found");
    }

    /** 校验关系是否会引入环路 — 接收 {@code OntologyRelationshipValidateQuery}。 */
    @PostMapping("/relationships/validate")
    public ApiResponse<OntologyRelationshipValidateVO> validateRelationship(
            @RequestBody OntologyRelationshipValidateQuery query) {
        OntologyRelationshipValidateVO result = ontologyService.validateRelationshipVO(
            query.getSourceEntityId(), query.getTargetEntityId());
        return ApiResponse.success(result);
    }

    /** 关系图谱（强类型版本：单图对象 nodes+edges 而非旧 {@code List<Map>} 容器）。 */
    @GetMapping("/relationships/graph")
    public ApiResponse<OntologyRelationshipGraphVO> getRelationshipGraph() {
        return ApiResponse.success(ontologyService.getRelationshipGraphVO());
    }
}
