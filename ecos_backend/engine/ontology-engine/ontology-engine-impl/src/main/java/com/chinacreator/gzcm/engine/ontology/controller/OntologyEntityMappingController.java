package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyMappingVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyMappingService;

/**
 * 本体映射契约查询 Controller — 方案 §5.3「映射契约全量（实例抽取入口）」。
 *
 * <p>与 {@link OntologyMappingController}（{@code /api/v1/ontology/mappings}，映射 CRUD）职责分离：
 * 本控制器只提供**知识工作台实例抽取入口**视角的只读契约查询，按 {@code ontologyId} 过滤，
 * 响应显式携带 {@code entityCode / resourceName / datasetId / fieldMappings / materialized}，
 * 供 kb-engine 的图谱实例抽取（B3-2）直接消费。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET /api/v1/ontology/entity-mappings?ontologyId= — 映射契约全量（ontologyId 可空 = 全量）</li>
 * </ul>
 *
 * <p>依赖方向：本引擎自有表 {@code ecos_entity_table_mapping}（只读）+ {@code ecos_ontology_entity}
 * 解析实体归属，不跨引擎查 {@code td_data_*}（架构铁律 §3.3）。
 */
@RestController
@RequestMapping("/api/v1/ontology")
public class OntologyEntityMappingController {

    private final OntologyMappingService mappingService;

    public OntologyEntityMappingController(OntologyMappingService mappingService) {
        this.mappingService = mappingService;
    }

    /**
     * GET /api/v1/ontology/entity-mappings — 映射契约全量（实例抽取入口）。
     *
     * @param ontologyId 本体业务 ID（可空；空 = 返回全量映射）
     * @return 映射契约列表（含 {@code materialized} 实例化开关）
     */
    @GetMapping("/entity-mappings")
    public ApiResponse<List<OntologyMappingVO>> listEntityMappings(
            @RequestParam(required = false) String ontologyId) {
        return ApiResponse.success(mappingService.listMappingsByOntology(ontologyId));
    }
}
