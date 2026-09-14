package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyPropertySaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyPropertyVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;

/**
 * Property Controller — 属性设计器增强（枚举/默认值/校验/扩展类型）
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/entities/{entityId}/properties            — 属性列表</li>
 *   <li>POST   /api/v1/ecos/entities/{entityId}/properties            — 创建属性</li>
 *   <li>GET    /api/v1/ecos/entities/{entityId}/properties/{propId}   — 属性详情</li>
 *   <li>PUT    /api/v1/ecos/entities/{entityId}/properties/{propId}   — 更新属性</li>
 *   <li>DELETE /api/v1/ecos/entities/{entityId}/properties/{propId}   — 删除属性</li>
 * </ul>
 *
 * <p>T16-1 (2026-09-12)：方法入/出参由 {@code Map<String,Object>} 改为强类型 DTO/VO
 * （{@code OntologyPropertySaveDTO} / {@code OntologyPropertyVO}）。
 */
@RestController
@RequestMapping("/api/v1/ecos/entities")
public class OntologyPropertyController {

    private static final Logger log = LoggerFactory.getLogger(OntologyPropertyController.class);

    private final OntologyService ontologyService;

    public OntologyPropertyController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    /** 列出实体的属性（强类型版本）。 */
    @GetMapping("/{entityId}/properties")
    public ApiResponse<List<OntologyPropertyVO>> listProperties(@PathVariable String entityId) {
        return ApiResponse.success(ontologyService.listPropertiesVO(entityId));
    }

    /** 创建属性 — 接收 {@code OntologyPropertySaveDTO}。 */
    @PostMapping("/{entityId}/properties")
    public ApiResponse<OntologyPropertyVO> createProperty(
            @PathVariable String entityId,
            @RequestBody OntologyPropertySaveDTO dto) {
        OntologyPropertyVO prop = ontologyService.createProperty(entityId, dto);
        log.info("Property created: {} [{}] for entity {}", prop.getId(), prop.getCode(), entityId);
        return ApiResponse.success(prop);
    }

    /** 属性详情（在实体属性列表中按 id 过滤；无专用 Repository 查询，走 filter）。 */
    @GetMapping("/{entityId}/properties/{propId}")
    public ApiResponse<OntologyPropertyVO> getProperty(
            @PathVariable String entityId,
            @PathVariable String propId) {
        return ontologyService.listPropertiesVO(entityId).stream()
            .filter(p -> propId.equals(p.getId()))
            .findFirst()
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-001: Property '" + propId + "' not found"));
    }

    /** 更新属性 — 接收 {@code OntologyPropertySaveDTO}。 */
    @PutMapping("/{entityId}/properties/{propId}")
    public ApiResponse<OntologyPropertyVO> updateProperty(
            @PathVariable String entityId,
            @PathVariable String propId,
            @RequestBody OntologyPropertySaveDTO dto) {
        return ontologyService.updateProperty(propId, dto)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-001: Property '" + propId + "' not found"));
    }

    /** 删除属性（逻辑删除，Wave B-3 T17）。 */
    @DeleteMapping("/{entityId}/properties/{propId}")
    public ApiResponse<String> deleteProperty(
            @PathVariable String entityId,
            @PathVariable String propId) {
        if (ontologyService.deleteProperty(propId)) {
            return ApiResponse.success("Property '" + propId + "' deleted");
        }
        return ApiResponse.notFound("ONT-001: Property '" + propId + "' not found");
    }
}
