package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.service.OntologyService;

/**
 * OntologyEntityGlobalController — 全局对象类型（跨全部 ontology）实体列表端点。
 *
 * <p>背景：实体域的端点（{@code /api/v1/ecos/entities/{entityId}/...}）分散在
 * {@link OntologyPropertyController}（属性域） / {@link OntologyRelationshipController}
 * （关系域，类前缀 {@code /api/v1/ecos}） / {@link OntologyController}（本体域，
 * 类前缀 {@code /api/v1/ecos/ontologies}），但前五版本**没有**裸 {@code GET /entities}
 * 全局清单端点。</p>
 *
 * <p>前端工作台「对象类型」页在尚未选定 ontology 时需要跨本体汇总实体；
 * 本体工作台前端 ontologyApi 与 workspace ObjectController 都在调
 * {@code GET /api/v1/ecos/entities}，缺该端点会 404（实证：2026-09-21）。
 * 此端点显式补齐，委托 {@link OntologyService#listAllObjects()}（强类型基础
 * ontology-engine 数据，字段与 {@code /api/v1/ecos/lineages/entities} 同构）。</p>
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET /api/v1/ecos/entities — 全部未删除实体（跨 ontology 汇总）</li>
 * </ul>
 *
 * <p>本 Controller 只加一端点，严格不改动 {@code OntologyController} 类前缀/路径，
 * 符合架构铁律 §1.1「只增不改」。</p>
 */
@RestController
@RequestMapping("/api/v1/ecos")
public class OntologyEntityGlobalController {

    private final OntologyService ontologyService;

    public OntologyEntityGlobalController(OntologyService ontologyService) {
        this.ontologyService = ontologyService;
    }

    /**
     * 列出全部未删除的本体实体（对象类型）。
     *
     * <p>字段形态与 {@code OntologyService#entityToMap(entity)} 一致：
     * {@code id / ontologyId / code / name / description / entityType / domainId /
     * sortOrder / mapping / createdAt / updatedAt / status}</p>
     */
    @GetMapping("/entities")
    public ApiResponse<List<Map<String, Object>>> listAllEntities() {
        return ApiResponse.success(ontologyService.listAllObjects());
    }
}
