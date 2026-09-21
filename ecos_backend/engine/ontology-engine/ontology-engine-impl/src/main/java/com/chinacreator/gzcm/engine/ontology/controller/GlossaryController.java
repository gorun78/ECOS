package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.GlossaryBindingDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGlossarySaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGlossaryVO;
import com.chinacreator.gzcm.engine.ontology.service.GlossaryService;

/**
 * 词条（Wiki）REST API — 业务逻辑全部委托 {@link GlossaryService}。
 *
 * <ul>
 *   <li>GET    /api/v1/ontology/glossary/terms          — 词条列表（?domain=&amp;status=&amp;termType=&amp;objectTypeId=&amp;keyword=）</li>
 *   <li>POST   /api/v1/ontology/glossary/terms          — 创建词条</li>
 *   <li>PUT    /api/v1/ontology/glossary/terms/{id}     — 更新词条（含状态流转）</li>
 *   <li>DELETE /api/v1/ontology/glossary/terms/{id}     — 删除词条</li>
 *   <li>PUT    /api/v1/ontology/glossary/terms/{id}/binding — 绑定/解绑本体实体、设主术语</li>
 * </ul>
 *
 * 状态流转: DRAFT → REVIEW → PUBLISHED → DEPRECATED（DEPRECATED 可回 DRAFT）。
 *
 * <p>关系边与图谱端点分别在 {@code GlossaryRelationController} /
 * {@code GlossaryGraphController}（同一路径前缀，子路径不重叠）。
 */
@RestController
@RequestMapping("/api/v1/ontology/glossary")
public class GlossaryController {

    private final GlossaryService glossaryService;

    public GlossaryController(GlossaryService glossaryService) {
        this.glossaryService = glossaryService;
    }

    // ═══════════════ 0. GET 根端点 ══════════════════════

    /**
     * 根端点自描述（硬编码端点路径，非业务数据载体，豁免强类型）。
     */
    @GetMapping
    public ApiResponse<java.util.Map<String, Object>> root() {
        java.util.Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("endpoint", "/api/v1/ontology/glossary");
        info.put("terms", "/api/v1/ontology/glossary/terms");
        info.put("relations", "/api/v1/ontology/glossary/relations");
        info.put("graph", "/api/v1/ontology/glossary/terms/{id}/graph");
        info.put("description", "词条库(Wiki)管理 API");
        return ApiResponse.success(info);
    }

    // ═══════════════ 1. GET 词条列表 ═══════════════════

    /**
     * 词条列表。
     *
     * @param domain       领域字典 code，可空
     * @param status       状态，可空
     * @param termType     词条分类，可空
     * @param objectTypeId 关联本体实体主键，可空
     * @param keyword      名称/编码/别名 模糊关键字，可空
     */
    @GetMapping("/terms")
    public ApiResponse<List<OntologyGlossaryVO>> listTerms(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String termType,
            @RequestParam(required = false) String objectTypeId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(
            glossaryService.listTerms(domain, status, termType, objectTypeId, keyword));
    }

    // ═══════════════ 2. POST 创建词条 ═══════════════════

    /** 创建词条 — 接收 {@link OntologyGlossarySaveDTO}，返回 VO。 */
    @PostMapping("/terms")
    public ApiResponse<OntologyGlossaryVO> createTerm(@RequestBody OntologyGlossarySaveDTO dto) {
        return ApiResponse.success(glossaryService.createTerm(dto));
    }

    // ═══════════════ 3. PUT 更新词条（含状态流转） ═════

    /** 更新词条 — 接收 {@link OntologyGlossarySaveDTO}，按状态流转，返回 VO。 */
    @PutMapping("/terms/{id}")
    public ApiResponse<OntologyGlossaryVO> updateTerm(@PathVariable Long id,
                                                      @RequestBody OntologyGlossarySaveDTO dto) {
        return ApiResponse.success(glossaryService.updateTerm(id, dto));
    }

    // ═══════════════ 4. DELETE 删除词条 ═════════════════

    /** 删除词条（关联关系边由外键级联清理）。 */
    @DeleteMapping("/terms/{id}")
    public ApiResponse<String> deleteTerm(@PathVariable Long id) {
        if (!glossaryService.deleteTerm(id)) {
            return ApiResponse.notFound("词条 " + id + " 不存在");
        }
        return ApiResponse.success("词条 " + id + " 已删除");
    }

    // ═══════════════ 5. PUT 绑定本体实体（T1 本体消费词条）═════

    /**
     * 绑定词条到本体实体 / 解绑 / 设为主术语。
     *
     * <p>body {@code {"objectTypeId":"ent001","primary":true}} → 绑定并设为主术语；
     * {@code {"objectTypeId":null}} 或空串 → 解绑。
     */
    @PutMapping("/terms/{id}/binding")
    public ApiResponse<OntologyGlossaryVO> bindTermToEntity(@PathVariable Long id,
                                                            @RequestBody GlossaryBindingDTO dto) {
        return ApiResponse.success(glossaryService.bindTermToEntity(id, dto));
    }
}