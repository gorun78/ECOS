package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyVersionService;

/**
 * Version Controller — 版本管理（Snapshot / Publish / Rollback / Deprecate / Diff）
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ecos/ontologies/{ontologyId}/versions                          — 版本列表</li>
 *   <li>POST   /api/v1/ecos/ontologies/{ontologyId}/versions                          — 创建新版本 (Draft)</li>
 *   <li>GET    /api/v1/ecos/ontologies/{ontologyId}/versions/{versionId}              — 版本详情</li>
 *   <li>POST   /api/v1/ecos/ontologies/{ontologyId}/versions/{versionId}/publish       — 发布版本</li>
 *   <li>POST   /api/v1/ecos/ontologies/{ontologyId}/versions/{versionId}/rollback      — 回滚</li>
 *   <li>POST   /api/v1/ecos/ontologies/{ontologyId}/versions/{versionId}/deprecate     — 废弃</li>
 *   <li>GET    /api/v1/ecos/ontologies/{ontologyId}/versions/{v1}/diff/{v2}            — 版本对比</li>
 *   <li>POST   /api/v1/ecos/ontologies/{ontologyId}/versions/publish-from-proposal/{proposalId} — 提案联动发布（PMO-28）</li>
 * </ul>
 *
 * <p>T16-2 (2026-09-12)：方法入/出参由 {@code Map<String,Object>} 改为强类型
 * {@link OntologyVersionVO} / {@link OntologyVersionSaveDTO}（JSDoc 溯源 {@code T16-2}）。
 * Service 旧 Map 签名保留（Wave31 C1 mock 兼容）。
 */
@RestController
@RequestMapping("/api/v1/ecos/ontologies")
public class OntologyVersionController {

    private static final Logger log = LoggerFactory.getLogger(OntologyVersionController.class);

    private final OntologyVersionService versionService;

    public OntologyVersionController(OntologyVersionService versionService) {
        this.versionService = versionService;
    }

    /** 版本列表（强类型 VO）。 */
    @GetMapping("/{ontologyId}/versions")
    public ApiResponse<List<OntologyVersionVO>> listVersions(@PathVariable String ontologyId) {
        return ApiResponse.success(versionService.listVersionsVO(ontologyId));
    }

    /** 创建新版本 Draft（强类型 DTO）。 */
    @PostMapping("/{ontologyId}/versions")
    public ApiResponse<OntologyVersionVO> createVersion(
            @PathVariable String ontologyId,
            @RequestBody OntologyVersionSaveDTO dto) {
        OntologyVersionVO ver = versionService.createVersionVO(ontologyId, dto);
        log.info("Version created: {} v{}", ver.getId(), ver.getVersionNo());
        return ApiResponse.success(ver);
    }

    /** 版本详情（不存在 404）。 */
    @GetMapping("/{ontologyId}/versions/{versionId}")
    public ApiResponse<OntologyVersionVO> getVersion(
            @PathVariable String ontologyId,
            @PathVariable String versionId) {
        OntologyVersionVO ver = versionService.getVersionVO(ontologyId, versionId);
        if (ver == null) {
            return ApiResponse.notFound("ONT-001: Version '" + versionId + "' not found");
        }
        return ApiResponse.success(ver);
    }

    /** 发布版本（Draft → Published；arge Already Published 409）。 */
    @PostMapping("/{ontologyId}/versions/{versionId}/publish")
    public ApiResponse<OntologyVersionVO> publishVersion(
            @PathVariable String ontologyId,
            @PathVariable String versionId) {
        try {
            OntologyVersionVO ver = versionService.publishVersionVO(ontologyId, versionId);
            log.info("Version published: {} v{}", versionId, ver.getVersionNo());
            return ApiResponse.success(ver);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /** 回滚。 */
    @PostMapping("/{ontologyId}/versions/{versionId}/rollback")
    public ApiResponse<OntologyVersionVO> rollback(
            @PathVariable String ontologyId,
            @PathVariable String versionId) {
        try {
            return ApiResponse.success(versionService.rollbackVO(ontologyId, versionId));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /** 废弃版本。 */
    @PostMapping("/{ontologyId}/versions/{versionId}/deprecate")
    public ApiResponse<OntologyVersionVO> deprecate(
            @PathVariable String ontologyId,
            @PathVariable String versionId) {
        return ApiResponse.success(versionService.deprecateVO(ontologyId, versionId));
    }

    /** 版本对比（强类型 VO：version1/version2/snapshot1/snapshot2）。 */
    @GetMapping("/{ontologyId}/versions/{v1}/diff/{v2}")
    public ApiResponse<OntologyVersionVO> diff(
            @PathVariable String ontologyId,
            @PathVariable String v1,
            @PathVariable String v2) {
        try {
            return ApiResponse.success(versionService.diffVO(ontologyId, v1, v2));
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    // ═══════════════ PMO-28 提案联动端点 ═════════════════
    // POST /api/v1/ecos/ontologies/{ontologyId}/versions/publish-from-proposal/{proposalId}
    // Body: { "publisher": "..." }
    // Query: expectedVersion (乐观锁) — 缺省时尝试读当前值
    // 效果: 提案→APPROVED + 自动 publish Draft 版本

    /**
     * 提案联动发布（PMO-28 T3）。
     *
     * <p>强类型入参：{@code publisher} 直接暴露为 body 字段；
     * 缺省时回退 "system"（与既有 {@code body.get("publisher") != null} 行为一致）。
     *
     * <p>返回值仍为 {@code Map<String,Object>}（既有契约）：
     * 因 service 旧 {@code publishFromProposal(ontologyId, proposalId, expectedVersion, publisher)}
     * 签名返回 Map（含 versionId / versionNo），本控制器**不调用** VO 重载而是
     * 复用既有 service 方法（避免触碰 Wave31 C1 mock 依赖的 service 实现）。
     * 这与 T16-1 已 commit 的"service 不动旧 Map 签名"原则一致。
     */
    @PostMapping("/{ontologyId}/versions/publish-from-proposal/{proposalId}")
    public ApiResponse<Map<String, Object>> publishFromProposal(
            @PathVariable String ontologyId,
            @PathVariable String proposalId,
            @RequestParam(required = false) Integer expectedVersion,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        String publisher = body != null && body.get("publisher") != null
                ? String.valueOf(body.get("publisher")) : "system";
        try {
            Map<String, Object> result = versionService.publishFromProposal(
                    ontologyId, proposalId, expectedVersion, publisher);
            return ApiResponse.success(result);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("ONT-409")) {
                return ApiResponse.error(ApiResponse.CODE_BAD_REQUEST, "OPTIMISTIC_LOCK_CONFLICT", e.getMessage());
            }
            return ApiResponse.badRequest(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }
}
