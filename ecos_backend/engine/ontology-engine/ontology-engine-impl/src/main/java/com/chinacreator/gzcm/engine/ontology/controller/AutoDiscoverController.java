package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyAutoDiscoverMappingVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyAutoDiscoverQuery;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyAutoDiscoverResultVO;
import com.chinacreator.gzcm.engine.ontology.service.AutoDiscoverService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 本体自动发现 Controller — T16-3。
 *
 * <p>入参 Map → {@link OntologyAutoDiscoverQuery}；
 * 返回 Map → {@link OntologyAutoDiscoverResultVO} /
 * {@link OntologyAutoDiscoverMappingVO}（Service 新增 VO 重载，
 * 旧 Map 方法保留，C1 兼容）。
 *
 * <p>说明：Service 在 Wave B-2 T13 已改（delegate 到 DataNetResourceClient
 * 走 datanet REST）；本批只增 VO 重载，不动 T13 已 commit 逻辑。
 */
@RestController
@RequestMapping("/api/v1/ecos")
public class AutoDiscoverController {

    private final AutoDiscoverService autoDiscoverService;

    public AutoDiscoverController(AutoDiscoverService autoDiscoverService) {
        this.autoDiscoverService = autoDiscoverService;
    }

    /**
     * POST /domains/{domainCode}/auto-discover — 自动发现本体对象。
     *
     * <p>入参 {@link OntologyAutoDiscoverQuery}（datasourceId + resourceNames）。
     */
    @PostMapping("/domains/{domainCode}/auto-discover")
    public ApiResponse<List<OntologyAutoDiscoverResultVO>> autoDiscover(
            @PathVariable String domainCode,
            @RequestBody OntologyAutoDiscoverQuery query) {
        try {
            String datasourceId = query.getDatasourceId();
            List<String> resourceNames = query.getResourceNames();

            if (datasourceId == null || datasourceId.isBlank()) {
                return ApiResponse.badRequest("datasourceId is required");
            }
            if (resourceNames == null || resourceNames.isEmpty()) {
                return ApiResponse.badRequest("resourceNames is required");
            }

            List<OntologyAutoDiscoverResultVO> results =
                autoDiscoverService.autoDiscoverVO(domainCode, datasourceId, resourceNames);
            return ApiResponse.success(results);
        } catch (Exception e) {
            return ApiResponse.internalError("AutoDiscover failed: " + e.getMessage());
        }
    }

    /** GET /entity-mappings — 实体表映射列表（强类型 VO）。 */
    @GetMapping("/entity-mappings")
    public ApiResponse<List<OntologyAutoDiscoverMappingVO>> listMappings(
            @RequestParam(required = false) String domainCode) {
        List<OntologyAutoDiscoverMappingVO> rows = autoDiscoverService.listMappingsVO(domainCode);
        return ApiResponse.success(rows);
    }
}
