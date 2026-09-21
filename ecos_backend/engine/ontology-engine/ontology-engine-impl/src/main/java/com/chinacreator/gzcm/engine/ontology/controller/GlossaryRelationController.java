package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.GlossaryRelationSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.GlossaryRelationVO;
import com.chinacreator.gzcm.engine.ontology.service.GlossaryService;

/**
 * 词条关系边 REST API — 业务逻辑全部委托 {@link GlossaryService}。
 *
 * <ul>
 *   <li>GET    /api/v1/ontology/glossary/relations        — 关系列表
 *       （?fromTermId=&amp;toTermId=&amp;relationType=&amp;termId=）</li>
 *   <li>POST   /api/v1/ontology/glossary/relations        — 创建关系边</li>
 *   <li>DELETE /api/v1/ontology/glossary/relations/{id}   — 删除关系边</li>
 * </ul>
 *
 * 边类型（glossary_relation_type 字典）:
 * ISA / SYNONYM / PART_OF / SEE_ALSO / CAUSAL / RELATED。
 */
@RestController
@RequestMapping("/api/v1/ontology/glossary")
public class GlossaryRelationController {

    private final GlossaryService glossaryService;

    public GlossaryRelationController(GlossaryService glossaryService) {
        this.glossaryService = glossaryService;
    }

    /**
     * 关系列表。
     *
     * @param fromTermId   起点词条 id，可空
     * @param toTermId     终点词条 id，可空
     * @param relationType 边类型，可空
     * @param termId       任一端词条 id（起点或终点命中），可空
     */
    @GetMapping("/relations")
    public ApiResponse<List<GlossaryRelationVO>> listRelations(
            @RequestParam(required = false) Long fromTermId,
            @RequestParam(required = false) Long toTermId,
            @RequestParam(required = false) String relationType,
            @RequestParam(required = false) Long termId) {
        return ApiResponse.success(
            glossaryService.listRelations(fromTermId, toTermId, relationType, termId));
    }

    /** 创建关系边（校验两端存在、非自环、类型合法、不重复）。 */
    @PostMapping("/relations")
    public ApiResponse<GlossaryRelationVO> createRelation(@RequestBody GlossaryRelationSaveDTO dto) {
        return ApiResponse.success(glossaryService.createRelation(dto));
    }

    /** 删除关系边。 */
    @DeleteMapping("/relations/{id}")
    public ApiResponse<String> deleteRelation(@PathVariable Long id) {
        if (!glossaryService.deleteRelation(id)) {
            return ApiResponse.notFound("关系 " + id + " 不存在");
        }
        return ApiResponse.success("关系 " + id + " 已删除");
    }
}