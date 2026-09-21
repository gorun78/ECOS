package com.chinacreator.gzcm.engine.ontology.controller;

import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.GlossaryGraphVO;
import com.chinacreator.gzcm.engine.ontology.service.GlossaryService;

/**
 * 词条图谱 REST API — 以指定词条为中心展开 N 层邻居关系图。
 *
 * <ul>
 *   <li>GET /api/v1/ontology/glossary/terms/{id}/graph?depth=N</li>
 * </ul>
 *
 * 层数收敛在 [1, 3]；返回节点 + 边集合，前端直接渲染力导向图。
 * 与 {@code GlossaryController} 共用路径前缀但子路径不重叠（/terms/{id}/graph）。
 */
@RestController
@RequestMapping("/api/v1/ontology/glossary")
public class GlossaryGraphController {

    private final GlossaryService glossaryService;

    public GlossaryGraphController(GlossaryService glossaryService) {
        this.glossaryService = glossaryService;
    }

    /**
     * 展开中心词条的 N 层邻居图谱。
     *
     * @param id    中心词条 id
     * @param depth 展开层数（默认 1，收敛在 1~3）
     */
    @GetMapping("/terms/{id}/graph")
    public ApiResponse<GlossaryGraphVO> graph(@PathVariable Long id,
                                             @RequestParam(required = false) Integer depth) {
        return ApiResponse.success(glossaryService.buildGraph(id, depth));
    }
}