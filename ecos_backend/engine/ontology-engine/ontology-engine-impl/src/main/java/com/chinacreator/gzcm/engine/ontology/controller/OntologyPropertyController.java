package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.dccheng.ontology.OntologyService;

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
 */
@RestController
@RequestMapping("/api/v1/ecos/entities")
public class OntologyPropertyController {

    private static final Logger log = LoggerFactory.getLogger(OntologyPropertyController.class);

    private final OntologyService ontologyService;

    public OntologyPropertyController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    @GetMapping("/{entityId}/properties")
    public ApiResponse<List<Map<String, Object>>> listProperties(@PathVariable String entityId) {
        return ApiResponse.success(ontologyService.listProperties(entityId));
    }

    @PostMapping("/{entityId}/properties")
    public ApiResponse<Map<String, Object>> createProperty(
            @PathVariable String entityId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> prop = ontologyService.createProperty(entityId, body);
        log.info("Property created: {} [{}] for entity {}", prop.get("id"), prop.get("code"), entityId);
        return ApiResponse.success(prop);
    }

    @GetMapping("/{entityId}/properties/{propId}")
    public ApiResponse<Map<String, Object>> getProperty(
            @PathVariable String entityId,
            @PathVariable String propId) {
        return ontologyService.listProperties(entityId).stream()
            .filter(p -> propId.equals(p.get("id")))
            .findFirst()
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-001: Property '" + propId + "' not found"));
    }

    @PutMapping("/{entityId}/properties/{propId}")
    public ApiResponse<Map<String, Object>> updateProperty(
            @PathVariable String entityId,
            @PathVariable String propId,
            @RequestBody Map<String, Object> body) {
        return ontologyService.updateProperty(propId, body)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-001: Property '" + propId + "' not found"));
    }

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
