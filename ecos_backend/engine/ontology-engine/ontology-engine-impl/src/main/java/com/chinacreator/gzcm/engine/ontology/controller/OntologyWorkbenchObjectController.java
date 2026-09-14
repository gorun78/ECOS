package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyWorkbenchObjectSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyWorkbenchObjectVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyWorkbenchObjectService;

/**
 * 本体工作台 5 类视图对象 REST API — object 视图按类型 list + 按 fileId 保存（Wave B-5 T7）。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/ontologies/{ontologyId}/objects?type=X — 按类型列出对象
 *              (X ∈ action / interface / shared_property / function / dataset, 省略=全部)</li>
 *   <li>GET    /api/v1/ecos/ontologies/{ontologyId}/objects/{fileId} — 对象详情</li>
 *   <li>POST   /api/v1/ecos/ontologies/{ontologyId}/objects — 创建对象</li>
 *   <li>PUT    /api/v1/ecos/ontologies/{ontologyId}/objects/{fileId} — 按 fileId 保存</li>
 *   <li>DELETE /api/v1/ecos/ontologies/{ontologyId}/objects/{fileId} — 逻辑删除</li>
 * </ul>
 *
 * <p>独立控制器（不动 T16 已改的 {@link OntologyController} 既有方法）：
 * 类级 {@code @RequestMapping("/api/v1/ecos/ontologies")} 与其并列且
 * {@code @RequestMapping} 级路径相同合法（方法路径 /objects/** 段不重叠,
 * 与既有 /entities /relationships /domains 段无冲突）。
 *
 * <p>三滤波器（架构铁律 §1.2）：前缀 /api/v1/ecos/ 无 VersionPrefixRewriteFilter
 * 改写条目（KEEP 直达）；SecurityConfig permitAll {@code /api/v1/ecos/**}、
 * ClearanceInterceptor 豁免 {@code /api/v1/ecos/ontologies} 前缀、
 * application.yml auth.whitelist {@code /api/v1/ecos/**} 均按通配覆盖，零注册改动。
 *
 * <p>T7 (2026-09-13)：强类型 {@code OntologyWorkbenchObjectSaveDTO /
 * OntologyWorkbenchObjectVO}（definition 字段嵌套动态结构豁免，同 T16-1 先例）。
 */
@RestController
@RequestMapping("/api/v1/ecos/ontologies")
public class OntologyWorkbenchObjectController {

    private static final Logger log = LoggerFactory.getLogger(OntologyWorkbenchObjectController.class);

    private final OntologyWorkbenchObjectService objectService;

    public OntologyWorkbenchObjectController(OntologyWorkbenchObjectService objectService) {
        this.objectService = objectService;
    }

    /** 按视图类型列出本体下的对象（强类型列表）。 */
    @GetMapping("/{ontologyId}/objects")
    public ApiResponse<List<OntologyWorkbenchObjectVO>> listObjects(
            @PathVariable String ontologyId,
            @RequestParam(value = "type", required = false) String type) {
        try {
            return ApiResponse.success(objectService.listObjects(ontologyId, type));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /** 对象详情（fileId = 工作验收"按 fileId 保存"链路端点）。 */
    @GetMapping("/{ontologyId}/objects/{fileId}")
    public ApiResponse<OntologyWorkbenchObjectVO> getObject(
            @PathVariable String ontologyId,
            @PathVariable String fileId) {
        return objectService.getObjectVO(ontologyId, fileId)
            .map(ApiResponse::success)
            .orElseGet(() -> ApiResponse.notFound("ONT-024: Object '" + fileId + "' not found"));
    }

    /** 创建对象 — 接收 {@code OntologyWorkbenchObjectSaveDTO}。 */
    @PostMapping("/{ontologyId}/objects")
    public ApiResponse<OntologyWorkbenchObjectVO> createObject(
            @PathVariable String ontologyId,
            @RequestBody OntologyWorkbenchObjectSaveDTO dto) {
        try {
            OntologyWorkbenchObjectVO vo = objectService.createObject(ontologyId, dto);
            return ApiResponse.success(vo);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /** 按 fileId 保存（更新）— 接收 {@code OntologyWorkbenchObjectSaveDTO}；不存在 → 404 语义。 */
    @PutMapping("/{ontologyId}/objects/{fileId}")
    public ApiResponse<OntologyWorkbenchObjectVO> updateObject(
            @PathVariable String ontologyId,
            @PathVariable String fileId,
            @RequestBody OntologyWorkbenchObjectSaveDTO dto) {
        try {
            Optional<OntologyWorkbenchObjectVO> updated =
                objectService.updateObject(ontologyId, fileId, dto);
            return updated.map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.notFound("ONT-024: Object '" + fileId + "' not found"));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /** 逻辑删除（T17 语义: is_deleted=1 + status=ARCHIVED, 列表过滤, 详情不可查）。 */
    @DeleteMapping("/{ontologyId}/objects/{fileId}")
    public ApiResponse<String> deleteObject(
            @PathVariable String ontologyId,
            @PathVariable String fileId) {
        if (objectService.deleteObject(ontologyId, fileId)) {
            log.info("Workbench object deleted: {} (ontology={})", fileId, ontologyId);
            return ApiResponse.success("对象 " + fileId + " 已删除");
        }
        return ApiResponse.notFound("ONT-024: Object '" + fileId + "' not found");
    }
}
