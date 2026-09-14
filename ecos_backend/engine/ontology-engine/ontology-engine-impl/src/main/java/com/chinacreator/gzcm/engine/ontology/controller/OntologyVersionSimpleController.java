package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionPreviousDiffVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyVersionService;

/**
 * 简化版本端点 — 不依赖 ontologyId 的快捷版本操作（T16-5 强类型入出参）
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/versions              — 全部版本列表</li>
 *   <li>POST   /api/v1/ecos/versions              — 创建版本快照（body 可含 ontologyId，缺省 default）</li>
 *   <li>GET    /api/v1/ecos/versions/{id}          — 版本详情</li>
 *   <li>GET    /api/v1/ecos/versions/{id}/diff     — 与前一版本 diff</li>
 *   <li>POST   /api/v1/ecos/versions/{id}/publish   — 发布版本</li>
 * </ul>
 *
 * <p>与 {@code OntologyVersionController}（T16-2 已改，路径
 * {@code /api/v1/ecos/ontologies/{ontologyId}/versions}）为**不同端点**，
 * 本简化端点直接挂载 {@code /api/v1/ecos/versions}，无路由重复。
 *
 * <p>T16-5：方法入/出参由 {@code Map<String,Object>} 改为强类型
 * {@link OntologyVersionVO} / {@link OntologyVersionSaveDTO} /
 * {@link OntologyVersionPreviousDiffVO}（复用 T16-2 VO/DTO；SaveDTO 扩展可选
 * ontologyId 字段）。Service 旧 Map 签名保留（Wave31 C1 mock 兼容）。
 */
@RestController
@RequestMapping("/api/v1/ecos/versions")
public class OntologyVersionSimpleController {

    private static final Logger log = LoggerFactory.getLogger(OntologyVersionSimpleController.class);

    private final OntologyVersionService versionService;

    public OntologyVersionSimpleController(OntologyVersionService versionService) {
        this.versionService = versionService;
    }

    /**
     * GET /api/v1/ecos/versions — 列出所有版本（跨全部 ontology，强类型 VO）
     */
    @GetMapping
    public ApiResponse<List<OntologyVersionVO>> listAllVersions() {
        return ApiResponse.success(versionService.listAllVersionsVO());
    }

    /**
     * POST /api/v1/ecos/versions — 创建版本快照（强类型 DTO）
     * Body: { "ontologyId": "...", "changeLog": "...", "publisher": "..." }
     */
    @PostMapping
    public ApiResponse<OntologyVersionVO> createVersion(@RequestBody OntologyVersionSaveDTO dto) {
        String ontologyId = dto.getOntologyId() != null ? dto.getOntologyId() : "default";
        OntologyVersionVO ver = versionService.createVersionVO(ontologyId, dto);
        log.info("Version created via simple endpoint: {} v{}", ver.getId(), ver.getVersionNo());
        return ApiResponse.success(ver);
    }

    /**
     * GET /api/v1/ecos/versions/{id} — 版本详情（强类型 VO，不存在 404）
     */
    @GetMapping("/{id}")
    public ApiResponse<OntologyVersionVO> getVersion(@PathVariable String id) {
        OntologyVersionVO ver = versionService.getVersionVOById(id);
        if (ver == null) {
            return ApiResponse.notFound("ONT-001: Version '" + id + "' not found");
        }
        return ApiResponse.success(ver);
    }

    /**
     * GET /api/v1/ecos/versions/{id}/diff — 与前一版本 diff（强类型 VO）
     */
    @GetMapping("/{id}/diff")
    public ApiResponse<OntologyVersionPreviousDiffVO> diffWithPrevious(@PathVariable String id) {
        try {
            return ApiResponse.success(versionService.diffWithPreviousVO(id));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /**
     * POST /api/v1/ecos/versions/{id}/publish — 发布版本（校验→Snapshot→版本号→Published）
     */
    @PostMapping("/{id}/publish")
    public ApiResponse<OntologyVersionVO> publishVersion(@PathVariable String id) {
        try {
            OntologyVersionVO ver = versionService.publishVersionVOById(id);
            log.info("Version published via simple endpoint: {}", id);
            return ApiResponse.success(ver);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }
}
